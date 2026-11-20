package com.urbext.data.repository

import android.content.Context
import com.urbext.core.radio.RadioPlanUtils
import com.urbext.data.local.SettingsDataStore
import com.urbext.data.local.UrbexDatabase
import com.urbext.data.local.toDomain
import com.urbext.data.local.toEntity
import com.urbext.data.seed.SeedData
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.ArchiveItem
import com.urbext.domain.model.ArchiveMediaType
import com.urbext.domain.model.BaseArticle
import com.urbext.domain.model.ConflictStrategy
import com.urbext.domain.model.DatabaseWorkspaceMeta
import com.urbext.domain.model.DatabaseWorkspaceSummary
import com.urbext.domain.model.DbItemSource
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexDbBundle
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.WorkspaceCounts
import com.urbext.domain.model.WorkspaceState
import com.urbext.domain.repository.ArchiveRepository
import com.urbext.domain.repository.BackupRepository
import com.urbext.domain.repository.BaseRepository
import com.urbext.domain.repository.MergeStats
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.WorkspaceRepository
import com.urbext.domain.usecase.MergeUrbexDbUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

@Serializable
private data class StoredWorkspaceIndex(
    val activeDbId: String,
    val mountedDbIds: List<String>,
    val metas: List<DatabaseWorkspaceMeta>
)

class WorkspaceManager(
    context: Context,
    private val db: UrbexDatabase,
    private val settingsStore: SettingsDataStore,
    private val mergeUseCase: MergeUrbexDbUseCase = MergeUrbexDbUseCase()
) : WorkspaceRepository {

    private val rootDir = File(context.applicationContext.filesDir, "urbex_workspaces").apply { mkdirs() }
    private val indexFile = File(rootDir, "workspaces_meta.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private val indexState = MutableStateFlow(
        StoredWorkspaceIndex(
            activeDbId = DEFAULT_DB_ID,
            mountedDbIds = listOf(DEFAULT_DB_ID),
            metas = emptyList()
        )
    )
    private val secondarySnapshots = MutableStateFlow<Map<String, UrbexDbPayload>>(emptyMap())

    @Volatile
    private var initialized = false

    @Volatile
    var onDataMutated: (() -> Unit)? = null

    @Volatile
    var suppressMutationCallbacks: Boolean = false

    private fun notifyDataMutated() {
        if (!suppressMutationCallbacks) {
            onDataMutated?.invoke()
        }
    }

    private fun preserveDeviceSettings(target: AppSettings, current: AppSettings): AppSettings =
        target.copy(
            editorEnabled = current.editorEnabled,
            pdaModeEnabled = current.pdaModeEnabled,
            pdaOrientation = current.pdaOrientation,
            showMapGrid = current.showMapGrid,
            showCompassHud = current.showCompassHud,
            hazardProximityAlertM = current.hazardProximityAlertM,
            confirmBeforeDelete = current.confirmBeforeDelete,
            hapticFeedback = current.hapticFeedback,
            maskCoordinatesOnScreen = current.maskCoordinatesOnScreen,
            imageCacheLimitMb = current.imageCacheLimitMb,
            cloudSync = current.cloudSync
        )

    val objectRepository: ObjectRepository = WorkspaceObjectRepository()
    val archiveRepository: ArchiveRepository = WorkspaceArchiveRepository()
    val baseRepository: BaseRepository = WorkspaceBaseRepository()
    val backupRepository: BackupRepository = WorkspaceBackupRepository()

    override suspend fun ensureInitialized() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (initialized) return@withLock

                // Wait/ensure Room has initial seed if brand-new DB
                if (db.objectDao().getAllOnce().isEmpty() &&
                    db.baseDao().getAllOnce().isEmpty() &&
                    db.profileDao().getOnce() == null
                ) {
                    UrbexDatabase.seedInto(db)
                }

                val loadedIndex = if (indexFile.exists()) {
                    runCatching {
                        json.decodeFromString<StoredWorkspaceIndex>(indexFile.readText(Charsets.UTF_8))
                    }.getOrNull()
                } else {
                    null
                }

                if (loadedIndex == null || loadedIndex.metas.isEmpty()) {
                    val now = System.currentTimeMillis()
                    val defaultMeta = DatabaseWorkspaceMeta(
                        id = DEFAULT_DB_ID,
                        name = "Москва · Сектор-1",
                        regionLabel = "Москва и область",
                        description = "Основная региональная база объектов, заметок и архива.",
                        createdAt = now,
                        updatedAt = now
                    )
                    val initialPayload = readActivePayloadFromRoom()
                    writePayloadFile(DEFAULT_DB_ID, initialPayload)
                    val freshIndex = StoredWorkspaceIndex(
                        activeDbId = DEFAULT_DB_ID,
                        mountedDbIds = listOf(DEFAULT_DB_ID),
                        metas = listOf(defaultMeta)
                    )
                    writeIndexFile(freshIndex)
                    indexState.value = freshIndex
                    secondarySnapshots.value = emptyMap()
                } else {
                    val validIds = loadedIndex.metas.map { it.id }.toSet()
                    val activeId = if (loadedIndex.activeDbId in validIds) {
                        loadedIndex.activeDbId
                    } else {
                        loadedIndex.metas.first().id
                    }
                    val mounted = loadedIndex.mountedDbIds.filter { it in validIds }.ifEmpty { listOf(activeId) }
                    val cleanIndex = loadedIndex.copy(
                        activeDbId = activeId,
                        mountedDbIds = if (activeId in mounted) mounted else listOf(activeId) + mounted
                    )
                    if (cleanIndex != loadedIndex) writeIndexFile(cleanIndex)
                    indexState.value = cleanIndex
                    refreshSecondarySnapshotsLocked(cleanIndex)
                }
                initialized = true
            }
        }
    }

    override fun observeWorkspaces(): Flow<WorkspaceState> {
        return combine(
            indexState,
            secondarySnapshots,
            db.objectDao().observeObjects(null, ""),
            db.archiveDao().observeItems(null),
            db.baseDao().observeArticles("")
        ) { index, snapshots, activeObjects, activeArchives, activeArticles ->
            val summaries = index.metas.map { meta ->
                val counts = if (meta.id == index.activeDbId) {
                    WorkspaceCounts(
                        objects = activeObjects.size,
                        archiveItems = activeArchives.size,
                        baseArticles = activeArticles.size,
                        meshMessages = snapshots[meta.id]?.meshMessages?.size ?: 0
                    )
                } else {
                    val p = snapshots[meta.id] ?: readPayloadFile(meta.id)
                    WorkspaceCounts(
                        objects = p?.objects?.size ?: 0,
                        archiveItems = p?.archiveItems?.size ?: 0,
                        baseArticles = p?.baseArticles?.size ?: 0,
                        meshMessages = p?.meshMessages?.size ?: 0
                    )
                }
                DatabaseWorkspaceSummary(meta = meta, counts = counts)
            }
            WorkspaceState(
                activeDbId = index.activeDbId,
                mountedDbIds = index.mountedDbIds,
                workspaces = summaries
            )
        }.flowOn(Dispatchers.IO)
    }

    override fun observeObjectSources(): Flow<Map<String, DbItemSource>> {
        return combine(
            indexState,
            secondarySnapshots,
            db.objectDao().observeObjects(null, "")
        ) { index, snapshots, activeObjects ->
            val metaById = index.metas.associateBy { it.id }
            val result = LinkedHashMap<String, DbItemSource>()

            // Secondary mounted databases first
            for (dbId in index.mountedDbIds) {
                if (dbId == index.activeDbId) continue
                val dbName = metaById[dbId]?.name ?: dbId
                val p = snapshots[dbId] ?: continue
                for (obj in p.objects) {
                    result[obj.id] = DbItemSource(dbId = dbId, dbName = dbName)
                }
            }
            // Active database always takes ownership for items stored in Room
            val activeName = metaById[index.activeDbId]?.name ?: index.activeDbId
            for (obj in activeObjects) {
                result[obj.id] = DbItemSource(dbId = index.activeDbId, dbName = activeName)
            }
            result
        }
    }

    override fun observeArchiveSources(): Flow<Map<String, DbItemSource>> {
        return combine(
            indexState,
            secondarySnapshots,
            db.archiveDao().observeItems(null)
        ) { index, snapshots, activeItems ->
            val metaById = index.metas.associateBy { it.id }
            val result = LinkedHashMap<String, DbItemSource>()

            for (dbId in index.mountedDbIds) {
                if (dbId == index.activeDbId) continue
                val dbName = metaById[dbId]?.name ?: dbId
                val p = snapshots[dbId] ?: continue
                for (item in p.archiveItems) {
                    result[item.id] = DbItemSource(dbId = dbId, dbName = dbName)
                }
            }
            val activeName = metaById[index.activeDbId]?.name ?: index.activeDbId
            for (item in activeItems) {
                result[item.id] = DbItemSource(dbId = index.activeDbId, dbName = activeName)
            }
            result
        }
    }

    override suspend fun switchDatabase(targetDbId: String, preserveMultiMount: Boolean) {
        ensureInitialized()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val curIndex = indexState.value
                if (curIndex.metas.none { it.id == targetDbId }) return@withLock
                if (curIndex.activeDbId == targetDbId && (!preserveMultiMount && curIndex.mountedDbIds == listOf(targetDbId))) {
                    return@withLock
                }

                // 1. Flush active Room state to its snapshot file before leaving it
                val curPayload = readActivePayloadFromRoom()
                writePayloadFile(curIndex.activeDbId, curPayload)

                // 2. Load target payload (preserve global PDA display/orientation settings)
                val targetPayload = readPayloadFile(targetDbId) ?: SeedData.createEmptyRegionPayload(
                    baseProfile = curPayload.profile,
                    baseSettings = curPayload.settings
                )
                if (curIndex.activeDbId != targetDbId) {
                    writePayloadToRoom(
                        targetPayload.copy(
                            settings = preserveDeviceSettings(targetPayload.settings, curPayload.settings)
                        )
                    )
                }

                val nextMounted = if (preserveMultiMount) {
                    (curIndex.mountedDbIds + targetDbId).distinct()
                } else {
                    listOf(targetDbId)
                }
                val nextIndex = curIndex.copy(
                    activeDbId = targetDbId,
                    mountedDbIds = nextMounted
                )
                writeIndexFile(nextIndex)
                indexState.value = nextIndex
                refreshSecondarySnapshotsLocked(nextIndex)
            }
        }
    }

    override suspend fun toggleMountDatabase(targetDbId: String) {
        ensureInitialized()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val curIndex = indexState.value
                if (curIndex.metas.none { it.id == targetDbId }) return@withLock

                val isMounted = targetDbId in curIndex.mountedDbIds
                val nextMounted = if (isMounted) {
                    if (targetDbId == curIndex.activeDbId && curIndex.mountedDbIds.size <= 1) {
                        return@withLock
                    }
                    curIndex.mountedDbIds.filterNot { it == targetDbId }
                } else {
                    curIndex.mountedDbIds + targetDbId
                }.ifEmpty { listOf(curIndex.activeDbId) }

                var nextActive = curIndex.activeDbId
                if (nextActive !in nextMounted) {
                    val curPayload = readActivePayloadFromRoom()
                    writePayloadFile(curIndex.activeDbId, curPayload)
                    nextActive = nextMounted.first()
                    val targetPayload = readPayloadFile(nextActive)
                    if (targetPayload != null) {
                        writePayloadToRoom(
                            targetPayload.copy(
                                settings = preserveDeviceSettings(targetPayload.settings, curPayload.settings)
                            )
                        )
                    }
                }

                val nextIndex = curIndex.copy(
                    activeDbId = nextActive,
                    mountedDbIds = nextMounted
                )
                writeIndexFile(nextIndex)
                indexState.value = nextIndex
                refreshSecondarySnapshotsLocked(nextIndex)
            }
        }
    }

    override suspend fun createDatabase(
        name: String,
        regionLabel: String,
        copySeed: Boolean,
        activateImmediately: Boolean
    ): DatabaseWorkspaceMeta? {
        ensureInitialized()
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val curIndex = indexState.value
                val curPayload = readActivePayloadFromRoom()
                writePayloadFile(curIndex.activeDbId, curPayload)

                val now = System.currentTimeMillis()
                val newId = "db-${now.toString(36)}"
                val meta = DatabaseWorkspaceMeta(
                    id = newId,
                    name = trimmedName,
                    regionLabel = regionLabel.trim().ifEmpty { trimmedName },
                    description = "Региональная база данных",
                    createdAt = now,
                    updatedAt = now
                )
                val newPayload = if (copySeed) {
                    SeedData.createPayload()
                } else {
                    SeedData.createEmptyRegionPayload(curPayload.profile, curPayload.settings)
                }
                writePayloadFile(newId, newPayload)

                val nextMetas = curIndex.metas + meta
                val nextIndex = if (activateImmediately) {
                    writePayloadToRoom(
                        newPayload.copy(
                            settings = preserveDeviceSettings(newPayload.settings, curPayload.settings)
                        )
                    )
                    curIndex.copy(
                        activeDbId = newId,
                        mountedDbIds = listOf(newId),
                        metas = nextMetas
                    )
                } else {
                    curIndex.copy(metas = nextMetas)
                }
                writeIndexFile(nextIndex)
                indexState.value = nextIndex
                refreshSecondarySnapshotsLocked(nextIndex)
                meta
            }
        }
    }

    override suspend fun renameDatabase(dbId: String, name: String, regionLabel: String) {
        ensureInitialized()
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val curIndex = indexState.value
                val now = System.currentTimeMillis()
                val nextMetas = curIndex.metas.map {
                    if (it.id == dbId) {
                        it.copy(
                            name = cleanName,
                            regionLabel = regionLabel.trim().ifEmpty { cleanName },
                            updatedAt = now
                        )
                    } else {
                        it
                    }
                }
                val nextIndex = curIndex.copy(metas = nextMetas)
                writeIndexFile(nextIndex)
                indexState.value = nextIndex
            }
        }
    }

    override suspend fun deleteDatabase(dbId: String) {
        ensureInitialized()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val curIndex = indexState.value
                if (curIndex.metas.size <= 1) return@withLock

                val remainingMetas = curIndex.metas.filterNot { it.id == dbId }
                var nextActive = curIndex.activeDbId
                var nextMounted = curIndex.mountedDbIds.filterNot { it == dbId }

                if (nextActive == dbId) {
                    val curSettings = settingsStore.getOnce()
                    nextActive = remainingMetas.first().id
                    if (nextActive !in nextMounted) {
                        nextMounted = listOf(nextActive) + nextMounted
                    }
                    val targetPayload = readPayloadFile(nextActive)
                    if (targetPayload != null) {
                        writePayloadToRoom(
                            targetPayload.copy(
                                settings = preserveDeviceSettings(targetPayload.settings, curSettings)
                            )
                        )
                    }
                }
                if (nextMounted.isEmpty()) {
                    nextMounted = listOf(nextActive)
                }

                payloadFile(dbId).delete()
                val nextIndex = curIndex.copy(
                    activeDbId = nextActive,
                    mountedDbIds = nextMounted,
                    metas = remainingMetas
                )
                writeIndexFile(nextIndex)
                indexState.value = nextIndex
                refreshSecondarySnapshotsLocked(nextIndex)
            }
        }
    }

    override suspend fun exportDatabasePayload(targetDbId: String?): UrbexDbPayload? {
        ensureInitialized()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val id = targetDbId ?: indexState.value.activeDbId
                if (id == indexState.value.activeDbId) {
                    val live = readActivePayloadFromRoom()
                    writePayloadFile(id, live)
                    live
                } else {
                    readPayloadFile(id)
                }
            }
        }
    }

    override suspend fun importIntoNewDatabase(
        name: String,
        regionLabel: String,
        bundle: UrbexDbBundle
    ): DatabaseWorkspaceMeta {
        ensureInitialized()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val curIndex = indexState.value
                val curPayload = readActivePayloadFromRoom()
                writePayloadFile(curIndex.activeDbId, curPayload)

                val now = System.currentTimeMillis()
                val newId = "db-imp-${now.toString(36)}"
                val cleanName = name.trim().ifEmpty { "Импорт ${bundle.manifest.deviceNodeId}" }
                val meta = DatabaseWorkspaceMeta(
                    id = newId,
                    name = cleanName,
                    regionLabel = regionLabel.trim().ifEmpty { cleanName },
                    description = "Импортированная база (${bundle.payload.objects.size} объектов)",
                    createdAt = now,
                    updatedAt = now
                )

                val importedPayload = bundle.payload.copy(
                    settings = preserveDeviceSettings(bundle.payload.settings, curPayload.settings)
                )
                writePayloadFile(newId, importedPayload)
                writePayloadToRoom(importedPayload)

                val nextIndex = curIndex.copy(
                    activeDbId = newId,
                    mountedDbIds = listOf(newId),
                    metas = curIndex.metas + meta
                )
                writeIndexFile(nextIndex)
                indexState.value = nextIndex
                refreshSecondarySnapshotsLocked(nextIndex)
                meta
            }
        }
    }

    private suspend fun readActivePayloadFromRoom(): UrbexDbPayload = UrbexDbPayload(
        objects = db.objectDao().getAllOnce().map { it.toDomain() },
        meshMessages = db.meshDao().getAllOnce().map { it.toDomain() },
        baseArticles = db.baseDao().getAllOnce().map { it.toDomain() },
        archiveItems = db.archiveDao().getAllOnce().map { it.toDomain() },
        profile = db.profileDao().getOnce()?.toDomain() ?: SeedData.defaultProfile(),
        settings = settingsStore.getOnce()
    )

    private suspend fun writePayloadToRoom(payload: UrbexDbPayload) {
        db.backupTransactionDao().replaceAll(
            objectDao = db.objectDao(),
            meshDao = db.meshDao(),
            baseDao = db.baseDao(),
            archiveDao = db.archiveDao(),
            profileDao = db.profileDao(),
            objects = payload.objects.map { it.toEntity() },
            messages = payload.meshMessages.map { it.toEntity() },
            articles = payload.baseArticles.map { it.toEntity() },
            archives = payload.archiveItems.map { it.toEntity() },
            profile = payload.profile.toEntity()
        )
        settingsStore.saveSettings(payload.settings)
    }

    private fun payloadFile(dbId: String): File = File(rootDir, "$dbId.json")

    private fun readPayloadFile(dbId: String): UrbexDbPayload? {
        val file = payloadFile(dbId)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<UrbexDbPayload>(file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    private fun writePayloadFile(dbId: String, payload: UrbexDbPayload) {
        payloadFile(dbId).writeText(json.encodeToString(payload), Charsets.UTF_8)
    }

    private fun writeIndexFile(index: StoredWorkspaceIndex) {
        indexFile.writeText(json.encodeToString(index), Charsets.UTF_8)
    }

    private fun refreshSecondarySnapshotsLocked(index: StoredWorkspaceIndex) {
        val map = LinkedHashMap<String, UrbexDbPayload>()
        for (meta in index.metas) {
            if (meta.id == index.activeDbId) continue
            readPayloadFile(meta.id)?.let { map[meta.id] = it }
        }
        secondarySnapshots.value = map
    }

    private inner class WorkspaceObjectRepository : ObjectRepository {
        override fun observeObjects(statusFilter: ReliabilityStatus?, query: String): Flow<List<UrbexObject>> {
            val trimmed = query.trim().lowercase(Locale.getDefault())
            return combine(
                db.objectDao().observeObjects(statusFilter?.name, query.trim()),
                indexState,
                secondarySnapshots
            ) { activeEntities, index, snapshots ->
                val seen = LinkedHashSet<String>()
                val merged = ArrayList<UrbexObject>()

                for (entity in activeEntities) {
                    val domain = entity.toDomain()
                    if (seen.add(domain.id)) {
                        merged.add(domain)
                    }
                }

                for (dbId in index.mountedDbIds) {
                    if (dbId == index.activeDbId) continue
                    val p = snapshots[dbId] ?: continue
                    for (obj in p.objects) {
                        if (!seen.add(obj.id)) continue
                        if (statusFilter != null && obj.status != statusFilter) continue
                        if (trimmed.isNotEmpty()) {
                            val match = obj.title.lowercase(Locale.getDefault()).contains(trimmed) ||
                                obj.description.lowercase(Locale.getDefault()).contains(trimmed) ||
                                obj.accessNotes.lowercase(Locale.getDefault()).contains(trimmed)
                            if (!match) continue
                        }
                        merged.add(obj)
                    }
                }
                merged.sortedByDescending { it.updatedAt }
            }
        }

        override suspend fun getObjectById(id: String): UrbexObject? {
            ensureInitialized()
            return withContext(Dispatchers.IO) {
                db.objectDao().getById(id)?.toDomain() ?: run {
                    val index = indexState.value
                    for (dbId in index.mountedDbIds) {
                        if (dbId == index.activeDbId) continue
                        val found = secondarySnapshots.value[dbId]?.objects?.firstOrNull { it.id == id }
                        if (found != null) return@withContext found
                    }
                    null
                }
            }
        }

        override suspend fun upsertObject(obj: UrbexObject) {
            ensureInitialized()
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val index = indexState.value
                    val ownerSecondary = index.mountedDbIds
                        .filter { it != index.activeDbId }
                        .firstOrNull { dbId ->
                            val existing = readPayloadFile(dbId)?.objects?.any { it.id == obj.id } == true
                            existing && db.objectDao().getById(obj.id) == null
                        }

                    if (ownerSecondary != null) {
                        val payload = readPayloadFile(ownerSecondary) ?: return@withLock
                        val updatedList = payload.objects.map { if (it.id == obj.id) obj else it }
                        writePayloadFile(ownerSecondary, payload.copy(objects = updatedList))
                        refreshSecondarySnapshotsLocked(index)
                    } else {
                        db.objectDao().upsert(obj.toEntity())
                        writePayloadFile(index.activeDbId, readActivePayloadFromRoom())
                    }
                    notifyDataMutated()
                }
            }
        }

        override suspend fun deleteObject(id: String) {
            ensureInitialized()
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val index = indexState.value
                    val inActive = db.objectDao().getById(id) != null
                    if (inActive) {
                        db.objectDao().deleteById(id)
                        writePayloadFile(index.activeDbId, readActivePayloadFromRoom())
                    }
                    for (dbId in index.mountedDbIds) {
                        if (dbId == index.activeDbId) continue
                        val payload = readPayloadFile(dbId) ?: continue
                        if (payload.objects.any { it.id == id }) {
                            writePayloadFile(dbId, payload.copy(objects = payload.objects.filterNot { it.id == id }))
                        }
                    }
                    refreshSecondarySnapshotsLocked(index)
                    notifyDataMutated()
                }
            }
        }
    }

    private inner class WorkspaceArchiveRepository : ArchiveRepository {
        override fun observeItems(typeFilter: ArchiveMediaType?): Flow<List<ArchiveItem>> {
            return combine(
                db.archiveDao().observeItems(typeFilter?.name),
                indexState,
                secondarySnapshots
            ) { activeEntities, index, snapshots ->
                val seen = LinkedHashSet<String>()
                val merged = ArrayList<ArchiveItem>()

                for (entity in activeEntities) {
                    val domain = entity.toDomain()
                    if (seen.add(domain.id)) merged.add(domain)
                }

                for (dbId in index.mountedDbIds) {
                    if (dbId == index.activeDbId) continue
                    val p = snapshots[dbId] ?: continue
                    for (item in p.archiveItems) {
                        if (!seen.add(item.id)) continue
                        if (typeFilter != null && item.type != typeFilter) continue
                        merged.add(item)
                    }
                }
                merged.sortedByDescending { it.createdAt }
            }
        }

        override suspend fun addItem(item: ArchiveItem) {
            ensureInitialized()
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    db.archiveDao().upsert(item.toEntity())
                    writePayloadFile(indexState.value.activeDbId, readActivePayloadFromRoom())
                    notifyDataMutated()
                }
            }
        }

        override suspend fun deleteItem(id: String) {
            ensureInitialized()
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val index = indexState.value
                    db.archiveDao().deleteById(id)
                    writePayloadFile(index.activeDbId, readActivePayloadFromRoom())
                    for (dbId in index.mountedDbIds) {
                        if (dbId == index.activeDbId) continue
                        val payload = readPayloadFile(dbId) ?: continue
                        if (payload.archiveItems.any { it.id == id }) {
                            writePayloadFile(dbId, payload.copy(archiveItems = payload.archiveItems.filterNot { it.id == id }))
                        }
                    }
                    refreshSecondarySnapshotsLocked(index)
                    notifyDataMutated()
                }
            }
        }
    }

    private inner class WorkspaceBaseRepository : BaseRepository {
        override fun observeArticles(query: String): Flow<List<BaseArticle>> {
            val trimmed = query.trim().lowercase(Locale.getDefault())
            return combine(
                db.baseDao().observeArticles(query.trim()),
                indexState,
                secondarySnapshots
            ) { activeEntities, index, snapshots ->
                val seen = LinkedHashSet<String>()
                val merged = ArrayList<BaseArticle>()

                for (entity in activeEntities) {
                    val domain = entity.toDomain()
                    if (seen.add(domain.id)) merged.add(domain)
                }

                for (dbId in index.mountedDbIds) {
                    if (dbId == index.activeDbId) continue
                    val p = snapshots[dbId] ?: continue
                    for (art in p.baseArticles) {
                        if (!seen.add(art.id)) continue
                        if (trimmed.isNotEmpty()) {
                            val match = art.title.lowercase(Locale.getDefault()).contains(trimmed) ||
                                art.summary.lowercase(Locale.getDefault()).contains(trimmed) ||
                                art.content.lowercase(Locale.getDefault()).contains(trimmed)
                            if (!match) continue
                        }
                        merged.add(art)
                    }
                }
                merged.sortedWith(compareByDescending<BaseArticle> { it.pinned }.thenByDescending { it.updatedAt })
            }
        }

        override suspend fun upsertArticle(article: BaseArticle) {
            ensureInitialized()
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val index = indexState.value
                    val ownerSecondary = index.mountedDbIds
                        .filter { it != index.activeDbId }
                        .firstOrNull { dbId ->
                            readPayloadFile(dbId)?.baseArticles?.any { it.id == article.id } == true
                        }
                    if (ownerSecondary != null && db.baseDao().getAllOnce().none { it.id == article.id }) {
                        val payload = readPayloadFile(ownerSecondary) ?: return@withLock
                        val updated = payload.baseArticles.map { if (it.id == article.id) article else it }
                        writePayloadFile(ownerSecondary, payload.copy(baseArticles = updated))
                        refreshSecondarySnapshotsLocked(index)
                    } else {
                        db.baseDao().upsert(article.toEntity())
                        writePayloadFile(index.activeDbId, readActivePayloadFromRoom())
                    }
                    notifyDataMutated()
                }
            }
        }

        override suspend fun deleteArticle(id: String) {
            ensureInitialized()
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val index = indexState.value
                    db.baseDao().deleteById(id)
                    writePayloadFile(index.activeDbId, readActivePayloadFromRoom())
                    for (dbId in index.mountedDbIds) {
                        if (dbId == index.activeDbId) continue
                        val payload = readPayloadFile(dbId) ?: continue
                        if (payload.baseArticles.any { it.id == id }) {
                            writePayloadFile(dbId, payload.copy(baseArticles = payload.baseArticles.filterNot { it.id == id }))
                        }
                    }
                    refreshSecondarySnapshotsLocked(index)
                    notifyDataMutated()
                }
            }
        }
    }

    private inner class WorkspaceBackupRepository : BackupRepository {
        override suspend fun exportPayload(): UrbexDbPayload {
            ensureInitialized()
            return withContext(Dispatchers.IO) {
                mutex.withLock {
                    val live = readActivePayloadFromRoom()
                    writePayloadFile(indexState.value.activeDbId, live)
                    live
                }
            }
        }

        override suspend fun importBundle(bundle: UrbexDbBundle, strategy: ConflictStrategy): MergeStats {
            ensureInitialized()
            return withContext(Dispatchers.IO) {
                mutex.withLock {
                    val current = readActivePayloadFromRoom()
                    val (merged, stats) = mergeUseCase.merge(current, bundle.payload, strategy)
                    val preserved = merged.copy(
                        settings = preserveDeviceSettings(merged.settings, current.settings)
                    )
                    writePayloadToRoom(preserved)
                    writePayloadFile(indexState.value.activeDbId, preserved)
                    notifyDataMutated()
                    stats
                }
            }
        }

        override suspend fun resetToSeed() {
            ensureInitialized()
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val curSettings = settingsStore.getOnce()
                    val now = System.currentTimeMillis()
                    UrbexDatabase.seedInto(db)
                    settingsStore.saveSettings(
                        preserveDeviceSettings(
                            AppSettings(
                                offlineSectors = SeedData.defaultOfflineSectors(),
                                radioChannels = RadioPlanUtils.createDefaultRadioChannels(now),
                                updatedAt = now
                            ),
                            curSettings
                        )
                    )
                    writePayloadFile(indexState.value.activeDbId, readActivePayloadFromRoom())
                }
            }
        }
    }

    companion object {
        const val DEFAULT_DB_ID = "db-moscow-main"
    }
}

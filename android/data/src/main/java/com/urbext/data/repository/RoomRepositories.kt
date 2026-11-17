package com.urbext.data.repository

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
import com.urbext.domain.model.MeshMessage
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexDbBundle
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile
import com.urbext.domain.repository.ArchiveRepository
import com.urbext.domain.repository.BackupRepository
import com.urbext.domain.repository.BaseRepository
import com.urbext.domain.repository.MergeStats
import com.urbext.domain.repository.MeshRepository
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.ProfileRepository
import com.urbext.domain.repository.SettingsRepository
import com.urbext.domain.usecase.MergeUrbexDbUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomObjectRepository(private val db: UrbexDatabase) : ObjectRepository {
    override fun observeObjects(statusFilter: ReliabilityStatus?, query: String): Flow<List<UrbexObject>> =
        db.objectDao().observeObjects(statusFilter?.name, query.trim()).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getObjectById(id: String): UrbexObject? = withContext(Dispatchers.IO) {
        db.objectDao().getById(id)?.toDomain()
    }

    override suspend fun upsertObject(obj: UrbexObject) = withContext(Dispatchers.IO) {
        db.objectDao().upsert(obj.toEntity())
    }

    override suspend fun deleteObject(id: String) = withContext(Dispatchers.IO) {
        db.objectDao().deleteById(id)
    }
}

class RoomMeshRepository(private val db: UrbexDatabase) : MeshRepository {
    override fun observeMessages(channelId: String): Flow<List<MeshMessage>> =
        db.meshDao().observeByChannel(channelId).map { list -> list.map { it.toDomain() } }

    override suspend fun sendMessage(message: MeshMessage) = withContext(Dispatchers.IO) {
        db.meshDao().upsert(message.toEntity())
    }
}

class RoomBaseRepository(private val db: UrbexDatabase) : BaseRepository {
    override fun observeArticles(query: String): Flow<List<BaseArticle>> =
        db.baseDao().observeArticles(query.trim()).map { list -> list.map { it.toDomain() } }

    override suspend fun upsertArticle(article: BaseArticle) = withContext(Dispatchers.IO) {
        db.baseDao().upsert(article.toEntity())
    }

    override suspend fun deleteArticle(id: String) = withContext(Dispatchers.IO) {
        db.baseDao().deleteById(id)
    }
}

class RoomArchiveRepository(private val db: UrbexDatabase) : ArchiveRepository {
    override fun observeItems(typeFilter: ArchiveMediaType?): Flow<List<ArchiveItem>> =
        db.archiveDao().observeItems(typeFilter?.name).map { list -> list.map { it.toDomain() } }

    override suspend fun addItem(item: ArchiveItem) = withContext(Dispatchers.IO) {
        db.archiveDao().upsert(item.toEntity())
    }

    override suspend fun deleteItem(id: String) = withContext(Dispatchers.IO) {
        db.archiveDao().deleteById(id)
    }
}

class RoomProfileRepository(private val db: UrbexDatabase) : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile> =
        db.profileDao().observeProfile().map { it?.toDomain() ?: SeedData.defaultProfile() }

    override suspend fun updateProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        db.profileDao().upsert(profile.toEntity())
    }
}

class DataStoreSettingsRepository(private val store: SettingsDataStore) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> = store.observeSettings()

    override suspend fun updateSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        store.saveSettings(settings)
    }
}

class RoomBackupRepository(
    private val db: UrbexDatabase,
    private val settingsStore: SettingsDataStore,
    private val mergeUseCase: MergeUrbexDbUseCase = MergeUrbexDbUseCase()
) : BackupRepository {
    override suspend fun exportPayload(): UrbexDbPayload = withContext(Dispatchers.IO) {
        UrbexDbPayload(
            objects = db.objectDao().getAllOnce().map { it.toDomain() },
            meshMessages = db.meshDao().getAllOnce().map { it.toDomain() },
            baseArticles = db.baseDao().getAllOnce().map { it.toDomain() },
            archiveItems = db.archiveDao().getAllOnce().map { it.toDomain() },
            profile = db.profileDao().getOnce()?.toDomain() ?: SeedData.defaultProfile(),
            settings = settingsStore.getOnce()
        )
    }

    override suspend fun importBundle(bundle: UrbexDbBundle, strategy: ConflictStrategy): MergeStats =
        withContext(Dispatchers.IO) {
            val current = exportPayload()
            val (merged, stats) = mergeUseCase.merge(current, bundle.payload, strategy)
            db.backupTransactionDao().replaceAll(
                objectDao = db.objectDao(),
                meshDao = db.meshDao(),
                baseDao = db.baseDao(),
                archiveDao = db.archiveDao(),
                profileDao = db.profileDao(),
                objects = merged.objects.map { it.toEntity() },
                messages = merged.meshMessages.map { it.toEntity() },
                articles = merged.baseArticles.map { it.toEntity() },
                archives = merged.archiveItems.map { it.toEntity() },
                profile = merged.profile.toEntity()
            )
            settingsStore.saveSettings(merged.settings)
            stats
        }

    override suspend fun resetToSeed() = withContext(Dispatchers.IO) {
        UrbexDatabase.seedInto(db)
        settingsStore.saveSettings(
            AppSettings(
                offlineSectors = SeedData.defaultOfflineSectors(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}

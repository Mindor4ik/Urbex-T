package com.urbext.app

import android.content.Context
import com.urbext.data.local.SettingsDataStore
import com.urbext.data.local.UrbexDatabase
import com.urbext.data.repository.DataStoreSettingsRepository
import com.urbext.data.repository.RoomMeshRepository
import com.urbext.data.repository.RoomProfileRepository
import com.urbext.data.repository.WorkspaceManager
import com.urbext.data.sync.CloudSyncEngine
import com.urbext.data.sync.CloudSyncScheduler
import com.urbext.data.sync.CloudSyncWorkBridge
import com.urbext.domain.repository.ArchiveRepository
import com.urbext.domain.repository.BackupRepository
import com.urbext.domain.repository.BaseRepository
import com.urbext.domain.repository.MeshRepository
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.ProfileRepository
import com.urbext.domain.repository.SettingsRepository
import com.urbext.domain.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lightweight zero-reflection DI container keeping cold startup fast on budget hardware.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { UrbexDatabase.getInstance(appContext) }
    private val settingsStore by lazy { SettingsDataStore(appContext) }

    val workspaceManager by lazy { WorkspaceManager(appContext, database, settingsStore) }
    val workspaceRepository: WorkspaceRepository by lazy { workspaceManager }

    val cloudSyncEngine by lazy { CloudSyncEngine(appContext, workspaceManager, settingsStore) }
    val cloudSyncScheduler by lazy { CloudSyncScheduler(appContext) }

    val objectRepository: ObjectRepository by lazy { workspaceManager.objectRepository }
    val meshRepository: MeshRepository by lazy { RoomMeshRepository(database) { triggerDebouncedSync() } }
    val baseRepository: BaseRepository by lazy { workspaceManager.baseRepository }
    val archiveRepository: ArchiveRepository by lazy { workspaceManager.archiveRepository }
    val profileRepository: ProfileRepository by lazy { RoomProfileRepository(database) { triggerDebouncedSync() } }
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(settingsStore) { triggerDebouncedSync() } }
    val backupRepository: BackupRepository by lazy { workspaceManager.backupRepository }

    init {
        CloudSyncWorkBridge.engineFactory = { _ -> cloudSyncEngine }
        workspaceManager.onDataMutated = { triggerDebouncedSync() }
        syncScope.launch {
            settingsStore.observeSettings()
                .map {
                    listOf(
                        it.cloudSync.enabled,
                        it.cloudSync.provider,
                        it.cloudSync.autoSyncIntervalMin,
                        it.cloudSync.wifiOnly
                    )
                }
                .distinctUntilChanged()
                .collect {
                    cloudSyncScheduler.applyConfig(settingsStore.getOnce().cloudSync)
                }
        }
    }

    private fun triggerDebouncedSync() {
        syncScope.launch {
            val cfg = settingsStore.getOnce().cloudSync
            cloudSyncScheduler.onLocalDataMutated(cfg)
        }
    }
}


package com.urbext.app

import android.content.Context
import com.urbext.data.local.SettingsDataStore
import com.urbext.data.local.UrbexDatabase
import com.urbext.data.repository.DataStoreSettingsRepository
import com.urbext.data.repository.RoomMeshRepository
import com.urbext.data.repository.RoomProfileRepository
import com.urbext.data.repository.WorkspaceManager
import com.urbext.domain.repository.ArchiveRepository
import com.urbext.domain.repository.BackupRepository
import com.urbext.domain.repository.BaseRepository
import com.urbext.domain.repository.MeshRepository
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.ProfileRepository
import com.urbext.domain.repository.SettingsRepository
import com.urbext.domain.repository.WorkspaceRepository

/**
 * Lightweight zero-reflection DI container keeping cold startup fast on budget hardware.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { UrbexDatabase.getInstance(appContext) }
    private val settingsStore by lazy { SettingsDataStore(appContext) }

    val workspaceManager by lazy { WorkspaceManager(appContext, database, settingsStore) }
    val workspaceRepository: WorkspaceRepository by lazy { workspaceManager }

    val objectRepository: ObjectRepository by lazy { workspaceManager.objectRepository }
    val meshRepository: MeshRepository by lazy { RoomMeshRepository(database) }
    val baseRepository: BaseRepository by lazy { workspaceManager.baseRepository }
    val archiveRepository: ArchiveRepository by lazy { workspaceManager.archiveRepository }
    val profileRepository: ProfileRepository by lazy { RoomProfileRepository(database) }
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(settingsStore) }
    val backupRepository: BackupRepository by lazy { workspaceManager.backupRepository }
}

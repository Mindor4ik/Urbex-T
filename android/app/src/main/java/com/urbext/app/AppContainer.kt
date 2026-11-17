package com.urbext.app

import android.content.Context
import com.urbext.data.local.SettingsDataStore
import com.urbext.data.local.UrbexDatabase
import com.urbext.data.repository.DataStoreSettingsRepository
import com.urbext.data.repository.RoomArchiveRepository
import com.urbext.data.repository.RoomBackupRepository
import com.urbext.data.repository.RoomBaseRepository
import com.urbext.data.repository.RoomMeshRepository
import com.urbext.data.repository.RoomObjectRepository
import com.urbext.data.repository.RoomProfileRepository
import com.urbext.domain.repository.ArchiveRepository
import com.urbext.domain.repository.BackupRepository
import com.urbext.domain.repository.BaseRepository
import com.urbext.domain.repository.MeshRepository
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.ProfileRepository
import com.urbext.domain.repository.SettingsRepository

/**
 * Lightweight zero-reflection DI container keeping cold startup fast on budget hardware.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { UrbexDatabase.getInstance(appContext) }
    private val settingsStore by lazy { SettingsDataStore(appContext) }

    val objectRepository: ObjectRepository by lazy { RoomObjectRepository(database) }
    val meshRepository: MeshRepository by lazy { RoomMeshRepository(database) }
    val baseRepository: BaseRepository by lazy { RoomBaseRepository(database) }
    val archiveRepository: ArchiveRepository by lazy { RoomArchiveRepository(database) }
    val profileRepository: ProfileRepository by lazy { RoomProfileRepository(database) }
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(settingsStore) }
    val backupRepository: BackupRepository by lazy { RoomBackupRepository(database, settingsStore) }
}

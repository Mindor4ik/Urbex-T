package com.urbext.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.urbext.core.backup.UrbexDbSerializer
import com.urbext.data.local.SettingsDataStore
import com.urbext.data.local.UrbexDatabase
import com.urbext.data.repository.RoomBackupRepository
import java.io.File

/**
 * On-demand background worker that writes an integrity-checked .urbexdb snapshot
 * to app-private filesDir without running persistent foreground services.
 */
class BackupVerifyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            val db = UrbexDatabase.getInstance(applicationContext)
            val settingsStore = SettingsDataStore(applicationContext)
            val repo = RoomBackupRepository(db, settingsStore)
            val payload = repo.exportPayload()
            val serialized = UrbexDbSerializer.serialize(payload, payload.profile.nodeId)
            val backupDir = File(applicationContext.filesDir, "backups").apply { mkdirs() }
            File(backupDir, "latest.urbexdb").writeText(serialized, Charsets.UTF_8)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}

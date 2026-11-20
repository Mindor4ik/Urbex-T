package com.urbext.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.urbext.domain.model.CloudSyncConfig
import com.urbext.domain.model.CloudSyncProvider
import java.util.concurrent.TimeUnit

object CloudSyncWorkBridge {
    @Volatile
    var engineFactory: ((Context) -> CloudSyncEngine)? = null
}

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val factory = CloudSyncWorkBridge.engineFactory ?: return Result.success()
        val engine = factory(applicationContext)
        val (ok, _) = engine.syncNow(manual = false)
        return if (ok) Result.success() else Result.retry()
    }
}

class CloudSyncScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun applyConfig(config: CloudSyncConfig) {
        if (!config.enabled || config.provider == CloudSyncProvider.NONE) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            workManager.cancelUniqueWork(DEBOUNCED_WORK_NAME)
            return
        }

        val networkType = when {
            config.provider == CloudSyncProvider.LOCAL_FOLDER -> NetworkType.NOT_REQUIRED
            config.wifiOnly -> NetworkType.UNMETERED
            else -> NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val intervalMin = config.autoSyncIntervalMin.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(intervalMin, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun onLocalDataMutated(config: CloudSyncConfig) {
        if (!config.enabled || !config.autoSyncOnChange || config.provider == CloudSyncProvider.NONE) {
            return
        }

        val networkType = when {
            config.provider == CloudSyncProvider.LOCAL_FOLDER -> NetworkType.NOT_REQUIRED
            config.wifiOnly -> NetworkType.UNMETERED
            else -> NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInitialDelay(4, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            DEBOUNCED_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "urbex_cloud_periodic_sync"
        private const val DEBOUNCED_WORK_NAME = "urbex_cloud_debounced_sync"
    }
}

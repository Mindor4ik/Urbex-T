package com.urbext.app

import android.app.Application
import android.os.StrictMode
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class UrbexApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imageLoaderLock = Any()

    @Volatile
    private var currentImageLoader: ImageLoader? = null

    @Volatile
    private var activeCacheConfig: Pair<Int, Boolean>? = null

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
        container = AppContainer(this)
        appScope.launch {
            container.observeImageCacheConfig()
                .map { (limitMb, lowMem) -> effectiveCacheConfig(limitMb, lowMem) }
                .distinctUntilChanged()
                .collect { effective ->
                    val activeDirName = cacheDirName(effective.first)
                    val swap = synchronized(imageLoaderLock) {
                        if (activeCacheConfig == effective && currentImageLoader != null) {
                            null
                        } else {
                            val prev = currentImageLoader
                            val next = buildImageLoader(effective.first, effective.second)
                            currentImageLoader = next
                            activeCacheConfig = effective
                            next to prev
                        }
                    } ?: return@collect
                    Coil.setImageLoader(swap.first)
                    swap.second?.memoryCache?.clear()
                    cleanupUnusedImageCacheDirs(activeDirName)
                }
        }
    }

    /**
     * Bounded RAM and disk image cache respecting Settings (imageCacheLimitMb + lowMemoryMode).
     */
    override fun newImageLoader(): ImageLoader {
        currentImageLoader?.let { return it }
        synchronized(imageLoaderLock) {
            currentImageLoader?.let { return it }
            val fallback = buildImageLoader(64, lowMemoryMode = false)
            currentImageLoader = fallback
            activeCacheConfig = 64 to false
            return fallback
        }
    }

    private fun effectiveCacheConfig(limitMb: Int, lowMemoryMode: Boolean): Pair<Int, Boolean> {
        val effectiveMb = if (lowMemoryMode) {
            minOf(limitMb, 32).coerceAtLeast(16)
        } else {
            limitMb.coerceIn(16, 512)
        }
        return effectiveMb to lowMemoryMode
    }

    private fun cacheDirName(effectiveMb: Int): String = "image_cache_${effectiveMb}mb"

    private fun buildImageLoader(effectiveMb: Int, lowMemoryMode: Boolean): ImageLoader {
        val memPercent = if (lowMemoryMode) 0.08 else 0.15
        val targetDir = cacheDir.resolve(cacheDirName(effectiveMb))
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(targetDir)
                    .maxSizeBytes(effectiveMb.toLong() * 1024L * 1024L)
                    .build()
            }
            .crossfade(false)
            .build()
    }

    private fun cleanupUnusedImageCacheDirs(activeDirName: String) {
        runCatching {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory &&
                    (file.name == "image_cache" || file.name.startsWith("image_cache_")) &&
                    file.name != activeDirName
                ) {
                    file.deleteRecursively()
                }
            }
        }
    }
}

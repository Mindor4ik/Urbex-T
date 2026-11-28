package com.urbext.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.urbext.domain.model.GpsFix
import com.urbext.domain.model.PowerProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Battery-aware Android LocationManager client.
 * Works on all Android devices (no proprietary Play Services lock-in),
 * pauses cleanly when observers unsubscribe, and supports manual single-fix mode.
 */
class AdaptiveLocationClient(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun isGpsProviderAvailable(): Boolean {
        val lm = locationManager ?: return false
        return runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownFixOrDefault(defaultLat: Double = 55.751244, defaultLon: Double = 37.618423): GpsFix {
        val cached = latestSharedFix
        if (cached != null) return cached
        val lm = locationManager
        val loc = if (lm != null) {
            runCatching {
                val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                when {
                    gpsLoc == null -> netLoc
                    netLoc == null -> gpsLoc
                    netLoc.time - gpsLoc.time > 120_000L -> netLoc
                    else -> gpsLoc
                }
            }.getOrNull()
        } else {
            null
        }
        return loc?.toGpsFix(source = "cached") ?: GpsFix(
            lat = defaultLat,
            lon = defaultLon,
            altitudeM = 142,
            accuracyM = 12,
            timestamp = System.currentTimeMillis(),
            source = "cached"
        )
    }

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(profile: PowerProfile): Flow<GpsFix> = callbackFlow {
        val lm = locationManager
        if (lm == null || profile == PowerProfile.MANUAL) {
            close()
            return@callbackFlow
        }

        val token = registerSubscriber(lm, profile) { fix ->
            trySend(fix)
        }

        awaitClose {
            unregisterSubscriber(lm, token)
        }
    }

    private fun Location.toGpsFix(source: String): GpsFix = GpsFix(
        lat = latitude,
        lon = longitude,
        altitudeM = if (hasAltitude()) altitude.toInt() else null,
        accuracyM = if (hasAccuracy()) accuracy.toInt() else null,
        timestamp = time,
        source = source
    )

    private data class SubscriberEntry(
        val profile: PowerProfile,
        val emit: (GpsFix) -> Unit
    )

    private companion object {
        private const val GPS_PRIORITY_WINDOW_MS = 15_000L

        private val hubLock = Any()
        private var nextToken = 1L
        private val subscribers = LinkedHashMap<Long, SubscriberEntry>()
        private var activeProfile: PowerProfile? = null
        private var activeListener: LocationListener? = null
        private var lastGpsFixMs: Long = 0L

        @Volatile
        private var latestSharedFix: GpsFix? = null

        private fun PowerProfile.priorityRank(): Int = when (this) {
            PowerProfile.PRECISION -> 3
            PowerProfile.BALANCED -> 2
            PowerProfile.ECO -> 1
            PowerProfile.MANUAL -> 0
        }

        private fun Location.toFix(source: String): GpsFix = GpsFix(
            lat = latitude,
            lon = longitude,
            altitudeM = if (hasAltitude()) altitude.toInt() else null,
            accuracyM = if (hasAccuracy()) accuracy.toInt() else null,
            timestamp = time,
            source = source
        )

        fun registerSubscriber(
            lm: LocationManager,
            profile: PowerProfile,
            emit: (GpsFix) -> Unit
        ): Long {
            val cachedToReplay: GpsFix?
            val token: Long
            synchronized(hubLock) {
                token = nextToken++
                subscribers[token] = SubscriberEntry(profile, emit)
                reconcileUpdatesLocked(lm)
                cachedToReplay = latestSharedFix
            }
            cachedToReplay?.let(emit)
            return token
        }

        fun unregisterSubscriber(lm: LocationManager, token: Long) {
            synchronized(hubLock) {
                subscribers.remove(token)
                reconcileUpdatesLocked(lm)
            }
        }

        @SuppressLint("MissingPermission")
        private fun reconcileUpdatesLocked(lm: LocationManager) {
            val desiredProfile = subscribers.values
                .map { it.profile }
                .filter { it != PowerProfile.MANUAL }
                .maxByOrNull { it.priorityRank() }

            if (desiredProfile == null) {
                activeListener?.let { listener ->
                    runCatching { lm.removeUpdates(listener) }
                }
                activeListener = null
                activeProfile = null
                return
            }

            if (activeListener != null && activeProfile == desiredProfile) {
                return
            }

            activeListener?.let { listener ->
                runCatching { lm.removeUpdates(listener) }
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val now = System.currentTimeMillis()
                    val isGps = location.provider == LocationManager.GPS_PROVIDER
                    val shouldEmit: Boolean
                    val targets: List<(GpsFix) -> Unit>
                    val fix = location.toFix(source = if (isGps) "gps" else "network")
                    synchronized(hubLock) {
                        if (isGps) {
                            lastGpsFixMs = now
                            shouldEmit = true
                        } else {
                            shouldEmit = (now - lastGpsFixMs) > GPS_PRIORITY_WINDOW_MS
                        }
                        if (shouldEmit) {
                            latestSharedFix = fix
                            targets = subscribers.values.map { it.emit }
                        } else {
                            targets = emptyList()
                        }
                    }
                    if (shouldEmit) {
                        targets.forEach { it(fix) }
                    }
                }

                @Deprecated("Deprecated in API 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }

            val minTimeMs = desiredProfile.intervalMs.coerceAtLeast(2_000L)
            val minDistanceM = when (desiredProfile) {
                PowerProfile.ECO -> 25f
                PowerProfile.BALANCED -> 10f
                PowerProfile.PRECISION -> 2f
                PowerProfile.MANUAL -> 0f
            }

            var anyRequested = false
            if (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                val ok = runCatching {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        minTimeMs,
                        minDistanceM,
                        listener,
                        Looper.getMainLooper()
                    )
                }.isSuccess
                if (ok) anyRequested = true
            }
            if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                val ok = runCatching {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        minTimeMs.coerceAtLeast(5_000L),
                        minDistanceM.coerceAtLeast(15f),
                        listener,
                        Looper.getMainLooper()
                    )
                }.isSuccess
                if (ok) anyRequested = true
            }

            if (anyRequested) {
                activeListener = listener
                activeProfile = desiredProfile
            } else {
                activeListener = null
                activeProfile = null
            }
        }
    }
}

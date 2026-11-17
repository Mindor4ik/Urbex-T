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
        val lm = locationManager
        val loc = if (lm != null) {
            runCatching {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
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

        val minTimeMs = profile.intervalMs.coerceAtLeast(2_000L)
        val minDistanceM = when (profile) {
            PowerProfile.ECO -> 25f
            PowerProfile.BALANCED -> 10f
            PowerProfile.PRECISION -> 2f
            PowerProfile.MANUAL -> 0f
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toGpsFix(source = "gps"))
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider != null) {
            runCatching {
                lm.requestLocationUpdates(
                    provider,
                    minTimeMs,
                    minDistanceM,
                    listener,
                    Looper.getMainLooper()
                )
            }
        }

        awaitClose {
            runCatching { lm.removeUpdates(listener) }
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
}

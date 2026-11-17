package com.urbext.core.geo

import com.urbext.domain.model.CoordinateFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
    }

    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
            sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        val deg = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        return deg.roundToInt() % 360
    }

    fun formatDistance(meters: Double): String {
        if (!meters.isFinite() || meters < 0) return "—"
        return if (meters < 1000.0) {
            "${meters.roundToInt()} м"
        } else {
            String.format(Locale.US, "%.2f км", meters / 1000.0)
        }
    }

    fun formatCoordinates(lat: Double, lon: Double, format: CoordinateFormat): String {
        if (!isValidCoordinate(lat, lon)) return "Нет координат"
        return when (format) {
            CoordinateFormat.DD -> {
                val latHem = if (lat >= 0) "N" else "S"
                val lonHem = if (lon >= 0) "E" else "W"
                String.format(
                    Locale.US,
                    "%.5f° %s, %.5f° %s",
                    kotlin.math.abs(lat),
                    latHem,
                    kotlin.math.abs(lon),
                    lonHem
                )
            }
            CoordinateFormat.DMS -> {
                val latDms = toDms(kotlin.math.abs(lat), if (lat >= 0) "N" else "S")
                val lonDms = toDms(kotlin.math.abs(lon), if (lon >= 0) "E" else "W")
                "$latDms $lonDms"
            }
            CoordinateFormat.UTM -> toUtm(lat, lon)
        }
    }

    private fun toDms(absDeg: Double, hemisphere: String): String {
        val d = floor(absDeg).toInt()
        val minFloat = (absDeg - d) * 60.0
        val m = floor(minFloat).toInt()
        val s = (minFloat - m) * 60.0
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", d, m, s, hemisphere)
    }

    private fun toUtm(lat: Double, lon: Double): String {
        val zone = floor((lon + 180.0) / 6.0).toInt() + 1
        val bands = "CDEFGHJKLMNPQRSTUVWX"
        val bandIdx = floor((lat + 80.0) / 8.0).toInt().coerceIn(0, bands.length - 1)
        val band = bands[bandIdx]
        val centralMeridian = (zone - 1) * 6.0 - 180.0 + 3.0
        val radLat = lat * PI / 180.0
        val radLonDelta = (lon - centralMeridian) * PI / 180.0
        val easting = (500_000.0 + EARTH_RADIUS_M * cos(radLat) * radLonDelta).roundToInt()
        val northingRaw = (EARTH_RADIUS_M * radLat).roundToInt()
        val northing = if (lat >= 0) northingRaw else 10_000_000 + northingRaw
        return String.format(Locale.US, "%d%c %06dE %07dN", zone, band, easting, northing)
    }
}

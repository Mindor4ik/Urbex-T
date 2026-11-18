package com.urbext.core.geo

import com.urbext.domain.model.CoordinateFormat
import com.urbext.domain.model.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
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

    enum class GpsSignalLevel(val label: String, val hexColor: String) {
        GOOD("Точный", "#6DB386"),
        MODERATE("Средний", "#DF9E43"),
        POOR("Слабый / нет", "#DC6860")
    }

    fun gpsSignalLevel(accuracyM: Int?, hasError: Boolean = false): GpsSignalLevel {
        if (hasError || accuracyM == null || accuracyM <= 0) return GpsSignalLevel.POOR
        return when {
            accuracyM <= 15 -> GpsSignalLevel.GOOD
            accuracyM <= 45 -> GpsSignalLevel.MODERATE
            else -> GpsSignalLevel.POOR
        }
    }

    fun buildCirclePolygonPoints(
        lat: Double,
        lon: Double,
        radiusMeters: Double,
        steps: Int = 48,
        maxRadiusMeters: Double = 2500.0
    ): List<Pair<Double, Double>> {
        val safeRadius = radiusMeters.coerceIn(5.0, maxRadiusMeters)
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.1)
        val dLat = safeRadius / metersPerDegLat
        val dLon = safeRadius / metersPerDegLon
        val points = ArrayList<Pair<Double, Double>>(steps + 1)
        for (i in 0..steps) {
            val angle = (2.0 * PI * i) / steps
            val pLon = lon + dLon * cos(angle)
            val pLat = lat + dLat * sin(angle)
            points.add(pLon to pLat)
        }
        return points
    }

    fun computePolygonPerimeterMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in points.indices) {
            if (points.size == 2 && i == 1) break
            val a = points[i]
            val b = points[(i + 1) % points.size]
            total += haversineDistanceMeters(a.lat, a.lon, b.lat, b.lon)
        }
        return total
    }

    fun computePolygonAreaM2(points: List<GeoPoint>): Double {
        if (points.size < 3) return 0.0
        val avgLat = points.sumOf { it.lat } / points.size
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(avgLat)).coerceAtLeast(0.1)
        val originLat = points[0].lat
        val originLon = points[0].lon

        var area2 = 0.0
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            val x1 = (p1.lon - originLon) * metersPerDegLon
            val y1 = (p1.lat - originLat) * metersPerDegLat
            val x2 = (p2.lon - originLon) * metersPerDegLon
            val y2 = (p2.lat - originLat) * metersPerDegLat
            area2 += x1 * y2 - x2 * y1
        }
        return abs(area2) / 2.0
    }

    fun formatArea(areaM2: Double): String {
        if (!areaM2.isFinite() || areaM2 <= 0.0) return "0 м²"
        return if (areaM2 < 10_000.0) {
            "${areaM2.roundToInt()} м²"
        } else {
            String.format(Locale.US, "%.2f га", areaM2 / 10_000.0)
        }
    }

    fun isPointInPolygon(lat: Double, lon: Double, polygon: List<GeoPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].lon
            val yi = polygon[i].lat
            val xj = polygon[j].lon
            val yj = polygon[j].lat
            val intersect = (yi > lat) != (yj > lat) &&
                lon < ((xj - xi) * (lat - yi)) / (yj - yi + 1e-12) + xi
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    fun buildZoneCircle(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        steps: Int = 16
    ): List<GeoPoint> {
        val safeR = radiusMeters.coerceIn(10.0, 5_000.0)
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(centerLat)).coerceAtLeast(0.1)
        val dLat = safeR / metersPerDegLat
        val dLon = safeR / metersPerDegLon
        return (0 until steps).map { i ->
            val angle = (2.0 * PI * i) / steps
            val lat = String.format(Locale.US, "%.6f", centerLat + dLat * sin(angle)).toDouble()
            val lon = String.format(Locale.US, "%.6f", centerLon + dLon * cos(angle)).toDouble()
            GeoPoint(lat = lat, lon = lon)
        }
    }

    fun simplifyPolygonPoints(
        points: List<GeoPoint>,
        minSpacingMeters: Double = 8.0,
        maxPoints: Int = 48
    ): List<GeoPoint> {
        if (points.size <= 3) return points
        val filtered = ArrayList<GeoPoint>()
        filtered.add(points[0])
        for (i in 1 until points.size) {
            val prev = filtered.last()
            if (haversineDistanceMeters(prev.lat, prev.lon, points[i].lat, points[i].lon) >= minSpacingMeters) {
                filtered.add(points[i])
            }
        }
        if (filtered.size <= maxPoints) return filtered
        val step = ceil(filtered.size.toDouble() / maxPoints.toDouble()).toInt().coerceAtLeast(1)
        return filtered.filterIndexed { index, _ -> index % step == 0 }.take(maxPoints)
    }

    fun formatShortDate(timestampMs: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes Б"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f КБ", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f МБ", mb)
    }
}


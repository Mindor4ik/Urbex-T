package com.urbext.core.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.DomainDisabled
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.urbext.domain.model.ObjectIcon
import kotlin.math.cos
import kotlin.math.sin

fun ObjectIcon.vector(): ImageVector = when (this) {
    ObjectIcon.FACTORY -> Icons.Default.Factory
    ObjectIcon.BUNKER -> Icons.Default.Shield
    ObjectIcon.BUILDING -> Icons.Default.Apartment
    ObjectIcon.TOWER -> Icons.Default.CellTower
    ObjectIcon.TUNNEL -> Icons.Default.DirectionsSubway
    ObjectIcon.WAREHOUSE -> Icons.Default.Warehouse
    ObjectIcon.POWER -> Icons.Default.Bolt
    ObjectIcon.BOILER -> Icons.Default.LocalFireDepartment
    ObjectIcon.WATER -> Icons.Default.WaterDrop
    ObjectIcon.FUEL -> Icons.Default.LocalGasStation
    ObjectIcon.RADAR -> Icons.Default.Radar
    ObjectIcon.RADIATION -> Icons.Default.Science
    ObjectIcon.HAZARD -> Icons.Default.Warning
    ObjectIcon.SKULL -> Icons.Default.Dangerous
    ObjectIcon.OBSERVATION -> Icons.Default.Visibility
    ObjectIcon.GATE -> Icons.Default.Lock
    ObjectIcon.CRANE -> Icons.Default.Construction
    ObjectIcon.HOSPITAL -> Icons.Default.LocalHospital
    ObjectIcon.AIRFIELD -> Icons.Default.Flight
    ObjectIcon.PORT -> Icons.Default.DirectionsBoat
    ObjectIcon.SHAFT -> Icons.Default.Terrain
    ObjectIcon.WORKSHOP -> Icons.Default.Build
    ObjectIcon.CAMERA -> Icons.Default.Videocam
    ObjectIcon.PIN -> Icons.Default.Place
    ObjectIcon.MILITARY -> Icons.Default.Flag
    ObjectIcon.SILO -> Icons.Default.RocketLaunch
    ObjectIcon.LAB -> Icons.Default.Biotech
    ObjectIcon.BIOHAZARD -> Icons.Default.BugReport
    ObjectIcon.ANTENNA -> Icons.Default.Sensors
    ObjectIcon.TRAIN -> Icons.Default.Train
    ObjectIcon.BRIDGE -> Icons.Default.AltRoute
    ObjectIcon.DAM -> Icons.Default.Waves
    ObjectIcon.MONUMENT -> Icons.Default.AccountBalance
    ObjectIcon.SCHOOL -> Icons.Default.School
    ObjectIcon.HOTEL -> Icons.Default.Hotel
    ObjectIcon.RUINS -> Icons.Default.DomainDisabled
    ObjectIcon.SHELTER -> Icons.Default.HealthAndSafety
    ObjectIcon.FIRE -> Icons.Default.Emergency
    ObjectIcon.COMPASS -> Icons.Default.Explore
    ObjectIcon.STASH -> Icons.Default.Inventory2
    ObjectIcon.SUBSTATION -> Icons.Default.ElectricalServices
    ObjectIcon.COOLING -> Icons.Default.FilterDrama
    ObjectIcon.CISTERN -> Icons.Default.Storage
    ObjectIcon.ELEVATOR -> Icons.Default.Domain
    ObjectIcon.HANGAR -> Icons.Default.Garage
    ObjectIcon.CHECKPOINT -> Icons.Default.Security
    ObjectIcon.BARRACKS -> Icons.Default.Groups
    ObjectIcon.RANGE -> Icons.Default.GpsFixed
    ObjectIcon.SEARCHLIGHT -> Icons.Default.WbSunny
    ObjectIcon.MUNITION -> Icons.Default.Inventory
    ObjectIcon.METRO -> Icons.Default.Subway
    ObjectIcon.VENT -> Icons.Default.Air
    ObjectIcon.DRAINAGE -> Icons.Default.Water
    ObjectIcon.CATACOMB -> Icons.Default.Hub
    ObjectIcon.VAULT -> Icons.Default.VpnKey
    ObjectIcon.DEPOT -> Icons.Default.DirectionsRailway
    ObjectIcon.BRIDGE_RAIL -> Icons.Default.ViewColumn
    ObjectIcon.PIER -> Icons.Default.Anchor
    ObjectIcon.LIGHTHOUSE -> Icons.Default.Lightbulb
    ObjectIcon.QUARRY -> Icons.Default.Landscape
    ObjectIcon.MINE_CART -> Icons.Default.Hardware
    ObjectIcon.OIL_RIG -> Icons.Default.Speed
    ObjectIcon.PIPELINE -> Icons.Default.Cable
    ObjectIcon.MORGUE -> Icons.Default.MedicalServices
    ObjectIcon.SANATORIUM -> Icons.Default.Park
    ObjectIcon.CHURCH -> Icons.Default.Church
    ObjectIcon.CASTLE -> Icons.Default.Castle
    ObjectIcon.THEATER -> Icons.Default.TheaterComedy
    ObjectIcon.STADIUM -> Icons.Default.Stadium
    ObjectIcon.MALL -> Icons.Default.Storefront
    ObjectIcon.GENERATOR -> Icons.Default.BatteryChargingFull
    ObjectIcon.EVAC -> Icons.Default.ExitToApp
}

/**
 * Draws a crisp monochrome vector glyph for any [ObjectIcon] onto an Android [Canvas],
 * used in MapLibre badge and circular pin bitmaps.
 */
fun drawObjectIconOnCanvas(
    canvas: Canvas,
    icon: ObjectIcon,
    cx: Float,
    cy: Float,
    sizePx: Float,
    colorInt: Int
) {
    val r = sizePx * 0.44f
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = Paint.Style.STROKE
        strokeWidth = (sizePx * 0.13f).coerceAtLeast(1.6f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = Paint.Style.FILL
    }

    when (icon) {
        ObjectIcon.FACTORY -> {
            val path = Path().apply {
                moveTo(cx - r, cy + r * 0.85f)
                lineTo(cx - r, cy - r * 0.1f)
                lineTo(cx - r * 0.3f, cy + r * 0.25f)
                lineTo(cx - r * 0.3f, cy - r * 0.1f)
                lineTo(cx + r * 0.4f, cy + r * 0.25f)
                lineTo(cx + r * 0.4f, cy - r * 0.8f)
                lineTo(cx + r, cy - r * 0.8f)
                lineTo(cx + r, cy + r * 0.85f)
                close()
            }
            canvas.drawPath(path, stroke)
        }
        ObjectIcon.BUNKER -> {
            val path = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.85f, cy - r * 0.55f)
                lineTo(cx + r * 0.85f, cy + r * 0.15f)
                quadTo(cx + r * 0.55f, cy + r * 0.78f, cx, cy + r)
                quadTo(cx - r * 0.55f, cy + r * 0.78f, cx - r * 0.85f, cy + r * 0.15f)
                lineTo(cx - r * 0.85f, cy - r * 0.55f)
                close()
            }
            canvas.drawPath(path, stroke)
        }
        ObjectIcon.BUILDING -> {
            val rect = RectF(cx - r * 0.65f, cy - r * 0.9f, cx + r * 0.65f, cy + r * 0.9f)
            canvas.drawRect(rect, stroke)
            canvas.drawCircle(cx - r * 0.24f, cy - r * 0.42f, r * 0.11f, fill)
            canvas.drawCircle(cx + r * 0.24f, cy - r * 0.42f, r * 0.11f, fill)
            canvas.drawCircle(cx - r * 0.24f, cy, r * 0.11f, fill)
            canvas.drawCircle(cx + r * 0.24f, cy, r * 0.11f, fill)
        }
        ObjectIcon.TOWER -> {
            canvas.drawLine(cx, cy - r * 0.9f, cx - r * 0.65f, cy + r * 0.9f, stroke)
            canvas.drawLine(cx, cy - r * 0.9f, cx + r * 0.65f, cy + r * 0.9f, stroke)
            canvas.drawLine(cx - r * 0.42f, cy + r * 0.25f, cx + r * 0.42f, cy + r * 0.25f, stroke)
            canvas.drawCircle(cx, cy - r * 0.85f, r * 0.16f, fill)
        }
        ObjectIcon.TUNNEL -> {
            val arch = RectF(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f)
            canvas.drawArc(arch, 180f, 180f, false, stroke)
            canvas.drawLine(cx - r * 0.85f, cy, cx - r * 0.85f, cy + r * 0.85f, stroke)
            canvas.drawLine(cx + r * 0.85f, cy, cx + r * 0.85f, cy + r * 0.85f, stroke)
            canvas.drawLine(cx - r, cy + r * 0.85f, cx + r, cy + r * 0.85f, stroke)
        }
        ObjectIcon.WAREHOUSE -> {
            val path = Path().apply {
                moveTo(cx - r, cy + r * 0.85f)
                lineTo(cx - r, cy - r * 0.2f)
                lineTo(cx, cy - r * 0.85f)
                lineTo(cx + r, cy - r * 0.2f)
                lineTo(cx + r, cy + r * 0.85f)
                close()
            }
            canvas.drawPath(path, stroke)
            canvas.drawLine(cx - r * 0.45f, cy + r * 0.2f, cx + r * 0.45f, cy + r * 0.2f, stroke)
        }
        ObjectIcon.POWER -> {
            val path = Path().apply {
                moveTo(cx + r * 0.15f, cy - r)
                lineTo(cx - r * 0.65f, cy + r * 0.08f)
                lineTo(cx + r * 0.02f, cy + r * 0.08f)
                lineTo(cx - r * 0.18f, cy + r)
                lineTo(cx + r * 0.65f, cy - r * 0.08f)
                lineTo(cx - r * 0.02f, cy - r * 0.08f)
                close()
            }
            canvas.drawPath(path, fill)
        }
        ObjectIcon.BOILER -> {
            val path = Path().apply {
                moveTo(cx, cy - r * 0.95f)
                quadTo(cx + r * 0.95f, cy + r * 0.1f, cx, cy + r * 0.95f)
                quadTo(cx - r * 0.95f, cy + r * 0.1f, cx, cy - r * 0.95f)
                close()
            }
            canvas.drawPath(path, stroke)
            canvas.drawCircle(cx, cy + r * 0.28f, r * 0.22f, fill)
        }
        ObjectIcon.WATER -> {
            val path = Path().apply {
                moveTo(cx, cy - r * 0.95f)
                quadTo(cx + r * 0.9f, cy + r * 0.22f, cx, cy + r * 0.92f)
                quadTo(cx - r * 0.9f, cy + r * 0.22f, cx, cy - r * 0.95f)
                close()
            }
            canvas.drawPath(path, fill)
        }
        ObjectIcon.FUEL -> {
            val body = RectF(cx - r * 0.65f, cy - r * 0.65f, cx + r * 0.45f, cy + r * 0.88f)
            canvas.drawRoundRect(body, r * 0.18f, r * 0.18f, stroke)
            canvas.drawLine(cx + r * 0.45f, cy - r * 0.1f, cx + r * 0.88f, cy + r * 0.4f, stroke)
        }
        ObjectIcon.RADAR -> {
            canvas.drawCircle(cx, cy + r * 0.2f, r * 0.22f, fill)
            val arc1 = RectF(cx - r * 0.6f, cy - r * 0.6f, cx + r * 0.6f, cy + r * 0.6f)
            val arc2 = RectF(cx - r * 0.95f, cy - r * 0.95f, cx + r * 0.95f, cy + r * 0.95f)
            canvas.drawArc(arc1, 210f, 120f, false, stroke)
            canvas.drawArc(arc2, 210f, 120f, false, stroke)
        }
        ObjectIcon.RADIATION -> {
            canvas.drawCircle(cx, cy, r * 0.22f, fill)
            for (angle in listOf(-90.0, 30.0, 150.0)) {
                val rad = Math.toRadians(angle)
                val x2 = cx + (r * 0.92f * cos(rad)).toFloat()
                val y2 = cy + (r * 0.92f * sin(rad)).toFloat()
                canvas.drawLine(cx, cy, x2, y2, stroke)
                canvas.drawCircle(x2, y2, r * 0.18f, fill)
            }
        }
        ObjectIcon.HAZARD -> {
            val path = Path().apply {
                moveTo(cx, cy - r * 0.92f)
                lineTo(cx + r * 0.92f, cy + r * 0.85f)
                lineTo(cx - r * 0.92f, cy + r * 0.85f)
                close()
            }
            canvas.drawPath(path, stroke)
            canvas.drawLine(cx, cy - r * 0.22f, cx, cy + r * 0.35f, stroke)
        }
        ObjectIcon.SKULL -> {
            canvas.drawCircle(cx, cy, r * 0.85f, stroke)
            canvas.drawLine(cx - r * 0.42f, cy - r * 0.42f, cx + r * 0.42f, cy + r * 0.42f, stroke)
            canvas.drawLine(cx + r * 0.42f, cy - r * 0.42f, cx - r * 0.42f, cy + r * 0.42f, stroke)
        }
        ObjectIcon.OBSERVATION -> {
            val path = Path().apply {
                moveTo(cx - r, cy)
                quadTo(cx, cy - r * 0.9f, cx + r, cy)
                quadTo(cx, cy + r * 0.9f, cx - r, cy)
                close()
            }
            canvas.drawPath(path, stroke)
            canvas.drawCircle(cx, cy, r * 0.26f, fill)
        }
        ObjectIcon.GATE -> {
            val body = RectF(cx - r * 0.68f, cy - r * 0.12f, cx + r * 0.68f, cy + r * 0.88f)
            canvas.drawRoundRect(body, r * 0.18f, r * 0.18f, stroke)
            val shackle = RectF(cx - r * 0.42f, cy - r * 0.88f, cx + r * 0.42f, cy + r * 0.1f)
            canvas.drawArc(shackle, 180f, 180f, false, stroke)
        }
        ObjectIcon.CRANE -> {
            canvas.drawLine(cx - r * 0.45f, cy + r * 0.9f, cx - r * 0.45f, cy - r * 0.82f, stroke)
            canvas.drawLine(cx - r * 0.82f, cy - r * 0.55f, cx + r * 0.92f, cy - r * 0.55f, stroke)
            canvas.drawLine(cx + r * 0.65f, cy - r * 0.55f, cx + r * 0.65f, cy + r * 0.2f, stroke)
        }
        ObjectIcon.HOSPITAL -> {
            val box = RectF(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.82f)
            canvas.drawRoundRect(box, r * 0.2f, r * 0.2f, stroke)
            canvas.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.45f, stroke)
            canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, stroke)
        }
        ObjectIcon.AIRFIELD -> {
            canvas.drawLine(cx, cy - r * 0.92f, cx, cy + r * 0.92f, stroke)
            canvas.drawLine(cx - r * 0.92f, cy + r * 0.05f, cx + r * 0.92f, cy - r * 0.28f, stroke)
            canvas.drawLine(cx - r * 0.35f, cy + r * 0.72f, cx + r * 0.35f, cy + r * 0.72f, stroke)
        }
        ObjectIcon.PORT -> {
            val hull = Path().apply {
                moveTo(cx - r * 0.92f, cy + r * 0.2f)
                lineTo(cx + r * 0.92f, cy + r * 0.2f)
                lineTo(cx + r * 0.62f, cy + r * 0.85f)
                lineTo(cx - r * 0.62f, cy + r * 0.85f)
                close()
            }
            canvas.drawPath(hull, stroke)
            canvas.drawLine(cx, cy - r * 0.85f, cx, cy + r * 0.2f, stroke)
        }
        ObjectIcon.SHAFT -> {
            val path = Path().apply {
                moveTo(cx - r * 0.95f, cy + r * 0.8f)
                lineTo(cx - r * 0.25f, cy - r * 0.7f)
                lineTo(cx + r * 0.18f, cy + r * 0.1f)
                lineTo(cx + r * 0.48f, cy - r * 0.4f)
                lineTo(cx + r * 0.95f, cy + r * 0.8f)
                close()
            }
            canvas.drawPath(path, stroke)
        }
        ObjectIcon.WORKSHOP -> {
            canvas.drawLine(cx - r * 0.7f, cy + r * 0.7f, cx + r * 0.55f, cy - r * 0.55f, stroke)
            canvas.drawCircle(cx + r * 0.6f, cy - r * 0.6f, r * 0.28f, stroke)
        }
        ObjectIcon.CAMERA -> {
            val body = RectF(cx - r * 0.88f, cy - r * 0.55f, cx + r * 0.32f, cy + r * 0.55f)
            canvas.drawRoundRect(body, r * 0.16f, r * 0.16f, stroke)
            val lens = Path().apply {
                moveTo(cx + r * 0.35f, cy - r * 0.15f)
                lineTo(cx + r * 0.92f, cy - r * 0.48f)
                lineTo(cx + r * 0.92f, cy + r * 0.48f)
                lineTo(cx + r * 0.35f, cy + r * 0.15f)
                close()
            }
            canvas.drawPath(lens, stroke)
        }
        ObjectIcon.PIN -> {
            canvas.drawCircle(cx, cy - r * 0.22f, r * 0.55f, stroke)
            canvas.drawCircle(cx, cy - r * 0.22f, r * 0.2f, fill)
            canvas.drawLine(cx, cy + r * 0.33f, cx, cy + r * 0.92f, stroke)
        }
        ObjectIcon.MILITARY -> {
            canvas.drawLine(cx - r * 0.55f, cy - r * 0.9f, cx - r * 0.55f, cy + r * 0.92f, stroke)
            val flag = Path().apply {
                moveTo(cx - r * 0.55f, cy - r * 0.85f)
                lineTo(cx + r * 0.82f, cy - r * 0.45f)
                lineTo(cx - r * 0.55f, cy - r * 0.02f)
                close()
            }
            canvas.drawPath(flag, stroke)
        }
        ObjectIcon.SILO -> {
            val nose = Path().apply {
                moveTo(cx, cy - r * 0.95f)
                lineTo(cx + r * 0.42f, cy - r * 0.2f)
                lineTo(cx + r * 0.42f, cy + r * 0.65f)
                lineTo(cx - r * 0.42f, cy + r * 0.65f)
                lineTo(cx - r * 0.42f, cy - r * 0.2f)
                close()
            }
            canvas.drawPath(nose, stroke)
            canvas.drawLine(cx - r * 0.72f, cy + r * 0.85f, cx + r * 0.72f, cy + r * 0.85f, stroke)
        }
        ObjectIcon.LAB -> {
            val flask = Path().apply {
                moveTo(cx - r * 0.25f, cy - r * 0.88f)
                lineTo(cx + r * 0.25f, cy - r * 0.88f)
                lineTo(cx + r * 0.22f, cy - r * 0.25f)
                lineTo(cx + r * 0.82f, cy + r * 0.82f)
                lineTo(cx - r * 0.82f, cy + r * 0.82f)
                lineTo(cx - r * 0.22f, cy - r * 0.25f)
                close()
            }
            canvas.drawPath(flask, stroke)
        }
        ObjectIcon.BIOHAZARD -> {
            canvas.drawCircle(cx, cy, r * 0.2f, fill)
            for (angle in listOf(-90.0, 30.0, 150.0)) {
                val rad = Math.toRadians(angle)
                val bx = cx + (r * 0.48f * cos(rad)).toFloat()
                val by = cy + (r * 0.48f * sin(rad)).toFloat()
                canvas.drawCircle(bx, by, r * 0.42f, stroke)
            }
        }
        ObjectIcon.ANTENNA -> {
            canvas.drawCircle(cx, cy, r * 0.2f, fill)
            val inner = RectF(cx - r * 0.56f, cy - r * 0.56f, cx + r * 0.56f, cy + r * 0.56f)
            val outer = RectF(cx - r * 0.95f, cy - r * 0.95f, cx + r * 0.95f, cy + r * 0.95f)
            canvas.drawArc(inner, -45f, 90f, false, stroke)
            canvas.drawArc(inner, 135f, 90f, false, stroke)
            canvas.drawArc(outer, -45f, 90f, false, stroke)
            canvas.drawArc(outer, 135f, 90f, false, stroke)
        }
        ObjectIcon.TRAIN -> {
            val cab = RectF(cx - r * 0.66f, cy - r * 0.85f, cx + r * 0.66f, cy + r * 0.55f)
            canvas.drawRoundRect(cab, r * 0.2f, r * 0.2f, stroke)
            canvas.drawLine(cx - r * 0.45f, cy + r * 0.58f, cx - r * 0.72f, cy + r * 0.92f, stroke)
            canvas.drawLine(cx + r * 0.45f, cy + r * 0.58f, cx + r * 0.72f, cy + r * 0.92f, stroke)
        }
        ObjectIcon.BRIDGE -> {
            val arch = RectF(cx - r * 0.9f, cy - r * 0.2f, cx + r * 0.9f, cy + r * 0.95f)
            canvas.drawLine(cx - r, cy - r * 0.35f, cx + r, cy - r * 0.35f, stroke)
            canvas.drawArc(arch, 180f, 180f, false, stroke)
            canvas.drawLine(cx - r * 0.6f, cy - r * 0.35f, cx - r * 0.6f, cy + r * 0.75f, stroke)
            canvas.drawLine(cx + r * 0.6f, cy - r * 0.35f, cx + r * 0.6f, cy + r * 0.75f, stroke)
        }
        ObjectIcon.DAM -> {
            canvas.drawLine(cx - r * 0.9f, cy - r * 0.42f, cx + r * 0.9f, cy - r * 0.42f, stroke)
            canvas.drawLine(cx - r * 0.9f, cy + r * 0.05f, cx + r * 0.9f, cy + r * 0.05f, stroke)
            canvas.drawLine(cx - r * 0.9f, cy + r * 0.52f, cx + r * 0.9f, cy + r * 0.52f, stroke)
        }
        ObjectIcon.MONUMENT -> {
            val roof = Path().apply {
                moveTo(cx, cy - r * 0.9f)
                lineTo(cx + r * 0.88f, cy - r * 0.32f)
                lineTo(cx - r * 0.88f, cy - r * 0.32f)
                close()
            }
            canvas.drawPath(roof, stroke)
            canvas.drawLine(cx - r * 0.55f, cy - r * 0.3f, cx - r * 0.55f, cy + r * 0.65f, stroke)
            canvas.drawLine(cx, cy - r * 0.3f, cx, cy + r * 0.65f, stroke)
            canvas.drawLine(cx + r * 0.55f, cy - r * 0.3f, cx + r * 0.55f, cy + r * 0.65f, stroke)
            canvas.drawLine(cx - r * 0.9f, cy + r * 0.78f, cx + r * 0.9f, cy + r * 0.78f, stroke)
        }
        ObjectIcon.SCHOOL -> {
            val cap = Path().apply {
                moveTo(cx, cy - r * 0.75f)
                lineTo(cx + r * 0.95f, cy - r * 0.15f)
                lineTo(cx, cy + r * 0.45f)
                lineTo(cx - r * 0.95f, cy - r * 0.15f)
                close()
            }
            canvas.drawPath(cap, stroke)
            canvas.drawLine(cx - r * 0.52f, cy + r * 0.25f, cx - r * 0.52f, cy + r * 0.75f, stroke)
            canvas.drawLine(cx + r * 0.52f, cy + r * 0.25f, cx + r * 0.52f, cy + r * 0.75f, stroke)
            canvas.drawLine(cx - r * 0.52f, cy + r * 0.75f, cx + r * 0.52f, cy + r * 0.75f, stroke)
        }
        ObjectIcon.HOTEL -> {
            canvas.drawLine(cx - r * 0.9f, cy - r * 0.55f, cx - r * 0.9f, cy + r * 0.68f, stroke)
            canvas.drawLine(cx - r * 0.9f, cy + r * 0.28f, cx + r * 0.9f, cy + r * 0.28f, stroke)
            canvas.drawLine(cx + r * 0.9f, cy - r * 0.15f, cx + r * 0.9f, cy + r * 0.68f, stroke)
            canvas.drawCircle(cx - r * 0.42f, cy - r * 0.12f, r * 0.22f, stroke)
        }
        ObjectIcon.RUINS -> {
            val box = RectF(cx - r * 0.85f, cy - r * 0.65f, cx + r * 0.85f, cy + r * 0.75f)
            canvas.drawRect(box, stroke)
            canvas.drawLine(cx - r * 0.85f, cy + r * 0.05f, cx + r * 0.85f, cy + r * 0.05f, stroke)
            canvas.drawLine(cx, cy - r * 0.65f, cx, cy + r * 0.05f, stroke)
            canvas.drawLine(cx - r * 0.35f, cy + r * 0.05f, cx - r * 0.35f, cy + r * 0.75f, stroke)
            canvas.drawLine(cx + r * 0.35f, cy + r * 0.05f, cx + r * 0.35f, cy + r * 0.75f, stroke)
        }
        ObjectIcon.SHELTER -> {
            val shield = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.85f, cy - r * 0.5f)
                lineTo(cx + r * 0.85f, cy + r * 0.15f)
                quadTo(cx + r * 0.5f, cy + r * 0.8f, cx, cy + r)
                quadTo(cx - r * 0.5f, cy + r * 0.8f, cx - r * 0.85f, cy + r * 0.15f)
                lineTo(cx - r * 0.85f, cy - r * 0.5f)
                close()
            }
            canvas.drawPath(shield, stroke)
            canvas.drawLine(cx, cy - r * 0.35f, cx, cy + r * 0.25f, stroke)
            canvas.drawCircle(cx, cy + r * 0.52f, r * 0.1f, fill)
        }
        ObjectIcon.FIRE -> {
            canvas.drawCircle(cx, cy, r * 0.22f, fill)
            for (angle in listOf(-90.0, -30.0, 30.0, 90.0, 150.0, 210.0)) {
                val rad = Math.toRadians(angle)
                val x2 = cx + (r * 0.85f * cos(rad)).toFloat()
                val y2 = cy + (r * 0.85f * sin(rad)).toFloat()
                canvas.drawLine(cx, cy, x2, y2, stroke)
            }
        }
        ObjectIcon.COMPASS -> {
            canvas.drawCircle(cx, cy, r * 0.88f, stroke)
            val needle = Path().apply {
                moveTo(cx + r * 0.45f, cy - r * 0.45f)
                lineTo(cx + r * 0.12f, cy + r * 0.12f)
                lineTo(cx - r * 0.45f, cy + r * 0.45f)
                lineTo(cx - r * 0.12f, cy - r * 0.12f)
                close()
            }
            canvas.drawPath(needle, stroke)
        }
        ObjectIcon.STASH -> {
            val cube = RectF(cx - r * 0.78f, cy - r * 0.68f, cx + r * 0.78f, cy + r * 0.78f)
            canvas.drawRoundRect(cube, r * 0.14f, r * 0.14f, stroke)
            canvas.drawLine(cx - r * 0.78f, cy - r * 0.18f, cx + r * 0.78f, cy - r * 0.18f, stroke)
            canvas.drawLine(cx, cy - r * 0.68f, cx, cy + r * 0.78f, stroke)
        }
        ObjectIcon.SUBSTATION -> {
            val chip = RectF(cx - r * 0.56f, cy - r * 0.56f, cx + r * 0.56f, cy + r * 0.56f)
            canvas.drawRoundRect(chip, r * 0.14f, r * 0.14f, stroke)
            canvas.drawLine(cx - r * 0.9f, cy - r * 0.25f, cx - r * 0.56f, cy - r * 0.25f, stroke)
            canvas.drawLine(cx - r * 0.9f, cy + r * 0.25f, cx - r * 0.56f, cy + r * 0.25f, stroke)
            canvas.drawLine(cx + r * 0.56f, cy - r * 0.25f, cx + r * 0.9f, cy - r * 0.25f, stroke)
            canvas.drawLine(cx + r * 0.56f, cy + r * 0.25f, cx + r * 0.9f, cy + r * 0.25f, stroke)
        }
        ObjectIcon.COOLING -> {
            val path = Path().apply {
                moveTo(cx - r * 0.48f, cy - r * 0.85f)
                lineTo(cx + r * 0.48f, cy - r * 0.85f)
                quadTo(cx + r * 0.42f, cy, cx + r * 0.85f, cy + r * 0.88f)
                lineTo(cx - r * 0.85f, cy + r * 0.88f)
                quadTo(cx - r * 0.42f, cy, cx - r * 0.48f, cy - r * 0.85f)
                close()
            }
            canvas.drawPath(path, stroke)
        }
        ObjectIcon.CISTERN -> {
            val topOval = RectF(cx - r * 0.72f, cy - r * 0.85f, cx + r * 0.72f, cy - r * 0.35f)
            canvas.drawOval(topOval, stroke)
            canvas.drawLine(cx - r * 0.72f, cy - r * 0.6f, cx - r * 0.72f, cy + r * 0.6f, stroke)
            canvas.drawLine(cx + r * 0.72f, cy - r * 0.6f, cx + r * 0.72f, cy + r * 0.6f, stroke)
            val botArc = RectF(cx - r * 0.72f, cy + r * 0.35f, cx + r * 0.72f, cy + r * 0.85f)
            canvas.drawArc(botArc, 0f, 180f, false, stroke)
        }
        ObjectIcon.ELEVATOR -> {
            val leftTower = RectF(cx - r * 0.78f, cy - r * 0.82f, cx - r * 0.08f, cy + r * 0.86f)
            val rightTower = RectF(cx + r * 0.08f, cy - r * 0.62f, cx + r * 0.78f, cy + r * 0.86f)
            canvas.drawRect(leftTower, stroke)
            canvas.drawRect(rightTower, stroke)
        }
        ObjectIcon.HANGAR -> {
            val arch = RectF(cx - r * 0.92f, cy - r * 0.68f, cx + r * 0.92f, cy + r * 1.1f)
            canvas.drawArc(arch, 180f, 180f, false, stroke)
            canvas.drawLine(cx - r * 0.92f, cy + r * 0.82f, cx + r * 0.92f, cy + r * 0.82f, stroke)
        }
        ObjectIcon.CHECKPOINT -> {
            canvas.drawLine(cx - r * 0.82f, cy - r * 0.6f, cx - r * 0.82f, cy + r * 0.85f, stroke)
            canvas.drawLine(cx - r * 0.82f, cy + r * 0.1f, cx + r * 0.88f, cy - r * 0.45f, stroke)
            canvas.drawCircle(cx + r * 0.5f, cy + r * 0.55f, r * 0.22f, fill)
        }
        ObjectIcon.BARRACKS -> {
            canvas.drawCircle(cx - r * 0.35f, cy - r * 0.35f, r * 0.25f, stroke)
            canvas.drawCircle(cx + r * 0.35f, cy - r * 0.35f, r * 0.25f, stroke)
            canvas.drawLine(cx - r * 0.82f, cy + r * 0.65f, cx + r * 0.82f, cy + r * 0.65f, stroke)
        }
        ObjectIcon.RANGE -> {
            canvas.drawCircle(cx, cy, r * 0.68f, stroke)
            canvas.drawCircle(cx, cy, r * 0.18f, fill)
            canvas.drawLine(cx, cy - r * 0.95f, cx, cy - r * 0.42f, stroke)
            canvas.drawLine(cx, cy + r * 0.42f, cx, cy + r * 0.95f, stroke)
            canvas.drawLine(cx - r * 0.95f, cy, cx - r * 0.42f, cy, stroke)
            canvas.drawLine(cx + r * 0.42f, cy, cx + r * 0.95f, cy, stroke)
        }
        ObjectIcon.SEARCHLIGHT -> {
            canvas.drawCircle(cx, cy, r * 0.4f, fill)
            for (angle in listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)) {
                val rad = Math.toRadians(angle)
                val x1 = cx + (r * 0.56f * cos(rad)).toFloat()
                val y1 = cy + (r * 0.56f * sin(rad)).toFloat()
                val x2 = cx + (r * 0.9f * cos(rad)).toFloat()
                val y2 = cy + (r * 0.9f * sin(rad)).toFloat()
                canvas.drawLine(x1, y1, x2, y2, stroke)
            }
        }
        ObjectIcon.MUNITION -> {
            canvas.drawCircle(cx - r * 0.1f, cy + r * 0.15f, r * 0.62f, stroke)
            canvas.drawLine(cx + r * 0.3f, cy - r * 0.3f, cx + r * 0.78f, cy - r * 0.78f, stroke)
            canvas.drawCircle(cx + r * 0.82f, cy - r * 0.82f, r * 0.14f, fill)
        }
        ObjectIcon.METRO -> {
            val path = Path().apply {
                moveTo(cx - r * 0.78f, cy + r * 0.82f)
                lineTo(cx - r * 0.48f, cy - r * 0.78f)
                lineTo(cx, cy + r * 0.18f)
                lineTo(cx + r * 0.48f, cy - r * 0.78f)
                lineTo(cx + r * 0.78f, cy + r * 0.82f)
            }
            canvas.drawPath(path, stroke)
        }
        ObjectIcon.VENT -> {
            canvas.drawCircle(cx, cy, r * 0.86f, stroke)
            canvas.drawCircle(cx, cy, r * 0.18f, fill)
            for (angle in listOf(-90.0, 30.0, 150.0)) {
                val rad = Math.toRadians(angle)
                canvas.drawLine(cx, cy, cx + (r * 0.72f * cos(rad)).toFloat(), cy + (r * 0.72f * sin(rad)).toFloat(), stroke)
            }
        }
        ObjectIcon.DRAINAGE -> {
            canvas.drawCircle(cx, cy, r * 0.78f, stroke)
            canvas.drawLine(cx - r * 0.78f, cy, cx + r * 0.78f, cy, stroke)
            canvas.drawLine(cx - r * 0.48f, cy + r * 0.42f, cx + r * 0.48f, cy + r * 0.42f, stroke)
        }
        ObjectIcon.CATACOMB -> {
            canvas.drawCircle(cx, cy, r * 0.22f, fill)
            canvas.drawCircle(cx - r * 0.68f, cy - r * 0.55f, r * 0.18f, fill)
            canvas.drawCircle(cx + r * 0.68f, cy - r * 0.55f, r * 0.18f, fill)
            canvas.drawCircle(cx, cy + r * 0.72f, r * 0.18f, fill)
            canvas.drawLine(cx, cy, cx - r * 0.68f, cy - r * 0.55f, stroke)
            canvas.drawLine(cx, cy, cx + r * 0.68f, cy - r * 0.55f, stroke)
            canvas.drawLine(cx, cy, cx, cy + r * 0.72f, stroke)
        }
        ObjectIcon.VAULT -> {
            canvas.drawCircle(cx - r * 0.42f, cy, r * 0.38f, stroke)
            canvas.drawLine(cx - r * 0.04f, cy, cx + r * 0.88f, cy, stroke)
            canvas.drawLine(cx + r * 0.55f, cy, cx + r * 0.55f, cy + r * 0.42f, stroke)
            canvas.drawLine(cx + r * 0.85f, cy, cx + r * 0.85f, cy + r * 0.42f, stroke)
        }
        ObjectIcon.DEPOT -> {
            canvas.drawLine(cx - r * 0.5f, cy - r * 0.9f, cx - r * 0.5f, cy + r * 0.9f, stroke)
            canvas.drawLine(cx + r * 0.5f, cy - r * 0.9f, cx + r * 0.5f, cy + r * 0.9f, stroke)
            canvas.drawLine(cx - r * 0.75f, cy - r * 0.45f, cx + r * 0.75f, cy - r * 0.45f, stroke)
            canvas.drawLine(cx - r * 0.75f, cy, cx + r * 0.75f, cy, stroke)
            canvas.drawLine(cx - r * 0.75f, cy + r * 0.45f, cx + r * 0.75f, cy + r * 0.45f, stroke)
        }
        ObjectIcon.BRIDGE_RAIL -> {
            canvas.drawLine(cx - r * 0.92f, cy - r * 0.48f, cx + r * 0.92f, cy - r * 0.48f, stroke)
            canvas.drawLine(cx - r * 0.6f, cy - r * 0.48f, cx - r * 0.6f, cy + r * 0.85f, stroke)
            canvas.drawLine(cx, cy - r * 0.48f, cx, cy + r * 0.85f, stroke)
            canvas.drawLine(cx + r * 0.6f, cy - r * 0.48f, cx + r * 0.6f, cy + r * 0.85f, stroke)
        }
        ObjectIcon.PIER -> {
            canvas.drawLine(cx, cy - r * 0.82f, cx, cy + r * 0.78f, stroke)
            canvas.drawLine(cx - r * 0.48f, cy - r * 0.28f, cx + r * 0.48f, cy - r * 0.28f, stroke)
            val arc = RectF(cx - r * 0.72f, cy - r * 0.05f, cx + r * 0.72f, cy + r * 0.82f)
            canvas.drawArc(arc, 0f, 180f, false, stroke)
        }
        ObjectIcon.LIGHTHOUSE -> {
            val body = Path().apply {
                moveTo(cx - r * 0.3f, cy - r * 0.6f)
                lineTo(cx + r * 0.3f, cy - r * 0.6f)
                lineTo(cx + r * 0.58f, cy + r * 0.88f)
                lineTo(cx - r * 0.58f, cy + r * 0.88f)
                close()
            }
            canvas.drawPath(body, stroke)
            canvas.drawCircle(cx, cy - r * 0.75f, r * 0.2f, fill)
        }
        ObjectIcon.QUARRY -> {
            canvas.drawLine(cx - r * 0.92f, cy - r * 0.35f, cx - r * 0.35f, cy - r * 0.35f, stroke)
            canvas.drawLine(cx - r * 0.35f, cy - r * 0.35f, cx - r * 0.15f, cy + r * 0.2f, stroke)
            canvas.drawLine(cx - r * 0.15f, cy + r * 0.2f, cx + r * 0.3f, cy + r * 0.65f, stroke)
            canvas.drawLine(cx + r * 0.3f, cy + r * 0.65f, cx + r * 0.92f, cy + r * 0.65f, stroke)
        }
        ObjectIcon.MINE_CART -> {
            canvas.drawLine(cx - r * 0.7f, cy + r * 0.7f, cx + r * 0.4f, cy - r * 0.4f, stroke)
            val head = RectF(cx - r * 0.2f, cy - r * 0.85f, cx + r * 0.8f, cy - r * 0.05f)
            canvas.drawArc(head, 200f, 140f, false, stroke)
        }
        ObjectIcon.OIL_RIG -> {
            canvas.drawLine(cx - r * 0.55f, cy + r * 0.88f, cx, cy - r * 0.88f, stroke)
            canvas.drawLine(cx + r * 0.55f, cy + r * 0.88f, cx, cy - r * 0.88f, stroke)
            canvas.drawLine(cx - r * 0.88f, cy - r * 0.35f, cx + r * 0.88f, cy - r * 0.75f, stroke)
        }
        ObjectIcon.PIPELINE -> {
            canvas.drawLine(cx - r * 0.95f, cy - r * 0.25f, cx + r * 0.95f, cy - r * 0.25f, stroke)
            canvas.drawLine(cx - r * 0.95f, cy + r * 0.25f, cx + r * 0.95f, cy + r * 0.25f, stroke)
            canvas.drawLine(cx - r * 0.4f, cy - r * 0.55f, cx - r * 0.4f, cy + r * 0.55f, stroke)
            canvas.drawLine(cx + r * 0.4f, cy - r * 0.55f, cx + r * 0.4f, cy + r * 0.55f, stroke)
        }
        ObjectIcon.MORGUE -> {
            canvas.drawLine(cx, cy - r * 0.82f, cx, cy + r * 0.82f, stroke)
            canvas.drawLine(cx - r * 0.82f, cy, cx + r * 0.82f, cy, stroke)
        }
        ObjectIcon.SANATORIUM -> {
            canvas.drawCircle(cx - r * 0.32f, cy - r * 0.25f, r * 0.46f, stroke)
            canvas.drawCircle(cx + r * 0.35f, cy - r * 0.1f, r * 0.38f, stroke)
            canvas.drawLine(cx - r * 0.32f, cy + r * 0.22f, cx - r * 0.32f, cy + r * 0.88f, stroke)
            canvas.drawLine(cx + r * 0.35f, cy + r * 0.28f, cx + r * 0.35f, cy + r * 0.88f, stroke)
        }
        ObjectIcon.CHURCH -> {
            canvas.drawLine(cx, cy - r * 0.95f, cx, cy - r * 0.45f, stroke)
            canvas.drawLine(cx - r * 0.22f, cy - r * 0.74f, cx + r * 0.22f, cy - r * 0.74f, stroke)
            val body = RectF(cx - r * 0.56f, cy - r * 0.2f, cx + r * 0.56f, cy + r * 0.86f)
            canvas.drawRect(body, stroke)
        }
        ObjectIcon.CASTLE -> {
            val path = Path().apply {
                moveTo(cx - r * 0.85f, cy + r * 0.86f)
                lineTo(cx - r * 0.85f, cy - r * 0.65f)
                lineTo(cx - r * 0.45f, cy - r * 0.65f)
                lineTo(cx - r * 0.45f, cy - r * 0.28f)
                lineTo(cx + r * 0.45f, cy - r * 0.28f)
                lineTo(cx + r * 0.45f, cy - r * 0.65f)
                lineTo(cx + r * 0.85f, cy - r * 0.65f)
                lineTo(cx + r * 0.85f, cy + r * 0.86f)
                close()
            }
            canvas.drawPath(path, stroke)
        }
        ObjectIcon.THEATER -> {
            val mask = RectF(cx - r * 0.75f, cy - r * 0.72f, cx + r * 0.75f, cy + r * 0.72f)
            canvas.drawRoundRect(mask, r * 0.3f, r * 0.3f, stroke)
            canvas.drawCircle(cx - r * 0.28f, cy - r * 0.15f, r * 0.1f, fill)
            canvas.drawCircle(cx + r * 0.28f, cy - r * 0.15f, r * 0.1f, fill)
            val smile = RectF(cx - r * 0.35f, cy - r * 0.05f, cx + r * 0.35f, cy + r * 0.42f)
            canvas.drawArc(smile, 0f, 180f, false, stroke)
        }
        ObjectIcon.STADIUM -> {
            val ring = RectF(cx - r * 0.92f, cy - r * 0.56f, cx + r * 0.92f, cy + r * 0.56f)
            canvas.drawOval(ring, stroke)
            val inner = RectF(cx - r * 0.48f, cy - r * 0.24f, cx + r * 0.48f, cy + r * 0.24f)
            canvas.drawOval(inner, stroke)
        }
        ObjectIcon.MALL -> {
            val shop = RectF(cx - r * 0.78f, cy - r * 0.25f, cx + r * 0.78f, cy + r * 0.82f)
            canvas.drawRect(shop, stroke)
            canvas.drawLine(cx - r * 0.92f, cy - r * 0.55f, cx + r * 0.92f, cy - r * 0.55f, stroke)
        }
        ObjectIcon.GENERATOR -> {
            val cell = RectF(cx - r * 0.55f, cy - r * 0.65f, cx + r * 0.55f, cy + r * 0.85f)
            canvas.drawRoundRect(cell, r * 0.14f, r * 0.14f, stroke)
            canvas.drawLine(cx - r * 0.22f, cy - r * 0.85f, cx + r * 0.22f, cy - r * 0.85f, stroke)
            canvas.drawLine(cx + r * 0.12f, cy - r * 0.3f, cx - r * 0.15f, cy + r * 0.1f, stroke)
            canvas.drawLine(cx - r * 0.15f, cy + r * 0.1f, cx + r * 0.15f, cy + r * 0.45f, stroke)
        }
        ObjectIcon.EVAC -> {
            val door = RectF(cx - r * 0.62f, cy - r * 0.82f, cx + r * 0.35f, cy + r * 0.82f)
            canvas.drawRect(door, stroke)
            canvas.drawLine(cx + r * 0.08f, cy, cx + r * 0.92f, cy, stroke)
            canvas.drawLine(cx + r * 0.62f, cy - r * 0.28f, cx + r * 0.92f, cy, stroke)
            canvas.drawLine(cx + r * 0.62f, cy + r * 0.28f, cx + r * 0.92f, cy, stroke)
        }
    }
}

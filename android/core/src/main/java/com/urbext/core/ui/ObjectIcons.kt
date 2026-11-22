package com.urbext.core.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
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
    }
}

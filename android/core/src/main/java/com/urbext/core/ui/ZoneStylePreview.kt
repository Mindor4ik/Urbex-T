package com.urbext.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.urbext.domain.model.ZonePattern
import com.urbext.domain.model.ZoneStyle

@Composable
fun ZoneLinePreviewGlyph(
    style: ZoneStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 24.dp, height = 10.dp)) {
        val cy = size.height / 2f
        val pad = 1.dp.toPx()
        val start = Offset(pad, cy)
        val end = Offset(size.width - pad, cy)
        when (style) {
            ZoneStyle.DOUBLE_LINE -> {
                val offset = 2.dp.toPx()
                val sw = 1.5.dp.toPx()
                drawLine(color, Offset(pad, cy - offset), Offset(size.width - pad, cy - offset), strokeWidth = sw)
                drawLine(color, Offset(pad, cy + offset), Offset(size.width - pad, cy + offset), strokeWidth = sw)
            }
            ZoneStyle.GLOW -> {
                drawLine(
                    color = color.copy(alpha = 0.35f),
                    start = start,
                    end = end,
                    strokeWidth = 5.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            ZoneStyle.OUTLINE, ZoneStyle.DASHED_OUTLINE -> {
                val effect = if (style == ZoneStyle.DASHED_OUTLINE) {
                    PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.5.dp.toPx()), 0f)
                } else {
                    null
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(pad, 1.5.dp.toPx()),
                    size = Size(size.width - pad * 2f, size.height - 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx(), pathEffect = effect)
                )
            }
            ZoneStyle.DASHED -> {
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = 1.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()), 0f)
                )
            }
            ZoneStyle.DOTTED -> {
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2.dp.toPx(), 3.dp.toPx()), 0f)
                )
            }
            ZoneStyle.DASH_DOT -> {
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 2.4.dp.toPx(), 1.4.dp.toPx(), 2.4.dp.toPx()),
                        0f
                    )
                )
            }
            ZoneStyle.LONG_DASH -> {
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = 1.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.5.dp.toPx(), 3.2.dp.toPx()), 0f)
                )
            }
            ZoneStyle.BOLD -> {
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = 3.dp.toPx()
                )
            }
            ZoneStyle.SOLID -> {
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = 1.8.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun ZonePatternPreviewGlyph(
    pattern: ZonePattern,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF0A0D0C))
            .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(3.dp))
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            drawRect(color.copy(alpha = if (pattern == ZonePattern.NONE) 0.34f else 0.16f))
            val stroke = color.copy(alpha = 0.90f)
            when (pattern) {
                ZonePattern.NONE -> Unit
                ZonePattern.HATCH -> {
                    val sw = 1.3.dp.toPx()
                    drawLine(stroke, Offset(-w * 0.1f, h * 0.5f), Offset(w * 0.5f, -h * 0.1f), sw)
                    drawLine(stroke, Offset(0f, h), Offset(w, 0f), sw)
                    drawLine(stroke, Offset(w * 0.5f, h * 1.1f), Offset(w * 1.1f, h * 0.5f), sw)
                }
                ZonePattern.CROSS_HATCH -> {
                    val sw = 1.1.dp.toPx()
                    drawLine(stroke, Offset(-w * 0.1f, h * 0.5f), Offset(w * 0.5f, -h * 0.1f), sw)
                    drawLine(stroke, Offset(0f, h), Offset(w, 0f), sw)
                    drawLine(stroke, Offset(w * 0.5f, h * 1.1f), Offset(w * 1.1f, h * 0.5f), sw)
                    drawLine(stroke, Offset(-w * 0.1f, h * 0.5f), Offset(w * 0.5f, h * 1.1f), sw)
                    drawLine(stroke, Offset(0f, 0f), Offset(w, h), sw)
                    drawLine(stroke, Offset(w * 0.5f, -h * 0.1f), Offset(w * 1.1f, h * 0.5f), sw)
                }
                ZonePattern.GRID -> {
                    val sw = 1.1.dp.toPx()
                    drawLine(stroke, Offset(w * 0.33f, 0f), Offset(w * 0.33f, h), sw)
                    drawLine(stroke, Offset(w * 0.67f, 0f), Offset(w * 0.67f, h), sw)
                    drawLine(stroke, Offset(0f, h * 0.33f), Offset(w, h * 0.33f), sw)
                    drawLine(stroke, Offset(0f, h * 0.67f), Offset(w, h * 0.67f), sw)
                }
                ZonePattern.DOTS -> {
                    val r = 1.3.dp.toPx()
                    drawCircle(stroke, r, Offset(w * 0.25f, h * 0.25f))
                    drawCircle(stroke, r, Offset(w * 0.75f, h * 0.25f))
                    drawCircle(stroke, r, Offset(w * 0.5f, h * 0.5f))
                    drawCircle(stroke, r, Offset(w * 0.25f, h * 0.75f))
                    drawCircle(stroke, r, Offset(w * 0.75f, h * 0.75f))
                }
                ZonePattern.VERTICAL -> {
                    val sw = 1.3.dp.toPx()
                    drawLine(stroke, Offset(w * 0.22f, 0f), Offset(w * 0.22f, h), sw)
                    drawLine(stroke, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), sw)
                    drawLine(stroke, Offset(w * 0.78f, 0f), Offset(w * 0.78f, h), sw)
                }
                ZonePattern.HORIZONTAL -> {
                    val sw = 1.3.dp.toPx()
                    drawLine(stroke, Offset(0f, h * 0.22f), Offset(w, h * 0.22f), sw)
                    drawLine(stroke, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), sw)
                    drawLine(stroke, Offset(0f, h * 0.78f), Offset(w, h * 0.78f), sw)
                }
                ZonePattern.HAZARD_STRIPES -> {
                    val sw = 3.dp.toPx()
                    drawLine(color.copy(alpha = 0.85f), Offset(-w * 0.1f, h * 0.6f), Offset(w * 0.6f, -h * 0.1f), sw)
                    drawLine(color.copy(alpha = 0.85f), Offset(w * 0.25f, h * 1.1f), Offset(w * 1.1f, h * 0.25f), sw)
                }
            }
        }
    }
}

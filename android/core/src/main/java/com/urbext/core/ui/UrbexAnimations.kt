package com.urbext.core.ui

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders a soft tactical outer glow (matching Web CSS box-shadow bloom) without clipping.
 * Place before .clip() / .background() so the halo extends cleanly around the element.
 */
fun Modifier.urbexGlow(
    color: Color,
    radius: Dp = 14.dp,
    cornerRadius: Dp = 12.dp,
    alpha: Float = 0.28f,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled || alpha <= 0f || radius <= 0.dp) return@composed this
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    val cornerPx = with(density) { cornerRadius.toPx() }
    val fwPaint = remember(radiusPx) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && radiusPx > 0.5f) {
                maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
            }
        }
    }
    this.drawBehind {
        if (radiusPx <= 0.5f || size.width <= 0f || size.height <= 0f) return@drawBehind
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            fwPaint.color = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRoundRect(
                    0f,
                    0f,
                    size.width,
                    size.height,
                    cornerPx,
                    cornerPx,
                    fwPaint
                )
            }
        } else {
            val steps = 3
            for (i in 1..steps) {
                val spread = radiusPx * (i.toFloat() / steps) * 0.55f
                val stepAlpha = (alpha * 0.42f) / i
                drawRoundRect(
                    color = color.copy(alpha = stepAlpha.coerceIn(0f, 1f)),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + spread * 2f, size.height + spread * 2f),
                    cornerRadius = CornerRadius(cornerPx + spread, cornerPx + spread)
                )
            }
        }
    }
}

fun Modifier.urbexCardGlow(
    color: Color = UrbexColors.Green,
    cornerRadius: Dp = 12.dp,
    alpha: Float = 0.10f,
    radius: Dp = 16.dp,
    enabled: Boolean = true
): Modifier = urbexGlow(
    color = color,
    radius = radius,
    cornerRadius = cornerRadius,
    alpha = alpha,
    enabled = enabled
)

fun Modifier.urbexBounceClick(
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "urbexPressScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

fun Modifier.urbexScaleOnPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "urbexButtonPressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}


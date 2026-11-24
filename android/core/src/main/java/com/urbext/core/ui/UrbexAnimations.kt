package com.urbext.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private class CachedGlowLayer(
    val color: Color,
    val topLeft: Offset,
    val size: Size,
    val cornerRadius: CornerRadius
)

// 7-band GPU-cached Gaussian-falloff spreads & weights calibrated against CSS `box-shadow: 0 0 Rpx`
// Avoids 8-bit ARGB rounding-to-zero on dark #0C0F0E OLED backgrounds while keeping zero BlurMaskFilter overhead.
private val GLOW_SPREAD_FRACTIONS = floatArrayOf(1.00f, 0.80f, 0.62f, 0.46f, 0.32f, 0.20f, 0.10f)
private val GLOW_LAYER_WEIGHTS = floatArrayOf(0.055f, 0.080f, 0.105f, 0.135f, 0.170f, 0.210f, 0.250f)

/**
 * Zero-lag GPU-cached outer bloom (1:1 visual parity with Web CSS `box-shadow: 0 0 Rpx rgba(...)`).
 * Uses `drawWithCache` + `ClipOp.Difference` multi-band rounded rects with zero native blur passes,
 * zero Modifier.composed overhead, and zero per-frame allocations.
 * Place before .clip() / .background() so the halo renders cleanly outside the element bounds.
 */
fun Modifier.urbexGlow(
    color: Color,
    radius: Dp = 16.dp,
    cornerRadius: Dp = 12.dp,
    alpha: Float = 0.30f,
    enabled: Boolean = true
): Modifier {
    if (!enabled || alpha <= 0f || radius <= 0.dp) return this
    val clampedAlpha = alpha.coerceIn(0f, 1f)
    return this.drawWithCache {
        val radiusPx = radius.toPx()
        val cornerPx = cornerRadius.toPx()
        if (radiusPx <= 0.5f || size.width <= 0f || size.height <= 0f) {
            onDrawBehind {}
        } else {
            val innerPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        radiusX = cornerPx,
                        radiusY = cornerPx
                    )
                )
            }
            val layers = Array(GLOW_SPREAD_FRACTIONS.size) { idx ->
                val spread = radiusPx * GLOW_SPREAD_FRACTIONS[idx]
                val bandAlpha = (clampedAlpha * GLOW_LAYER_WEIGHTS[idx]).coerceIn(0f, 1f)
                val r = cornerPx + spread * 0.85f
                CachedGlowLayer(
                    color = color.copy(alpha = bandAlpha),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + spread * 2f, size.height + spread * 2f),
                    cornerRadius = CornerRadius(r, r)
                )
            }
            onDrawBehind {
                clipPath(innerPath, ClipOp.Difference) {
                    for (i in layers.indices) {
                        val layer = layers[i]
                        drawRoundRect(
                            color = layer.color,
                            topLeft = layer.topLeft,
                            size = layer.size,
                            cornerRadius = layer.cornerRadius
                        )
                    }
                }
            }
        }
    }
}

fun Modifier.urbexCardGlow(
    color: Color = UrbexColors.Green,
    cornerRadius: Dp = 12.dp,
    alpha: Float = 0.17f,
    radius: Dp = 18.dp,
    enabled: Boolean = true
): Modifier = urbexGlow(
    color = color,
    radius = radius,
    cornerRadius = cornerRadius,
    alpha = alpha,
    enabled = enabled
)

/**
 * Tactile filled CTA button without Material3's hidden 48.dp minimumInteractiveComponentSize wrapper,
 * ensuring the outer glow matches the visible button dimensions and corner radius 1:1.
 */
@Composable
fun UrbexFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    cornerRadius: Dp = 9.dp,
    containerColor: Color = UrbexColors.Green,
    disabledContainerColor: Color = UrbexColors.Elevated,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    glowColor: Color? = UrbexColors.GreenBright,
    glowRadius: Dp = 14.dp,
    glowAlpha: Float = 0.32f,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(cornerRadius)
    val effectiveBg = if (enabled) containerColor else disabledContainerColor
    Row(
        modifier = modifier
            .height(height)
            .urbexScaleOnPress(interactionSource, 0.96f)
            .then(
                if (enabled && glowColor != null && glowAlpha > 0f && glowRadius > 0.dp) {
                    Modifier.urbexGlow(
                        color = glowColor,
                        radius = glowRadius,
                        cornerRadius = cornerRadius,
                        alpha = glowAlpha,
                        enabled = true
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(effectiveBg, shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Tactile outlined button with optional colored-border glow matching its exact dimensions and corner radius 1:1.
 * Leaves dark neutral outlines unlit unless `glowColor` is explicitly supplied.
 */
@Composable
fun UrbexOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    cornerRadius: Dp = 9.dp,
    containerColor: Color = Color.Transparent,
    borderColor: Color = UrbexColors.BorderStrong,
    borderWidth: Dp = 1.dp,
    enabled: Boolean = true,
    glowColor: Color? = null,
    glowRadius: Dp = 10.dp,
    glowAlpha: Float = 0.24f,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(cornerRadius)
    val activeBorder = if (enabled) borderColor else borderColor.copy(alpha = 0.45f)
    Row(
        modifier = modifier
            .height(height)
            .urbexScaleOnPress(interactionSource, 0.96f)
            .then(
                if (enabled && glowColor != null && glowAlpha > 0f && glowRadius > 0.dp) {
                    Modifier.urbexGlow(
                        color = glowColor,
                        radius = glowRadius,
                        cornerRadius = cornerRadius,
                        alpha = glowAlpha,
                        enabled = true
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(containerColor, shape)
            .border(borderWidth, activeBorder, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

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

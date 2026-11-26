package com.urbext.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private class CachedGlowLayer(
    val color: Color,
    val topLeft: Offset,
    val size: Size,
    val cornerRadius: CornerRadius,
    val strokeStyle: Stroke
)

// 7-band GPU-cached Gaussian-falloff outward Stroke rings calibrated against CSS `box-shadow: 0 0 Rpx`.
// Parallel-offset Stroke rings never bleed inside transparent outlined buttons, allocate zero Path objects,
// avoid Skia ClipOp.Difference stencil clipping, and paint each pixel ~1x instead of 7x overdraw.
private val GLOW_STOP_FRACTIONS = floatArrayOf(0f, 0.10f, 0.22f, 0.36f, 0.52f, 0.68f, 0.84f, 1.00f)
private val GLOW_LAYER_WEIGHTS = floatArrayOf(0.82f, 0.56f, 0.36f, 0.22f, 0.12f, 0.06f, 0.025f)

/**
 * Zero-lag GPU-cached outer bloom (1:1 visual parity with Web CSS `box-shadow: 0 0 Rpx rgba(...)`).
 * Uses `drawWithCache` + parallel-offset `Stroke` rings strictly outside [0, w] x [0, h] with zero
 * native blur passes, zero Path/stencil clipping, and zero per-frame allocations.
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
            val layers = Array(GLOW_LAYER_WEIGHTS.size) { idx ->
                val innerDist = radiusPx * GLOW_STOP_FRACTIONS[idx]
                val overlapPx = if (idx < GLOW_LAYER_WEIGHTS.lastIndex) 0.45f else 0f
                val outerDist = maxOf(radiusPx * GLOW_STOP_FRACTIONS[idx + 1] + overlapPx, innerDist + 1f)
                val strokeW = outerDist - innerDist
                val midDist = (innerDist + outerDist) * 0.5f
                val bandAlpha = (clampedAlpha * GLOW_LAYER_WEIGHTS[idx]).coerceIn(0f, 1f)
                val r = (cornerPx + midDist).coerceAtLeast(0f)
                CachedGlowLayer(
                    color = color.copy(alpha = bandAlpha),
                    topLeft = Offset(-midDist, -midDist),
                    size = Size(size.width + midDist * 2f, size.height + midDist * 2f),
                    cornerRadius = CornerRadius(r, r),
                    strokeStyle = Stroke(width = strokeW)
                )
            }
            onDrawBehind {
                for (i in layers.indices) {
                    val layer = layers[i]
                    drawRoundRect(
                        color = layer.color,
                        topLeft = layer.topLeft,
                        size = layer.size,
                        cornerRadius = layer.cornerRadius,
                        style = layer.strokeStyle
                    )
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
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "urbexPressScale"
    )
    this
        .graphicsLayer {
            val s = scaleState.value
            scaleX = s
            scaleY = s
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
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "urbexButtonPressScale"
    )
    this.graphicsLayer {
        val s = scaleState.value
        scaleX = s
        scaleY = s
    }
}

private val UrbexTileEaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Smooth GPU-only staggered tile/card entrance animation when opening a tab or subview.
 * Uses a single Animatable + graphicsLayer so per-frame updates incur zero recompositions,
 * and outer urbexGlow halos placed after this modifier animate together without clipping.
 */
fun Modifier.urbexTileEnter(
    index: Int = 0,
    delayStepMs: Int = 26,
    maxCascadeItems: Int = 10,
    offsetDp: Dp = 12.dp,
    durationMs: Int = 240,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    return composed {
        val safeIndex = index.coerceAtLeast(0)
        val isCascade = safeIndex < maxCascadeItems
        val targetDelay = if (isCascade) safeIndex * delayStepMs else 0
        val targetDuration = if (isCascade) durationMs else (durationMs * 2 / 3).coerceAtLeast(130)
        val initialOffsetPx = with(LocalDensity.current) {
            (if (isCascade) offsetDp else offsetDp * 0.5f).toPx()
        }
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            if (targetDelay > 0) {
                delay(targetDelay.toLong())
            }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = targetDuration, easing = UrbexTileEaseOut)
            )
        }
        this.graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = (1f - p) * initialOffsetPx
            val s = 0.978f + 0.022f * p
            scaleX = s
            scaleY = s
        }
    }
}

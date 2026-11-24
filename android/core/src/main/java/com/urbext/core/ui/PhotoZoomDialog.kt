package com.urbext.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import kotlin.math.abs

private fun clampZoomOffset(
    rawX: Float,
    rawY: Float,
    targetScale: Float,
    stageSize: IntSize,
    imageAspect: Float?,
    horizontalPadPx: Float,
    verticalPadPx: Float
): Pair<Float, Float> {
    if (targetScale <= 1.01f || stageSize.width <= 0 || stageSize.height <= 0) {
        return 0f to 0f
    }
    val stageW = stageSize.width.toFloat()
    val stageH = stageSize.height.toFloat()
    val availW = (stageW - horizontalPadPx).coerceAtLeast(1f)
    val availH = (stageH - verticalPadPx).coerceAtLeast(1f)

    val (fittedW, fittedH) = if (imageAspect != null && imageAspect > 0f) {
        val stageAspect = availW / availH
        if (imageAspect > stageAspect) {
            availW to (availW / imageAspect)
        } else {
            (availH * imageAspect) to availH
        }
    } else {
        availW to availH
    }

    val maxX = ((fittedW * targetScale - stageW) / 2f).coerceAtLeast(0f)
    val maxY = ((fittedH * targetScale - stageH) / 2f).coerceAtLeast(0f)
    return rawX.coerceIn(-maxX, maxX) to rawY.coerceIn(-maxY, maxY)
}

/**
 * Fullscreen interactive photo zoom dialog matching Web PhotoZoomModal.tsx.
 * Features an uncluttered top header (title + counter + scale + single Close button),
 * full-bleed interactive zoom/pan stage (1x..5x) with strict viewport pan clamping,
 * and a bottom dock for thumbnails and zoom buttons.
 * Explicitly configures the Dialog window for opaque edge-to-edge display and clamps layout
 * height against physical screen bounds + real Activity system-bar insets so bottom zoom controls
 * never clip behind gesture/navigation bars and no underlying screen pixels bleed through.
 */
@Composable
fun PhotoZoomDialog(
    photoUri: String,
    title: String,
    photos: List<String> = emptyList(),
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    val gallery = remember(photoUri, photos) {
        val normalized = (if (photos.isNotEmpty()) photos else listOf(photoUri))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (normalized.isNotEmpty()) normalized else listOf(photoUri)
    }

    var currentIdx by remember(gallery, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, (gallery.size - 1).coerceAtLeast(0)))
    }
    val activeUri = gallery.getOrElse(currentIdx) { photoUri }

    var scale by remember(activeUri) { mutableFloatStateOf(1f) }
    var offsetX by remember(activeUri) { mutableFloatStateOf(0f) }
    var offsetY by remember(activeUri) { mutableFloatStateOf(0f) }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    var imageAspect by remember(activeUri) { mutableStateOf<Float?>(null) }

    LaunchedEffect(activeUri) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        imageAspect = null
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    val stagePadHPx = with(density) { 16.dp.toPx() }
    val stagePadVPx = with(density) { 20.dp.toPx() }

    LaunchedEffect(stageSize, imageAspect, scale) {
        val (cx, cy) = clampZoomOffset(
            rawX = offsetX,
            rawY = offsetY,
            targetScale = scale,
            stageSize = stageSize,
            imageAspect = imageAspect,
            horizontalPadPx = stagePadHPx,
            verticalPadPx = stagePadVPx
        )
        if (cx != offsetX) offsetX = cx
        if (cy != offsetY) offsetY = cy
    }

    val activity = remember(context) {
        var ctx: Context = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) break
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }

    // Capture Activity-level safe insets before entering the nested Dialog window
    val callerSafePadding = WindowInsets.safeDrawing.asPaddingValues()
    val callerTopPx = with(density) { callerSafePadding.calculateTopPadding().roundToPx() }
    val callerBottomPx = with(density) { callerSafePadding.calculateBottomPadding().roundToPx() }
    val callerStartPx = with(density) { callerSafePadding.calculateStartPadding(layoutDirection).roundToPx() }
    val callerEndPx = with(density) { callerSafePadding.calculateEndPadding(layoutDirection).roundToPx() }

    val activitySystemBarsPx = remember(activity, cfg) {
        val decor = activity?.window?.decorView
        val insets = decor?.let { ViewCompat.getRootWindowInsets(it) }
        insets?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
    }
    val activityTopPx = activitySystemBarsPx?.top ?: 0
    val activityBottomPx = activitySystemBarsPx?.bottom ?: 0
    val activityLeftPx = activitySystemBarsPx?.left ?: 0
    val activityRightPx = activitySystemBarsPx?.right ?: 0

    val screenRealBoundsPx = remember(activity, cfg, density) {
        val wm = activity?.windowManager
        when {
            wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val bounds = wm.currentWindowMetrics.bounds
                bounds.width() to bounds.height()
            }
            wm != null -> {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
                dm.widthPixels to dm.heightPixels
            }
            else -> with(density) {
                cfg.screenWidthDp.dp.roundToPx() to cfg.screenHeightDp.dp.roundToPx()
            }
        }
    }
    val screenRealWidthPx = screenRealBoundsPx.first
    val screenRealHeightPx = screenRealBoundsPx.second

    val scalePct = (scale * 100).toInt()
    val isZoomed = scale > 1.02f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window

        SideEffect {
            dialogWindow?.let { win ->
                win.setBackgroundDrawable(ColorDrawable(0xFF050706.toInt()))
                win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(1.0f)
                win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                win.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                @Suppress("DEPRECATION")
                win.statusBarColor = 0xFF0D110E.toInt()
                @Suppress("DEPRECATION")
                win.navigationBarColor = 0xFF050706.toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    win.isNavigationBarContrastEnforced = false
                    win.isStatusBarContrastEnforced = false
                }
                win.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                win.setGravity(Gravity.FILL)
                WindowCompat.setDecorFitsSystemWindows(win, false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    win.attributes = win.attributes.apply {
                        fitInsetsTypes = 0
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    win.attributes = win.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
        }

        val dialogSafePadding = WindowInsets.safeDrawing.asPaddingValues()
        val dialogTopInsetPx = with(density) { dialogSafePadding.calculateTopPadding().roundToPx() }
        val dialogBottomInsetPx = with(density) { dialogSafePadding.calculateBottomPadding().roundToPx() }
        val dialogStartInsetPx = with(density) { dialogSafePadding.calculateStartPadding(layoutDirection).roundToPx() }
        val dialogEndInsetPx = with(density) { dialogSafePadding.calculateEndPadding(layoutDirection).roundToPx() }

        var dialogRootTopPx by remember { mutableIntStateOf(0) }
        var dialogRootLeftPx by remember { mutableIntStateOf(0) }
        var dialogRootWidthPx by remember { mutableIntStateOf(screenRealWidthPx) }
        var dialogRootHeightPx by remember { mutableIntStateOf(screenRealHeightPx) }

        val expectedTopPx = maxOf(callerTopPx, activityTopPx, dialogTopInsetPx)
        val expectedBottomPx = maxOf(callerBottomPx, activityBottomPx, dialogBottomInsetPx)
        val expectedLeftPx = maxOf(callerStartPx, activityLeftPx, dialogStartInsetPx)
        val expectedRightPx = maxOf(callerEndPx, activityRightPx, dialogEndInsetPx)

        val topBarInsetDp = with(density) {
            (expectedTopPx - dialogRootTopPx).coerceAtLeast(0).toDp()
        }
        val bottomDockInsetDp = with(density) {
            val bottomWindowGap = (screenRealHeightPx - dialogRootTopPx - dialogRootHeightPx).coerceAtLeast(0)
            (expectedBottomPx - bottomWindowGap).coerceAtLeast(0).toDp()
        }
        val sideStartInsetDp = with(density) {
            (expectedLeftPx - dialogRootLeftPx).coerceAtLeast(0).toDp()
        }
        val sideEndInsetDp = with(density) {
            val rightWindowGap = (screenRealWidthPx - dialogRootLeftPx - dialogRootWidthPx).coerceAtLeast(0)
            (expectedRightPx - rightWindowGap).coerceAtLeast(0).toDp()
        }
        val maxVisibleHeightDp = with(density) {
            val visiblePx = (screenRealHeightPx - dialogRootTopPx).coerceAtLeast(240)
            visiblePx.toDp()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050706))
                .onGloballyPositioned { coords ->
                    val loc = IntArray(2)
                    dialogView.getLocationOnScreen(loc)
                    val nextTop = loc[1].coerceAtLeast(0)
                    val nextLeft = loc[0].coerceAtLeast(0)
                    val nextWidth = coords.size.width.coerceAtLeast(1)
                    val nextHeight = coords.size.height.coerceAtLeast(1)
                    if (abs(nextTop - dialogRootTopPx) > 1) {
                        dialogRootTopPx = nextTop
                    }
                    if (abs(nextLeft - dialogRootLeftPx) > 1) {
                        dialogRootLeftPx = nextLeft
                    }
                    if (abs(nextWidth - dialogRootWidthPx) > 1) {
                        dialogRootWidthPx = nextWidth
                    }
                    if (abs(nextHeight - dialogRootHeightPx) > 1) {
                        dialogRootHeightPx = nextHeight
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxVisibleHeightDp)
                    .fillMaxHeight()
            ) {
                // Clean Top Header Bar (Title + Counter + Live Zoom + Single Close Button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface)
                        .padding(
                            start = sideStartInsetDp + 12.dp,
                            end = sideEndInsetDp + 12.dp,
                            top = topBarInsetDp + if (isLandscape) 6.dp else 10.dp,
                            bottom = if (isLandscape) 6.dp else 10.dp
                        ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title.ifBlank { "Просмотр снимка" },
                            color = UrbexColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (gallery.size > 1) {
                            Text(
                                text = "${currentIdx + 1} / ${gallery.size}",
                                color = UrbexColors.GreenBright,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(UrbexColors.Elevated, RoundedCornerShape(6.dp))
                                    .border(1.dp, UrbexColors.BorderStrong, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "$scalePct%",
                            color = UrbexColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .background(UrbexColors.Background, RoundedCornerShape(6.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(UrbexColors.Background)
                            .border(1.dp, UrbexColors.BorderStrong, RoundedCornerShape(10.dp))
                            .urbexBounceClick(0.94f, onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть просмотр фото",
                            tint = UrbexColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(UrbexColors.Border)
                )

                // Edge-to-Edge Interactive Gesture Canvas (clipped & pan-clamped to image bounds)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = sideStartInsetDp, end = sideEndInsetDp)
                        .clipToBounds()
                        .onSizeChanged { stageSize = it }
                        .pointerInput(activeUri, stageSize, imageAspect) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        val nextScale = 2.5f
                                        scale = nextScale
                                        val (cx, cy) = clampZoomOffset(
                                            rawX = offsetX,
                                            rawY = offsetY,
                                            targetScale = nextScale,
                                            stageSize = stageSize,
                                            imageAspect = imageAspect,
                                            horizontalPadPx = stagePadHPx,
                                            verticalPadPx = stagePadVPx
                                        )
                                        offsetX = cx
                                        offsetY = cy
                                    }
                                }
                            )
                        }
                        .pointerInput(activeUri, stageSize, imageAspect) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = nextScale
                                val (cx, cy) = clampZoomOffset(
                                    rawX = offsetX + pan.x,
                                    rawY = offsetY + pan.y,
                                    targetScale = nextScale,
                                    stageSize = stageSize,
                                    imageAspect = imageAspect,
                                    horizontalPadPx = stagePadHPx,
                                    verticalPadPx = stagePadVPx
                                )
                                offsetX = cx
                                offsetY = cy
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = activeUri,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        onSuccess = { result ->
                            val d = result.result.drawable
                            if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                                imageAspect = d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )

                    if (gallery.size > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 10.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(UrbexColors.Surface.copy(alpha = 0.90f))
                                .border(1.dp, UrbexColors.BorderStrong, CircleShape)
                                .urbexBounceClick(0.92f) {
                                    currentIdx = (currentIdx - 1 + gallery.size) % gallery.size
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Предыдущее фото",
                                tint = UrbexColors.TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 10.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(UrbexColors.Surface.copy(alpha = 0.90f))
                                .border(1.dp, UrbexColors.BorderStrong, CircleShape)
                                .urbexBounceClick(0.92f) {
                                    currentIdx = (currentIdx + 1) % gallery.size
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Следующее фото",
                                tint = UrbexColors.TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Bottom Tactical Dock: Gallery Strip + Non-overlapping Zoom Controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(UrbexColors.Border)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface)
                        .padding(
                            start = sideStartInsetDp + 12.dp,
                            end = sideEndInsetDp + 12.dp,
                            top = if (isLandscape) 8.dp else 10.dp,
                            bottom = bottomDockInsetDp + if (isLandscape) 10.dp else 14.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (gallery.size > 1) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            itemsIndexed(gallery) { idx, thumbUri ->
                                val isSelected = idx == currentIdx
                                Box(
                                    modifier = Modifier
                                        .size(
                                            width = if (isLandscape) 48.dp else 56.dp,
                                            height = if (isLandscape) 34.dp else 42.dp
                                        )
                                        .urbexGlow(
                                            color = UrbexColors.GreenBright,
                                            radius = 10.dp,
                                            cornerRadius = 8.dp,
                                            alpha = 0.24f,
                                            enabled = isSelected
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) UrbexColors.GreenBright else UrbexColors.Border,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .urbexBounceClick(0.95f) { currentIdx = idx }
                                ) {
                                    AsyncImage(
                                        model = thumbUri,
                                        contentDescription = "Фото ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .background(UrbexColors.Background, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canZoomOut = scale > 1.01f
                        val canZoomIn = scale < 4.99f

                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(UrbexColors.Surface)
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(9.dp))
                                .urbexBounceClick(0.94f) {
                                    if (canZoomOut) {
                                        val next = (scale - 0.5f).coerceIn(1f, 5f)
                                        scale = next
                                        val (cx, cy) = clampZoomOffset(
                                            rawX = offsetX,
                                            rawY = offsetY,
                                            targetScale = next,
                                            stageSize = stageSize,
                                            imageAspect = imageAspect,
                                            horizontalPadPx = stagePadHPx,
                                            verticalPadPx = stagePadVPx
                                        )
                                        offsetX = cx
                                        offsetY = cy
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Уменьшить",
                                tint = if (canZoomOut) UrbexColors.TextPrimary else UrbexColors.TextDim,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .height(38.dp)
                                .urbexGlow(UrbexColors.GreenBright, radius = 10.dp, cornerRadius = 9.dp, alpha = 0.22f, enabled = isZoomed)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isZoomed) UrbexColors.GreenSoft else UrbexColors.Surface)
                                .border(
                                    1.dp,
                                    if (isZoomed) UrbexColors.Green else UrbexColors.Border,
                                    RoundedCornerShape(9.dp)
                                )
                                .urbexBounceClick(0.95f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = if (isZoomed) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "1:1 · $scalePct%",
                                color = if (isZoomed) UrbexColors.GreenBright else UrbexColors.TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(UrbexColors.Surface)
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(9.dp))
                                .urbexBounceClick(0.94f) {
                                    if (canZoomIn) {
                                        val next = (scale + 0.5f).coerceIn(1f, 5f)
                                        scale = next
                                        val (cx, cy) = clampZoomOffset(
                                            rawX = offsetX,
                                            rawY = offsetY,
                                            targetScale = next,
                                            stageSize = stageSize,
                                            imageAspect = imageAspect,
                                            horizontalPadPx = stagePadHPx,
                                            verticalPadPx = stagePadVPx
                                        )
                                        offsetX = cx
                                        offsetY = cy
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Приблизить",
                                tint = if (canZoomIn) UrbexColors.TextPrimary else UrbexColors.TextDim,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

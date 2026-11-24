package com.urbext.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * Fullscreen interactive photo zoom dialog matching Web PhotoZoomModal.tsx.
 * Features an uncluttered top header (title + counter + scale + single Close button),
 * full-bleed interactive zoom/pan stage (1x..5x), and a bottom dock for thumbnails and zoom buttons.
 * Uses explicit Box buttons (no Material IconButton) so buttons never overlap regardless of screen width.
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

    LaunchedEffect(activeUri) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    val scalePct = (scale * 100).toInt()
    val isZoomed = scale > 1.02f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFA050706)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Clean Top Header Bar (Title + Counter + Live Zoom + Single Close Button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface.copy(alpha = 0.94f))
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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

                // Edge-to-Edge Interactive Gesture Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(activeUri) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(activeUri) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = nextScale
                                if (nextScale > 1.02f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = activeUri,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
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
                        .background(UrbexColors.Surface.copy(alpha = 0.95f))
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                        .size(width = 56.dp, height = 42.dp)
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
                                        if (next <= 1.01f) {
                                            scale = 1f
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
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
                                        scale = (scale + 0.5f).coerceIn(1f, 5f)
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

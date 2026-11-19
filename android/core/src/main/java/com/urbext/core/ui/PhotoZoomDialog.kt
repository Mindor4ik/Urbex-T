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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Supports single photo or multi-photo gallery switching, pinch-to-zoom (1x..5x),
 * pan drag, double-tap toggle, and top +/-/1:1 controls.
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xF5080B08)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface.copy(alpha = 0.92f))
                        .border(0.5.dp, UrbexColors.Border)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title.ifBlank { "Просмотр фото" },
                                color = UrbexColors.TextPrimary,
                                fontSize = 13.sp,
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
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(UrbexColors.Elevated, RoundedCornerShape(5.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(5.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Сведение/разведение пальцев или двойной тап · ${(scale * 100).toInt()}%",
                            color = UrbexColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                scale = (scale - 0.5f).coerceIn(1f, 5f)
                                if (scale <= 1.01f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Уменьшить", tint = UrbexColors.TextPrimary, modifier = Modifier.size(16.dp))
                        }

                        Text(
                            text = "1:1",
                            color = UrbexColors.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                .urbexBounceClick(0.95f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                        )

                        IconButton(
                            onClick = { scale = (scale + 0.5f).coerceIn(1f, 5f) },
                            modifier = Modifier
                                .size(34.dp)
                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить", tint = UrbexColors.TextPrimary, modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .background(UrbexColors.Elevated, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = UrbexColors.TextPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Interactive Gesture Canvas
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
                            .padding(8.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )

                    if (gallery.size > 1) {
                        IconButton(
                            onClick = {
                                currentIdx = (currentIdx - 1 + gallery.size) % gallery.size
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 10.dp)
                                .size(38.dp)
                                .background(UrbexColors.Surface.copy(alpha = 0.88f), CircleShape)
                                .border(1.dp, UrbexColors.Border, CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Предыдущее фото",
                                tint = UrbexColors.TextPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                currentIdx = (currentIdx + 1) % gallery.size
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 10.dp)
                                .size(38.dp)
                                .background(UrbexColors.Surface.copy(alpha = 0.88f), CircleShape)
                                .border(1.dp, UrbexColors.Border, CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Следующее фото",
                                tint = UrbexColors.TextPrimary
                            )
                        }
                    }
                }

                // Bottom Thumbnail Switcher for multi-photo galleries
                if (gallery.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Surface.copy(alpha = 0.92f))
                            .border(0.5.dp, UrbexColors.Border)
                            .padding(vertical = 8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(gallery) { idx, thumbUri ->
                            val isSelected = idx == currentIdx
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
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
            }
        }
    }
}

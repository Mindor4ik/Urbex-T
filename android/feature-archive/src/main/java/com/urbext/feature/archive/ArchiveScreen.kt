package com.urbext.feature.archive

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.domain.model.ArchiveItem
import com.urbext.domain.model.ArchiveMediaType
import com.urbext.domain.repository.ArchiveRepository
import kotlinx.coroutines.launch

@Composable
fun ArchiveScreen(
    archiveRepository: ArchiveRepository,
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var typeFilter by remember { mutableStateOf<ArchiveMediaType?>(null) }
    var inspecting by remember { mutableStateOf<ArchiveItem?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }

    val items by archiveRepository.observeItems(typeFilter)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val docPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val mediaType = when {
            mime.startsWith("image/") -> ArchiveMediaType.PHOTO
            mime.startsWith("video/") -> ArchiveMediaType.VIDEO
            mime.startsWith("audio/") -> ArchiveMediaType.AUDIO
            mime.startsWith("text/") -> ArchiveMediaType.NOTE
            else -> ArchiveMediaType.DOCUMENT
        }
        val now = System.currentTimeMillis()
        scope.launch {
            archiveRepository.addItem(
                ArchiveItem(
                    id = "arch-${now.toString(36)}",
                    title = uri.lastPathSegment?.substringAfterLast('/') ?: "Файл",
                    type = mediaType,
                    mimeType = mime,
                    sizeBytes = 0L,
                    contentUri = uri.toString(),
                    notes = "Добавлено с устройства",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    AnimatedContent(
        targetState = inspecting,
        transitionSpec = {
            (fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200)))
                .togetherWith(fadeOut(tween(140)))
        },
        contentKey = { it?.id },
        label = "ArchiveInspectorTransition"
    ) { item ->
        if (item != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UrbexColors.Background)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                        .urbexBounceClick(0.95f) { inspecting = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = UrbexColors.TextPrimary
                    )
                    Text(
                        text = " К архиву",
                        color = UrbexColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(item.title, color = UrbexColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${item.type.label} · ${item.mimeType}",
                    color = UrbexColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                if (item.type == ArchiveMediaType.PHOTO && item.contentUri != null) {
                    AsyncImage(
                        model = item.contentUri,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                    )
                }
                if (!item.textContent.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text(item.textContent!!, color = UrbexColors.TextPrimary, fontSize = 14.sp)
                    }
                }
                if (item.notes.isNotBlank()) {
                    Text(item.notes, color = UrbexColors.TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UrbexColors.Background)
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val noteInteraction = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = { showNoteDialog = true },
                        interactionSource = noteInteraction,
                        modifier = Modifier
                            .weight(1f)
                            .urbexScaleOnPress(noteInteraction, 0.96f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" Заметка", fontSize = 12.sp)
                    }
                    val fileInteraction = remember { MutableInteractionSource() }
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                        onClick = { docPicker.launch(arrayOf("image/*", "video/*", "audio/*", "text/*", "application/pdf")) },
                        interactionSource = fileInteraction,
                        modifier = Modifier
                            .weight(1f)
                            .urbexScaleOnPress(fileInteraction, 0.96f)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = UrbexColors.Background)
                        Text(" Файл", color = UrbexColors.Background, fontSize = 12.sp)
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "all") {
                        ArchiveFilterPill("Все", typeFilter == null) { typeFilter = null }
                    }
                    items(ArchiveMediaType.entries, key = { it.name }) { t ->
                        ArchiveFilterPill(t.label, typeFilter == t) { typeFilter = t }
                    }
                }

                AnimatedContent(
                    targetState = items.isEmpty(),
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
                    label = "ArchiveEmptyTransition"
                ) { isEmpty ->
                    if (isEmpty) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(bottom = bottomContentPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "В архиве пока нет записей выбранного типа.",
                                color = UrbexColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp + bottomContentPadding),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items, key = { it.id }) { entry ->
                                val delInteraction = remember { MutableInteractionSource() }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                                        .urbexBounceClick(0.98f) { inspecting = entry }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.title, color = UrbexColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Text(
                                            text = "${entry.type.label} · ${entry.mimeType}",
                                            color = UrbexColors.TextSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { scope.launch { archiveRepository.deleteItem(entry.id) } },
                                        interactionSource = delInteraction,
                                        modifier = Modifier.urbexScaleOnPress(delInteraction, 0.90f)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = UrbexColors.Danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNoteDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            containerColor = UrbexColors.Surface,
            title = { Text("Полевая заметка", color = UrbexColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Текст") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                    onClick = {
                        if (title.trim().length < 2 || body.trim().length < 2) return@Button
                        val now = System.currentTimeMillis()
                        scope.launch {
                            archiveRepository.addItem(
                                ArchiveItem(
                                    id = "arch-${now.toString(36)}",
                                    title = title.trim(),
                                    type = ArchiveMediaType.NOTE,
                                    mimeType = "text/plain",
                                    sizeBytes = body.length.toLong(),
                                    textContent = body.trim(),
                                    notes = "Полевая заметка PDA",
                                    createdAt = now,
                                    updatedAt = now
                                )
                            )
                            showNoteDialog = false
                        }
                    }
                ) {
                    Text("Сохранить", color = UrbexColors.Background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("Отмена", color = UrbexColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ArchiveFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Surface,
        animationSpec = tween(160),
        label = "archivePillBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
        animationSpec = tween(160),
        label = "archivePillBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
        animationSpec = tween(160),
        label = "archivePillText"
    )
    Text(
        text = label,
        color = textColor,
        fontSize = 12.sp,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .urbexBounceClick(0.95f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

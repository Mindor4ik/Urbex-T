package com.urbext.feature.archive

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.urbext.core.geo.GeoUtils
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.domain.model.ArchiveItem
import com.urbext.domain.model.ArchiveMediaType
import com.urbext.domain.repository.ArchiveRepository
import com.urbext.domain.repository.ObjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArchiveScreen(
    archiveRepository: ArchiveRepository,
    objectRepository: ObjectRepository,
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var typeFilter by remember { mutableStateOf<ArchiveMediaType?>(null) }
    var inspecting by remember { mutableStateOf<ArchiveItem?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }

    val items by archiveRepository.observeItems(typeFilter)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val objects by objectRepository.observeObjects()
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
        scope.launch {
            var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Файл"
            var sizeBytes = 0L
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx >= 0) {
                                displayName = cursor.getString(nameIdx) ?: displayName
                            }
                            if (sizeIdx >= 0) {
                                sizeBytes = cursor.getLong(sizeIdx)
                            }
                        }
                    }
                }
            }
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val isText = mime.startsWith("text/") || displayName.endsWith(".txt") || displayName.endsWith(".md")
            val mediaType = when {
                mime.startsWith("image/") -> ArchiveMediaType.PHOTO
                mime.startsWith("video/") -> ArchiveMediaType.VIDEO
                mime.startsWith("audio/") -> ArchiveMediaType.AUDIO
                isText -> ArchiveMediaType.NOTE
                else -> ArchiveMediaType.DOCUMENT
            }
            val textBody = if (isText) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
            } else {
                null
            }

            val now = System.currentTimeMillis()
            val newItem = ArchiveItem(
                id = "arch-${now.toString(36)}",
                title = displayName,
                type = mediaType,
                mimeType = mime,
                sizeBytes = sizeBytes,
                contentUri = uri.toString(),
                textContent = textBody,
                notes = "Импортировано с устройства в локальный архив.",
                createdAt = now,
                updatedAt = now
            )
            archiveRepository.addItem(newItem)
            inspecting = newItem
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
            val linkedObj = item.objectId?.let { id -> objects.firstOrNull { it.id == id } }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UrbexColors.Background)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.contentUri != null) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(item.contentUri), item.mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" Открыть", fontSize = 12.sp)
                            }
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    archiveRepository.deleteItem(item.id)
                                    inspecting = null
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = UrbexColors.Danger)
                        }
                    }
                }

                Text(item.title, color = UrbexColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${item.type.label} · ${GeoUtils.formatBytes(item.sizeBytes)} · ${GeoUtils.formatShortDate(item.createdAt)}",
                    color = UrbexColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                if (linkedObj != null) {
                    Text(
                        text = "Объект: ${linkedObj.title}",
                        color = UrbexColors.GreenBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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
                        Text(item.textContent!!, color = UrbexColors.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
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
                        Text(" Полевая заметка", fontSize = 12.sp)
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
                        Text(" Добавить файл", color = UrbexColors.Background, fontSize = 12.sp)
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
                                text = "В выбранной категории нет материалов.",
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
                                val linkedTitle = entry.objectId?.let { oid -> objects.firstOrNull { it.id == oid }?.title }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                                        .urbexBounceClick(0.98f) { inspecting = entry }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (entry.type == ArchiveMediaType.PHOTO && entry.contentUri != null) {
                                        AsyncImage(
                                            model = entry.contentUri,
                                            contentDescription = entry.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val (icon, tint) = when (entry.type) {
                                                ArchiveMediaType.PHOTO -> Icons.Default.Image to UrbexColors.GreenBright
                                                ArchiveMediaType.VIDEO -> Icons.Default.VideoFile to UrbexColors.Amber
                                                ArchiveMediaType.AUDIO -> Icons.Default.AudioFile to UrbexColors.Amber
                                                ArchiveMediaType.NOTE, ArchiveMediaType.DOCUMENT -> Icons.Default.Description to UrbexColors.TextSecondary
                                            }
                                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(entry.title, color = UrbexColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "${entry.type.label} · ${GeoUtils.formatBytes(entry.sizeBytes)} · ${GeoUtils.formatShortDate(entry.createdAt)}",
                                            color = UrbexColors.TextSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (linkedTitle != null) {
                                            Text(
                                                text = "Объект: $linkedTitle",
                                                color = UrbexColors.GreenBright,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
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
        var selectedObjectId by remember { mutableStateOf<String?>(null) }
        var noteError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            containerColor = UrbexColors.Surface,
            title = { Text("Новая полевая заметка в архив", color = UrbexColors.TextPrimary, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(visible = noteError != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
                        Text(noteError.orEmpty(), color = Color(0xFFF1ACA7), fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Название заметки") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Привязка к объекту", color = UrbexColors.TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item(key = "none") {
                            ArchiveFilterPill("Без привязки", selectedObjectId == null) { selectedObjectId = null }
                        }
                        items(objects, key = { it.id }) { obj ->
                            ArchiveFilterPill(obj.title.take(22), selectedObjectId == obj.id) { selectedObjectId = obj.id }
                        }
                    }
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Наблюдения, замеры, маршрутные пометки...") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                    onClick = {
                        if (title.trim().length < 2 || body.trim().length < 2) {
                            noteError = "Введите заголовок и текст полевой заметки."
                            return@Button
                        }
                        val now = System.currentTimeMillis()
                        val newNote = ArchiveItem(
                            id = "arch-${now.toString(36)}",
                            title = title.trim(),
                            type = ArchiveMediaType.NOTE,
                            objectId = selectedObjectId,
                            mimeType = "text/plain",
                            sizeBytes = body.trim().toByteArray(Charsets.UTF_8).size.toLong(),
                            textContent = body.trim(),
                            notes = "Полевая заметка PDA",
                            createdAt = now,
                            updatedAt = now
                        )
                        scope.launch {
                            archiveRepository.addItem(newNote)
                            inspecting = newNote
                            showNoteDialog = false
                        }
                    }
                ) {
                    Text("Сохранить заметку", color = UrbexColors.Background)
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

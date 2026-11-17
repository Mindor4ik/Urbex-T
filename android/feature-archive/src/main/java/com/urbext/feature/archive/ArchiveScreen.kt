package com.urbext.feature.archive

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.urbext.core.ui.UrbexColors
import com.urbext.domain.model.ArchiveItem
import com.urbext.domain.model.ArchiveMediaType
import com.urbext.domain.repository.ArchiveRepository
import kotlinx.coroutines.launch

@Composable
fun ArchiveScreen(archiveRepository: ArchiveRepository) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UrbexColors.Background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showNoteDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Заметка", fontSize = 12.sp)
            }
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                onClick = { docPicker.launch(arrayOf("image/*", "video/*", "audio/*", "text/*", "application/pdf")) },
                modifier = Modifier.weight(1f)
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                        .clickable { inspecting = item }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, color = UrbexColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            text = "${item.type.label} · ${item.mimeType}",
                            color = UrbexColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = {
                        scope.launch { archiveRepository.deleteItem(item.id) }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = UrbexColors.Danger)
                    }
                }
            }
        }
    }

    inspecting?.let { item ->
        AlertDialog(
            onDismissRequest = { inspecting = null },
            containerColor = UrbexColors.Surface,
            title = { Text(item.title, color = UrbexColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.type == ArchiveMediaType.PHOTO && item.contentUri != null) {
                        AsyncImage(
                            model = item.contentUri,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    if (!item.textContent.isNullOrBlank()) {
                        Text(item.textContent!!, color = UrbexColors.TextPrimary, fontSize = 13.sp)
                    }
                    Text(item.notes, color = UrbexColors.TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { inspecting = null }) {
                    Text("Закрыть", color = UrbexColors.GreenBright)
                }
            }
        )
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
    Text(
        text = label,
        color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier
            .background(if (selected) UrbexColors.Elevated else UrbexColors.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.urbext.core.ui.PhotoZoomDialog
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.UrbexCompactMultilineField
import com.urbext.core.ui.UrbexDropdownField
import com.urbext.core.ui.UrbexDropdownOption
import com.urbext.core.ui.UrbexFilledButton
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexGlow
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.domain.model.ArchiveItem
import com.urbext.domain.model.ArchiveMediaType
import com.urbext.domain.model.WorkspaceState
import com.urbext.domain.repository.ArchiveRepository
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArchiveScreen(
    archiveRepository: ArchiveRepository,
    objectRepository: ObjectRepository,
    workspaceRepository: WorkspaceRepository? = null,
    editorEnabled: Boolean = false,
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    var typeFilter by remember { mutableStateOf<ArchiveMediaType?>(null) }
    var inspecting by remember { mutableStateOf<ArchiveItem?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var zoomedPhoto by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(editorEnabled) {
        if (!editorEnabled) showNoteDialog = false
    }

    val items by archiveRepository.observeItems(typeFilter)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val objects by objectRepository.observeObjects()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val workspaceState by (workspaceRepository?.observeWorkspaces() ?: flowOf(WorkspaceState()))
        .collectAsStateWithLifecycle(initialValue = WorkspaceState())
    val archiveSources by (workspaceRepository?.observeArchiveSources() ?: flowOf(emptyMap()))
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val isMultiMounted = workspaceState.mountedDbIds.size > 1

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
                    .padding(
                        start = if (isLandscape) 10.dp else 16.dp,
                        top = if (isLandscape) 8.dp else 16.dp,
                        end = if (isLandscape) 10.dp else 16.dp,
                        bottom = (if (isLandscape) 10.dp else 16.dp) + bottomContentPadding
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 12.dp)
            ) {
                val topActionH = if (isLandscape) 34.dp else 38.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .height(topActionH)
                            .clip(RoundedCornerShape(8.dp))
                            .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.95f) { inspecting = null }
                            .padding(horizontal = if (isLandscape) 10.dp else 12.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = UrbexColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "К архиву",
                            color = UrbexColors.TextPrimary,
                            fontSize = if (isLandscape) 12.sp else 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.contentUri != null) {
                            OutlinedButton(
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(topActionH),
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
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Открыть", fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }
                        }
                        if (editorEnabled) {
                            Box(
                                modifier = Modifier
                                    .size(topActionH)
                                    .urbexGlow(UrbexColors.Danger, radius = 10.dp, cornerRadius = 8.dp, alpha = 0.26f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(UrbexColors.DangerSoft.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Danger.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.94f) {
                                        scope.launch {
                                            archiveRepository.deleteItem(item.id)
                                            inspecting = null
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = UrbexColors.Danger, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1.15f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (item.type == ArchiveMediaType.PHOTO && item.contentUri != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                        .urbexBounceClick(0.99f) { zoomedPhoto = item.contentUri!! to item.title }
                                ) {
                                    AsyncImage(
                                        model = item.contentUri,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize()
                                    )
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .background(Color(0xCC0D110D), RoundedCornerShape(8.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить", tint = UrbexColors.TextPrimary, modifier = Modifier.size(13.dp))
                                        Text("Приблизить", color = UrbexColors.TextPrimary, fontSize = 11.sp)
                                    }
                                }
                            }
                            if (!item.textContent.isNullOrBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(item.textContent!!, color = UrbexColors.TextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
                                }
                            }
                            if ((item.type != ArchiveMediaType.PHOTO || item.contentUri == null) && item.textContent.isNullOrBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(item.type.label, color = UrbexColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(item.mimeType, color = UrbexColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(0.85f)
                                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(item.title, color = UrbexColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${item.type.label} · ${GeoUtils.formatBytes(item.sizeBytes)} · ${GeoUtils.formatShortDate(item.createdAt)}",
                                color = UrbexColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                            if (linkedObj != null) {
                                Text(
                                    text = "Объект: ${linkedObj.title}",
                                    color = UrbexColors.GreenBright,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (item.notes.isNotBlank()) {
                                Text(item.notes, color = UrbexColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                    }
                } else {
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                .urbexBounceClick(0.99f) { zoomedPhoto = item.contentUri!! to item.title }
                        ) {
                            AsyncImage(
                                model = item.contentUri,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .background(Color(0xCC0D110D), RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить", tint = UrbexColors.TextPrimary, modifier = Modifier.size(14.dp))
                                Text("Приблизить", color = UrbexColors.TextPrimary, fontSize = 11.sp)
                            }
                        }
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
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UrbexColors.Background)
                    .padding(
                        start = if (isLandscape) 10.dp else 12.dp,
                        top = if (isLandscape) 8.dp else 12.dp,
                        end = if (isLandscape) 10.dp else 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp)
            ) {
                if (isLandscape) {
                    // Single compact horizontal toolbar row in landscape
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item(key = "all") {
                                ArchiveFilterPill("Все", typeFilter == null, compact = true) { typeFilter = null }
                            }
                            items(ArchiveMediaType.entries, key = { it.name }) { t ->
                                ArchiveFilterPill(t.label, typeFilter == t, compact = true) { typeFilter = t }
                            }
                        }

                        if (editorEnabled) {
                            val noteInteraction = remember { MutableInteractionSource() }
                            OutlinedButton(
                                onClick = { showNoteDialog = true },
                                interactionSource = noteInteraction,
                                shape = RoundedCornerShape(9.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .urbexScaleOnPress(noteInteraction, 0.96f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Заметка", fontSize = 12.sp)
                            }
                            UrbexFilledButton(
                                onClick = { docPicker.launch(arrayOf("image/*", "video/*", "audio/*", "text/*", "application/pdf")) },
                                height = 36.dp,
                                cornerRadius = 9.dp,
                                glowColor = UrbexColors.GreenBright,
                                glowRadius = 12.dp,
                                glowAlpha = 0.30f,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Файл", color = UrbexColors.Background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item(key = "all") {
                                ArchiveFilterPill("Все", typeFilter == null) { typeFilter = null }
                            }
                            items(ArchiveMediaType.entries, key = { it.name }) { t ->
                                ArchiveFilterPill(t.label, typeFilter == t) { typeFilter = t }
                            }
                        }

                        if (editorEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val noteInteraction = remember { MutableInteractionSource() }
                                OutlinedButton(
                                    onClick = { showNoteDialog = true },
                                    shape = RoundedCornerShape(9.dp),
                                    interactionSource = noteInteraction,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .urbexScaleOnPress(noteInteraction, 0.96f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Заметка", fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                                UrbexFilledButton(
                                    onClick = { docPicker.launch(arrayOf("image/*", "video/*", "audio/*", "text/*", "application/pdf")) },
                                    height = 40.dp,
                                    cornerRadius = 9.dp,
                                    glowColor = UrbexColors.GreenBright,
                                    glowRadius = 12.dp,
                                    glowAlpha = 0.30f,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Добавить файл", color = UrbexColors.Background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (isLandscape) 2 else 1),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = (if (isLandscape) 12.dp else 24.dp) + bottomContentPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
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
                                        .padding(if (isLandscape) 8.dp else 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val thumbSize = if (isLandscape) 42.dp else 50.dp
                                    if (entry.type == ArchiveMediaType.PHOTO && entry.contentUri != null) {
                                        AsyncImage(
                                            model = entry.contentUri,
                                            contentDescription = entry.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(thumbSize)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(thumbSize)
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
                                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (isLandscape) 20.dp else 22.dp))
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(entry.title, color = UrbexColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = if (isLandscape) 13.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "${entry.type.label} · ${GeoUtils.formatBytes(entry.sizeBytes)} · ${GeoUtils.formatShortDate(entry.createdAt)}",
                                            color = UrbexColors.TextSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val entryDbSource = archiveSources[entry.id]
                                        if (isMultiMounted && entryDbSource != null) {
                                            Text(
                                                text = "БД: ${entryDbSource.dbName}",
                                                color = UrbexColors.Amber,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
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
                                    if (editorEnabled) {
                                        IconButton(
                                            onClick = { scope.launch { archiveRepository.deleteItem(entry.id) } },
                                            interactionSource = delInteraction,
                                            modifier = Modifier
                                                .size(if (isLandscape) 32.dp else 40.dp)
                                                .urbexScaleOnPress(delInteraction, 0.90f)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = UrbexColors.Danger, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorEnabled && showNoteDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var selectedObjectId by remember { mutableStateOf<String?>(null) }
        var noteError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            containerColor = UrbexColors.Surface,
            title = { Text("Новая полевая заметка в архив", color = UrbexColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    AnimatedVisibility(visible = noteError != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
                        Text(noteError.orEmpty(), color = Color(0xFFF1ACA7), fontSize = 12.sp)
                    }
                    UrbexCompactInputField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Название заметки",
                        placeholder = "Например: Замер радиации у шлюза",
                        minHeight = 36.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    UrbexDropdownField(
                        selectedValue = selectedObjectId,
                        label = "Привязка к объекту",
                        compact = true,
                        options = listOf(
                            UrbexDropdownOption<String?>(value = null, label = "Без привязки к объекту")
                        ) + objects.map { obj ->
                            UrbexDropdownOption<String?>(
                                value = obj.id,
                                label = obj.title,
                                subtitle = obj.category.label
                            )
                        },
                        onSelect = { selectedObjectId = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    UrbexCompactMultilineField(
                        value = body,
                        onValueChange = { body = it },
                        label = "Наблюдения, замеры, маршрутные пометки",
                        placeholder = "Текст полевой заметки...",
                        minLines = if (isLandscape) 2 else 3,
                        minHeight = if (isLandscape) 54.dp else 72.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                UrbexFilledButton(
                    height = 36.dp,
                    cornerRadius = 9.dp,
                    glowColor = UrbexColors.GreenBright,
                    glowRadius = 12.dp,
                    glowAlpha = 0.30f,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    onClick = {
                        if (title.trim().length < 2 || body.trim().length < 2) {
                            noteError = "Введите заголовок и текст полевой заметки."
                            return@UrbexFilledButton
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

    zoomedPhoto?.let { (uri, title) ->
        PhotoZoomDialog(
            photoUri = uri,
            title = title,
            onDismiss = { zoomedPhoto = null }
        )
    }
}

@Composable
private fun ArchiveFilterPill(
    label: String,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
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
        fontSize = if (compact) 11.sp else 12.sp,
        modifier = Modifier
            .urbexGlow(UrbexColors.GreenBright, radius = 9.dp, cornerRadius = 8.dp, alpha = 0.25f, enabled = selected)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .urbexBounceClick(0.95f, onClick = onClick)
            .padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 6.dp else 8.dp
            )
    )
}

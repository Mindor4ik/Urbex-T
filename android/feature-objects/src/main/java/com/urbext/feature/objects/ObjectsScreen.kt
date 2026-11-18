package com.urbext.feature.objects

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.urbext.core.geo.GeoUtils
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.color
import com.urbext.core.ui.urbexBounceClick
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.ObjectsViewMode
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun ObjectsScreen(
    objectRepository: ObjectRepository,
    settingsRepository: SettingsRepository,
    initialSelectedId: String? = null
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<ReliabilityStatus?>(null) }
    var viewMode by remember { mutableStateOf(ObjectsViewMode.CARDS) }
    var activeObjectId by remember { mutableStateOf(initialSelectedId) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialSelectedId) {
        if (initialSelectedId != null) {
            activeObjectId = initialSelectedId
        }
    }

    val objects by objectRepository.observeObjects(statusFilter, query)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    val inspecting = remember(objects, activeObjectId) {
        activeObjectId?.let { id -> objects.firstOrNull { it.id == id } }
    }

    AnimatedContent(
        targetState = inspecting,
        contentKey = { it?.id },
        transitionSpec = {
            (fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200))) togetherWith fadeOut(tween(140))
        },
        label = "ObjectsDetailTransition"
    ) { obj ->
        if (obj != null) {
            // Full-area Object Detail View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UrbexColors.Background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.95f) { activeObjectId = null }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = UrbexColors.TextPrimary)
                        Text("К списку", color = UrbexColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    IconButton(onClick = {
                        scope.launch {
                            objectRepository.deleteObject(obj.id)
                            activeObjectId = null
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = UrbexColors.Danger)
                    }
                }

                Text(
                    text = "${obj.status.glyph} ${obj.status.label} · ${obj.category.label}",
                    color = obj.status.color(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = obj.title,
                    color = UrbexColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                if (obj.photoUri != null) {
                    AsyncImage(
                        model = obj.photoUri,
                        contentDescription = obj.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(UrbexColors.Surface)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = GeoUtils.formatCoordinates(obj.lat, obj.lon, settings.coordinateFormat),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = UrbexColors.Amber
                    )
                    Text(
                        text = "Высота: ${obj.elevationM} м · Автор: ${obj.authorNodeId}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = UrbexColors.TextSecondary
                    )
                }

                Text("Достоверность:", color = UrbexColors.TextSecondary, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ReliabilityStatus.entries, key = { it.name }) { st ->
                        FilterChipButton(
                            label = "${st.glyph} ${st.label}",
                            selected = obj.status == st,
                            onClick = {
                                val updated = obj.copy(status = st, updatedAt = System.currentTimeMillis())
                                scope.launch { objectRepository.upsertObject(updated) }
                            }
                        )
                    }
                }

                Text("Описание", color = UrbexColors.TextSecondary, fontSize = 12.sp)
                Text(obj.description, color = UrbexColors.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)

                Text("Подход и периметр", color = UrbexColors.TextSecondary, fontSize = 12.sp)
                Text(obj.accessNotes, color = UrbexColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)

                if (obj.hazards.isNotEmpty()) {
                    Text("Факторы риска", color = UrbexColors.Amber, fontSize = 12.sp)
                    Text(obj.hazards.joinToString(" · "), color = UrbexColors.TextPrimary, fontSize = 13.sp)
                }
            }
        } else {
            // Objects Browser with 3 View Modes (List / Compact / Cards)
            Scaffold(
                containerColor = UrbexColors.Background,
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = UrbexColors.Green,
                        contentColor = UrbexColors.Background
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить объект")
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Поиск объектов...", color = UrbexColors.TextDim) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // View mode selector: Список / Компакт / Карточки
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ObjectsViewMode.entries, key = { it.name }) { mode ->
                            FilterChipButton(
                                label = mode.label,
                                selected = viewMode == mode,
                                onClick = { viewMode = mode }
                            )
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "all") {
                            FilterChipButton(
                                label = "Все",
                                selected = statusFilter == null,
                                onClick = { statusFilter = null }
                            )
                        }
                        items(ReliabilityStatus.entries, key = { it.name }) { status ->
                            FilterChipButton(
                                label = "${status.glyph} ${status.label}",
                                selected = statusFilter == status,
                                onClick = { statusFilter = status }
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = viewMode,
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                        label = "ObjectsViewModeTransition"
                    ) { mode ->
                        when (mode) {
                            ObjectsViewMode.LIST -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 72.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(objects, key = { it.id }) { item ->
                                        Row(
                                            modifier = Modifier
                                                .animateItem()
                                                .fillMaxWidth()
                                                .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                                                .urbexBounceClick(0.98f) { activeObjectId = item.id }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.title, color = UrbexColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(GeoUtils.formatCoordinates(item.lat, item.lon, settings.coordinateFormat), color = UrbexColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                            Text("${item.status.glyph} ${item.status.label}", color = item.status.color(), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            ObjectsViewMode.COMPACT -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 72.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(objects, key = { it.id }) { item ->
                                        Row(
                                            modifier = Modifier
                                                .animateItem()
                                                .fillMaxWidth()
                                                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                                .urbexBounceClick(0.98f) { activeObjectId = item.id }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (item.photoUri != null) {
                                                AsyncImage(
                                                    model = item.photoUri,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(UrbexColors.Background)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("${item.status.glyph} ${item.status.label}", color = item.status.color(), fontSize = 11.sp)
                                                Text(item.title, color = UrbexColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(item.description, color = UrbexColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }

                            ObjectsViewMode.CARDS -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 260.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 72.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(objects, key = { it.id }) { item ->
                                        Column(
                                            modifier = Modifier
                                                .animateItem()
                                                .fillMaxWidth()
                                                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                                .clip(RoundedCornerShape(12.dp))
                                                .urbexBounceClick(0.97f) { activeObjectId = item.id }
                                        ) {
                                            if (item.photoUri != null) {
                                                AsyncImage(
                                                    model = item.photoUri,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxWidth().height(130.dp).background(UrbexColors.Background)
                                                )
                                            }
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("${item.status.glyph} ${item.status.label}", color = item.status.color(), fontSize = 11.sp)
                                                Text(item.title, color = UrbexColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(item.description, color = UrbexColors.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(GeoUtils.formatCoordinates(item.lat, item.lon, settings.coordinateFormat), color = UrbexColors.TextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
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
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var latText by remember { mutableStateOf("55.75124") }
        var lonText by remember { mutableStateOf("37.61842") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = UrbexColors.Surface,
            title = { Text("Новый объект", color = UrbexColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (error != null) Text(error!!, color = UrbexColors.Danger, fontSize = 12.sp)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(value = latText, onValueChange = { latText = it }, label = { Text("Широта") }, singleLine = true)
                    OutlinedTextField(value = lonText, onValueChange = { lonText = it }, label = { Text("Долгота") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Описание") }, minLines = 2)
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                    onClick = {
                        val lat = latText.replace(',', '.').toDoubleOrNull()
                        val lon = lonText.replace(',', '.').toDoubleOrNull()
                        if (title.trim().length < 2 || lat == null || lon == null || !GeoUtils.isValidCoordinate(lat, lon)) {
                            error = "Проверьте название и корректность координат."
                            return@Button
                        }
                        val now = System.currentTimeMillis()
                        scope.launch {
                            objectRepository.upsertObject(
                                UrbexObject(
                                    id = "obj-${now.toString(36)}",
                                    title = title.trim(),
                                    category = ObjectCategory.INDUSTRIAL,
                                    status = ReliabilityStatus.UNVERIFIED,
                                    lat = lat,
                                    lon = lon,
                                    elevationM = 140,
                                    description = desc.trim().ifEmpty { "Полевая запись." },
                                    accessNotes = "Уточняется на местности.",
                                    hazards = emptyList(),
                                    createdAt = now,
                                    updatedAt = now,
                                    authorNodeId = "!a48f20c1"
                                )
                            )
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Сохранить", color = UrbexColors.Background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Отмена", color = UrbexColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun FilterChipButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Surface,
        animationSpec = tween(160),
        label = "filterChipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
        animationSpec = tween(160),
        label = "filterChipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
        animationSpec = tween(160),
        label = "filterChipText"
    )
    Text(
        text = label,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .urbexBounceClick(0.95f, onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

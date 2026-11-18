package com.urbext.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.backup.ParseUrbexDbResult
import com.urbext.core.backup.UrbexDbSerializer
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.ConflictStrategy
import com.urbext.domain.model.CoordinateFormat
import com.urbext.domain.model.MapLayerMode
import com.urbext.domain.model.ObjectsViewMode
import com.urbext.domain.model.PowerProfile
import com.urbext.domain.model.UrbexDbBundle
import com.urbext.domain.repository.BackupRepository
import com.urbext.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    backupRepository: BackupRepository,
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    var statusBanner by remember { mutableStateOf<String?>(null) }
    var errorBanner by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<UrbexDbBundle?>(null) }
    var selectedStrategy by remember { mutableStateOf(ConflictStrategy.KEEP_NEWER) }
    var confirmResetOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = backupRepository.exportPayload()
            val raw = UrbexDbSerializer.serialize(payload, payload.profile.nodeId)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(raw.toByteArray(Charsets.UTF_8))
                }
            }
            errorBanner = null
            statusBanner = "Резервная копия .urbexdb сохранена."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            if (raw == null) {
                errorBanner = "Не удалось прочитать файл."
                return@launch
            }
            when (val res = UrbexDbSerializer.parseAndVerify(raw)) {
                is ParseUrbexDbResult.Invalid -> {
                    errorBanner = res.error
                    statusBanner = null
                }
                is ParseUrbexDbResult.Valid -> {
                    errorBanner = null
                    pendingImport = res.bundle
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UrbexColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Модуль связи Meshtastic", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Включить Meshtastic", color = UrbexColors.TextPrimary, fontSize = 14.sp)
                Text("Активирует каналы, поиск LoRa-узлов по USB/BLE и ретрансляцию пакетов", color = UrbexColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = settings.meshEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsRepository.updateSettings(
                            settings.copy(meshEnabled = enabled, updatedAt = System.currentTimeMillis())
                        )
                    }
                }
            )
        }

        Text("Профиль энергопотребления GPS", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        PowerProfile.entries.forEach { profile ->
            val selected = settings.powerProfile == profile
            val bgColor by animateColorAsState(
                targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Surface,
                animationSpec = tween(160),
                label = "powerBg"
            )
            val borderColor by animateColorAsState(
                targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
                animationSpec = tween(160),
                label = "powerBorder"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor, RoundedCornerShape(10.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .urbexBounceClick(0.98f) {
                        scope.launch {
                            settingsRepository.updateSettings(
                                settings.copy(
                                    powerProfile = profile,
                                    gpsIntervalMs = profile.intervalMs,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    .padding(12.dp)
            ) {
                Text(profile.label, color = UrbexColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(profile.summary, color = UrbexColors.TextSecondary, fontSize = 12.sp)
            }
        }

        Text("Формат координат", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        CoordinateFormat.entries.forEach { fmt ->
            val selected = settings.coordinateFormat == fmt
            val bgColor by animateColorAsState(
                targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Surface,
                animationSpec = tween(160),
                label = "fmtBg"
            )
            val borderColor by animateColorAsState(
                targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
                animationSpec = tween(160),
                label = "fmtBorder"
            )
            val fmtTextColor by animateColorAsState(
                targetValue = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                animationSpec = tween(160),
                label = "fmtText"
            )
            Text(
                text = fmt.label,
                color = fmtTextColor,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor, RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .urbexBounceClick(0.98f) {
                        scope.launch {
                            settingsRepository.updateSettings(
                                settings.copy(coordinateFormat = fmt, updatedAt = System.currentTimeMillis())
                            )
                        }
                    }
                    .padding(12.dp)
            )
        }

        Text("Слой карты", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapLayerMode.entries.forEach { layer ->
                val selected = settings.mapLayer == layer
                val layerBg by animateColorAsState(
                    targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Surface,
                    animationSpec = tween(160),
                    label = "layerBg"
                )
                val layerBorder by animateColorAsState(
                    targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
                    animationSpec = tween(160),
                    label = "layerBorder"
                )
                val layerText by animateColorAsState(
                    targetValue = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                    animationSpec = tween(160),
                    label = "layerText"
                )
                Text(
                    text = layer.label,
                    color = layerText,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .background(layerBg, RoundedCornerShape(8.dp))
                        .border(1.dp, layerBorder, RoundedCornerShape(8.dp))
                        .urbexBounceClick(0.97f) {
                            scope.launch {
                                settingsRepository.updateSettings(
                                    settings.copy(mapLayer = layer, updatedAt = System.currentTimeMillis())
                                )
                            }
                        }
                        .padding(12.dp)
                )
            }
        }

        Text("Пагинация списков", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(8, 12, 24, 50).forEach { count ->
                val selected = settings.pageSize == count
                val pageBg by animateColorAsState(
                    targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Surface,
                    animationSpec = tween(160),
                    label = "pageBg"
                )
                val pageBorder by animateColorAsState(
                    targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
                    animationSpec = tween(160),
                    label = "pageBorder"
                )
                Text(
                    text = "$count",
                    color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .background(pageBg, RoundedCornerShape(8.dp))
                        .border(1.dp, pageBorder, RoundedCornerShape(8.dp))
                        .urbexBounceClick(0.96f) {
                            scope.launch {
                                settingsRepository.updateSettings(
                                    settings.copy(pageSize = count, updatedAt = System.currentTimeMillis())
                                )
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 12.dp)
                )
            }
        }

        Text("Вид объектов по умолчанию", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ObjectsViewMode.entries.forEach { mode ->
                val selected = settings.defaultObjectsView == mode
                Text(
                    text = mode.label,
                    color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) UrbexColors.Elevated else UrbexColors.Surface, RoundedCornerShape(8.dp))
                        .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                        .urbexBounceClick(0.96f) {
                            scope.launch {
                                settingsRepository.updateSettings(
                                    settings.copy(defaultObjectsView = mode, updatedAt = System.currentTimeMillis())
                                )
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 10.dp)
                )
            }
        }

        Text("Радиус круговой зоны по умолчанию", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 80, 150, 300).forEach { radius ->
                val selected = settings.defaultZoneRadiusM == radius
                Text(
                    text = "$radius м",
                    color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) UrbexColors.Elevated else UrbexColors.Surface, RoundedCornerShape(8.dp))
                        .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                        .urbexBounceClick(0.96f) {
                            scope.launch {
                                settingsRepository.updateSettings(
                                    settings.copy(defaultZoneRadiusM = radius, updatedAt = System.currentTimeMillis())
                                )
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 10.dp)
                )
            }
        }

        Text("Отображение карты, зон и экрана", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

        SettingsToggleRow(
            title = "Показывать зоны объектов на карте",
            subtitle = "Полигоны периметров и выбор объекта касанием внутри зоны",
            checked = settings.showObjectZones,
            onCheckedChange = { enabled ->
                scope.launch {
                    settingsRepository.updateSettings(
                        settings.copy(showObjectZones = enabled, updatedAt = System.currentTimeMillis())
                    )
                }
            }
        )

        SettingsToggleRow(
            title = "Подписи объектов на карте",
            subtitle = "При выключении остаются только цветные маркеры без плашек",
            checked = settings.showObjectLabels,
            onCheckedChange = { enabled ->
                scope.launch {
                    settingsRepository.updateSettings(
                        settings.copy(showObjectLabels = enabled, updatedAt = System.currentTimeMillis())
                    )
                }
            }
        )

        SettingsToggleRow(
            title = "Автослежение по GPS на карте",
            subtitle = "Карта автоматически смещается за текущей позицией при новом фиксе",
            checked = settings.autoCenterGps,
            onCheckedChange = { enabled ->
                scope.launch {
                    settingsRepository.updateSettings(
                        settings.copy(autoCenterGps = enabled, updatedAt = System.currentTimeMillis())
                    )
                }
            }
        )

        SettingsToggleRow(
            title = "Не выключать экран на карте",
            subtitle = "Удерживает подсветку экрана активной при просмотре карты",
            checked = settings.keepScreenAwake,
            onCheckedChange = { enabled ->
                scope.launch {
                    settingsRepository.updateSettings(
                        settings.copy(keepScreenAwake = enabled, updatedAt = System.currentTimeMillis())
                    )
                }
            }
        )

        SettingsToggleRow(
            title = "Режим экономии RAM",
            subtitle = "Ограничивает кэш миниатюр и отключает декоративные изолинии",
            checked = settings.lowMemoryMode,
            onCheckedChange = { enabled ->
                scope.launch {
                    settingsRepository.updateSettings(
                        settings.copy(lowMemoryMode = enabled, updatedAt = System.currentTimeMillis())
                    )
                }
            }
        )

        // Cached Offline Sectors Section
        Text(
            text = "Кэшированные офлайн-секторы (${settings.offlineSectors.size})",
            color = UrbexColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (settings.offlineSectors.isEmpty()) {
            Text(
                text = "Нет сохранённых секторов карты.",
                color = UrbexColors.TextSecondary,
                fontSize = 12.sp
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                settings.offlineSectors.forEach { sec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sec.name, color = UrbexColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "R=${sec.radiusKm} км · Z${sec.zoomMin}–${sec.zoomMax} · ${sec.sizeMb} МБ",
                                color = UrbexColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    settingsRepository.updateSettings(
                                        settings.copy(
                                            offlineSectors = settings.offlineSectors.filterNot { it.id == sec.id },
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить сектор", tint = UrbexColors.Danger)
                        }
                    }
                }
            }
        }

        // .urbexdb Backup Section
        Text("Резервное копирование (.urbexdb)", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val exportInteraction = remember { MutableInteractionSource() }
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                onClick = { exportLauncher.launch("urbex-backup.urbexdb") },
                interactionSource = exportInteraction,
                modifier = Modifier
                    .weight(1f)
                    .urbexScaleOnPress(exportInteraction, 0.96f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(16.dp))
                Text(" Экспорт", color = UrbexColors.Background)
            }
            val importInteraction = remember { MutableInteractionSource() }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                interactionSource = importInteraction,
                modifier = Modifier
                    .weight(1f)
                    .urbexScaleOnPress(importInteraction, 0.96f)
            ) {
                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Импорт")
            }
        }

        AnimatedVisibility(visible = errorBanner != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Text(errorBanner.orEmpty(), color = UrbexColors.Danger, fontSize = 12.sp)
        }
        AnimatedVisibility(visible = statusBanner != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Text(statusBanner.orEmpty(), color = UrbexColors.GreenBright, fontSize = 12.sp)
        }

        // Reset & Recovery Section
        Text("Сброс и восстановление", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Возвращает локальную базу к исходному полевому набору сектора (при сбоях или очистке кэша).",
            color = UrbexColors.TextSecondary,
            fontSize = 12.sp
        )
        if (!confirmResetOpen) {
            OutlinedButton(
                onClick = { confirmResetOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFF1ACA7), modifier = Modifier.size(16.dp))
                Text(" Сбросить базу к исходной", color = Color(0xFFF1ACA7), fontSize = 12.sp)
            }
        }
        AnimatedVisibility(
            visible = confirmResetOpen,
            enter = fadeIn(tween(150)) + expandVertically(tween(150)),
            exit = fadeOut(tween(130)) + shrinkVertically(tween(130))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UrbexColors.DangerSoft, RoundedCornerShape(10.dp))
                    .border(1.dp, UrbexColors.Danger, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Подтвердить полный сброс?", color = Color(0xFFF1ACA7), fontSize = 12.sp, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Отмена",
                        color = UrbexColors.TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(UrbexColors.Elevated, RoundedCornerShape(6.dp))
                            .urbexBounceClick(0.95f) { confirmResetOpen = false }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Text(
                        text = "Сбросить",
                        color = UrbexColors.Background,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(UrbexColors.Danger, RoundedCornerShape(6.dp))
                            .urbexBounceClick(0.95f) {
                                scope.launch {
                                    backupRepository.resetToSeed()
                                    confirmResetOpen = false
                                    errorBanner = null
                                    statusBanner = "Локальная база восстановлена к исходному состоянию."
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    pendingImport?.let { bundle ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = UrbexColors.Surface,
            title = { Text("Контроль целостности пройден", color = UrbexColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Формат: ${bundle.manifest.format} v${bundle.manifest.version}\n" +
                            "Узел: ${bundle.manifest.deviceNodeId}\n" +
                            "Сумма: ${bundle.manifest.checksum}\n" +
                            "Состав: ${bundle.manifest.counts.objects} объектов, ${bundle.manifest.counts.archiveItems} архив, ${bundle.manifest.counts.meshMessages} пакетов",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = UrbexColors.TextSecondary
                    )
                    Text("Стратегия конфликтов:", color = UrbexColors.TextPrimary, fontSize = 13.sp)
                    ConflictStrategy.entries.forEach { strategy ->
                        val label = when (strategy) {
                            ConflictStrategy.KEEP_NEWER -> "Оставить новее"
                            ConflictStrategy.SKIP_EXISTING -> "Пропустить существующие"
                            ConflictStrategy.OVERWRITE -> "Перезаписать"
                        }
                        val isStrategySelected = selectedStrategy == strategy
                        val strategyBorder by animateColorAsState(
                            targetValue = if (isStrategySelected) UrbexColors.Green else UrbexColors.Border,
                            animationSpec = tween(160),
                            label = "strategyBorder"
                        )
                        val strategyText by animateColorAsState(
                            targetValue = if (isStrategySelected) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                            animationSpec = tween(160),
                            label = "strategyText"
                        )
                        Text(
                            text = label,
                            color = strategyText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(UrbexColors.Background, RoundedCornerShape(6.dp))
                                .border(1.dp, strategyBorder, RoundedCornerShape(6.dp))
                                .urbexBounceClick(0.98f) { selectedStrategy = strategy }
                                .padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                    onClick = {
                        scope.launch {
                            val stats = backupRepository.importBundle(bundle, selectedStrategy)
                            pendingImport = null
                            statusBanner = "Импорт завершён: +${stats.added}, обновлено ${stats.updated}, пропущено ${stats.skipped}"
                        }
                    }
                ) {
                    Text("Применить", color = UrbexColors.Background)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text("Отмена", color = UrbexColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = UrbexColors.TextPrimary, fontSize = 14.sp)
            Text(subtitle, color = UrbexColors.TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

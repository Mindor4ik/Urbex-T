package com.urbext.feature.settings

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.backup.ParseUrbexDbResult
import com.urbext.core.backup.UrbexDbSerializer
import com.urbext.core.ui.DatabaseTemplateDialog
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.ConflictStrategy
import com.urbext.domain.model.CoordinateFormat
import com.urbext.domain.model.MapLayerMode
import com.urbext.domain.model.ObjectsViewMode
import com.urbext.domain.model.PdaOrientation
import com.urbext.domain.model.PowerProfile
import com.urbext.domain.model.UrbexDbBundle
import com.urbext.domain.model.WorkspaceState
import com.urbext.domain.repository.BackupRepository
import com.urbext.domain.repository.SettingsRepository
import com.urbext.domain.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    backupRepository: BackupRepository,
    workspaceRepository: WorkspaceRepository? = null,
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val twoColLandscape = isLandscape && cfg.screenWidthDp >= 560
    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val workspaceState by (workspaceRepository?.observeWorkspaces() ?: flowOf(WorkspaceState()))
        .collectAsStateWithLifecycle(initialValue = WorkspaceState())

    var statusBanner by remember { mutableStateOf<String?>(null) }
    var errorBanner by remember { mutableStateOf<String?>(null) }
    var dbMessage by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<UrbexDbBundle?>(null) }
    var importWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var importTargetMode by remember { mutableStateOf("merge") } // "merge" | "new_db"
    var importNewDbName by remember { mutableStateOf("Импортированная база") }
    var selectedStrategy by remember { mutableStateOf(ConflictStrategy.KEEP_NEWER) }
    var confirmResetOpen by remember { mutableStateOf(false) }

    // Database Manager local form state
    var showCreateDbForm by remember { mutableStateOf(false) }
    var newDbName by remember { mutableStateOf("") }
    var newDbRegion by remember { mutableStateOf("") }
    var newDbCopySeed by remember { mutableStateOf(false) }
    var renamingDbId by remember { mutableStateOf<String?>(null) }
    var renameDbName by remember { mutableStateOf("") }
    var renameDbRegion by remember { mutableStateOf("") }
    var confirmDeleteDbId by remember { mutableStateOf<String?>(null) }
    var pendingExportDbId by remember { mutableStateOf<String?>(null) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val exportTarget = pendingExportDbId
        pendingExportDbId = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = workspaceRepository?.exportDatabasePayload(exportTarget)
                ?: backupRepository.exportPayload()
            val raw = UrbexDbSerializer.serialize(payload, payload.profile.nodeId)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(raw.toByteArray(Charsets.UTF_8))
                }
            }
            errorBanner = null
            statusBanner = "Резервная копия .urbexdb сформирована и сохранена."
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
                    importWarnings = res.warnings
                    importNewDbName = "БД ${res.bundle.manifest.deviceNodeId}"
                    importTargetMode = if (workspaceRepository != null) "new_db" else "merge"
                    pendingImport = res.bundle
                }
            }
        }
    }

    val leftCards: @Composable () -> Unit = {
        // 0. Editor Mode Gate Card
        SettingsSectionCard(
            title = "Режим редактора",
            icon = Icons.Default.Edit,
            iconTint = UrbexColors.Amber,
            compact = isLandscape
        ) {
            SettingsToggleRow(
                title = "Включить режим редактора",
                subtitle = "Открывает кнопки создания, редактирования и удаления карточек объектов, зон на карте, записей базы знаний и файлов архива. При выключении терминал работает только на чтение.",
                checked = settings.editorEnabled,
                compact = isLandscape,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsRepository.updateSettings(
                            settings.copy(editorEnabled = enabled, updatedAt = System.currentTimeMillis())
                        )
                    }
                }
            )
        }

        // 1. Meshtastic Section Card
        SettingsSectionCard(
            title = "Модуль связи Meshtastic",
            icon = Icons.Default.Radio,
            iconTint = UrbexColors.GreenBright,
            compact = isLandscape
        ) {
            SettingsToggleRow(
                title = "Включить модуль Meshtastic",
                subtitle = "Активирует каналы связи, поиск реальных LoRa-узлов по USB/BLE и ретрансляцию пакетов. По умолчанию выключен для экономии заряда.",
                checked = settings.meshEnabled,
                compact = isLandscape,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsRepository.updateSettings(
                            settings.copy(meshEnabled = enabled, updatedAt = System.currentTimeMillis())
                        )
                    }
                }
            )
        }

        // 2. PDA Mode & Screen Orientation Card
        SettingsSectionCard(
            title = "Режим КПК / PDA и экран",
            icon = Icons.Default.Smartphone,
            iconTint = UrbexColors.Amber,
            compact = isLandscape
        ) {
            SettingsToggleRow(
                title = "Режим PDA (главный экран)",
                subtitle = "Скрывает системную строку состояния, отображает заряд и часы в шапке Urbex-T, включает лаунчер приложений устройства и позволяет выбрать Urbex-T главным экраном телефона.",
                checked = settings.pdaModeEnabled,
                compact = isLandscape,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsRepository.updateSettings(
                            settings.copy(pdaModeEnabled = enabled, updatedAt = System.currentTimeMillis())
                        )
                    }
                }
            )

            Text("Ориентация экрана PDA", color = UrbexColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PdaOrientation.entries.forEach { orient ->
                    val selected = settings.pdaOrientation == orient
                    val bgColor by animateColorAsState(
                        targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Background,
                        animationSpec = tween(160),
                        label = "orientBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
                        animationSpec = tween(160),
                        label = "orientBorder"
                    )
                    Text(
                        text = orient.label,
                        color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                        fontSize = if (isLandscape) 11.sp else 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .background(bgColor, RoundedCornerShape(8.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.96f) {
                                scope.launch {
                                    settingsRepository.updateSettings(
                                        settings.copy(pdaOrientation = orient, updatedAt = System.currentTimeMillis())
                                    )
                                }
                            }
                            .padding(vertical = if (isLandscape) 7.dp else 10.dp, horizontal = 4.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        if (!settings.pdaModeEnabled) {
                            settingsRepository.updateSettings(
                                settings.copy(pdaModeEnabled = true, updatedAt = System.currentTimeMillis())
                            )
                        }
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                        }.onFailure {
                            errorBanner = "Откройте Настройки Android -> Приложения по умолчанию -> Главный экран."
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 34.dp else 40.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(15.dp))
                Text(" Выбрать Urbex-T главным экраном", color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        // 3. Power, Map & GPS Section Card
        SettingsSectionCard(
            title = "Энергопотребление, карта и GPS",
            icon = Icons.Default.BatteryChargingFull,
            iconTint = UrbexColors.GreenBright,
            compact = isLandscape
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PowerProfile.entries.chunked(2).forEach { rowProfiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowProfiles.forEach { profile ->
                            val selected = settings.powerProfile == profile
                            val bgColor by animateColorAsState(
                                targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Background,
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
                                    .weight(1f)
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
                                    .padding(if (isLandscape) 8.dp else 10.dp)
                            ) {
                                Text(profile.label, color = UrbexColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                Text(profile.summary, color = UrbexColors.TextSecondary, fontSize = 10.sp, lineHeight = 13.sp)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = UrbexColors.Border)

            Text("Слой карты", color = UrbexColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MapLayerMode.entries.forEach { layer ->
                    val selected = settings.mapLayer == layer
                    Text(
                        text = layer.label,
                        color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selected) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(8.dp))
                            .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.97f) {
                                scope.launch {
                                    settingsRepository.updateSettings(
                                        settings.copy(mapLayer = layer, updatedAt = System.currentTimeMillis())
                                    )
                                }
                            }
                            .padding(if (isLandscape) 8.dp else 11.dp)
                    )
                }
            }

            Text("Формат координат", color = UrbexColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                CoordinateFormat.entries.forEach { fmt ->
                    val selected = settings.coordinateFormat == fmt
                    Text(
                        text = fmt.label,
                        color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(8.dp))
                            .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.98f) {
                                scope.launch {
                                    settingsRepository.updateSettings(
                                        settings.copy(coordinateFormat = fmt, updatedAt = System.currentTimeMillis())
                                    )
                                }
                            }
                            .padding(if (isLandscape) 8.dp else 11.dp)
                    )
                }
            }

            Text("Пагинация списков", color = UrbexColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(8, 12, 24, 50).forEach { count ->
                    val selected = settings.pageSize == count
                    Text(
                        text = "$count",
                        color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selected) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(8.dp))
                            .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.96f) {
                                scope.launch {
                                    settingsRepository.updateSettings(
                                        settings.copy(pageSize = count, updatedAt = System.currentTimeMillis())
                                    )
                                }
                            }
                            .padding(vertical = if (isLandscape) 7.dp else 10.dp, horizontal = 6.dp)
                    )
                }
            }

            Text("Вид объектов по умолчанию", color = UrbexColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ObjectsViewMode.entries.forEach { mode ->
                    val selected = settings.defaultObjectsView == mode
                    Text(
                        text = mode.label,
                        color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selected) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(8.dp))
                            .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.96f) {
                                scope.launch {
                                    settingsRepository.updateSettings(
                                        settings.copy(defaultObjectsView = mode, updatedAt = System.currentTimeMillis())
                                    )
                                }
                            }
                            .padding(vertical = if (isLandscape) 7.dp else 10.dp, horizontal = 6.dp)
                    )
                }
            }

            HorizontalDivider(color = UrbexColors.Border)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(15.dp))
                Text("Отображение карты, зон и экрана", color = UrbexColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Text("Радиус круговой зоны по умолчанию", color = UrbexColors.TextSecondary, fontSize = 11.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 80, 150, 300).forEach { radius ->
                    val selected = settings.defaultZoneRadiusM == radius
                    Text(
                        text = "$radius м",
                        color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selected) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(8.dp))
                            .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.96f) {
                                scope.launch {
                                    settingsRepository.updateSettings(
                                        settings.copy(defaultZoneRadiusM = radius, updatedAt = System.currentTimeMillis())
                                    )
                                }
                            }
                            .padding(vertical = if (isLandscape) 7.dp else 10.dp, horizontal = 4.dp)
                    )
                }
            }

            SettingsToggleRow(
                title = "Показывать зоны объектов на карте",
                subtitle = "Отображает размеченные полигоны периметров и позволяет выбирать объект касанием внутри зоны",
                checked = settings.showObjectZones,
                compact = isLandscape,
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
                subtitle = "При выключении остаются только цветные маркеры без текстовых плашек",
                checked = settings.showObjectLabels,
                compact = isLandscape,
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
                subtitle = "Карта автоматически смещается за текущей позицией при получении нового фикса",
                checked = settings.autoCenterGps,
                compact = isLandscape,
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
                subtitle = "Удерживает экран активным во время полевой разведки (Wake Lock)",
                checked = settings.keepScreenAwake,
                compact = isLandscape,
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
                subtitle = "Ограничивает кэш миниатюр и отключает декоративные изолинии при низкой памяти",
                checked = settings.lowMemoryMode,
                compact = isLandscape,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsRepository.updateSettings(
                            settings.copy(lowMemoryMode = enabled, updatedAt = System.currentTimeMillis())
                        )
                    }
                }
            )
        }
    }

    val rightCards: @Composable () -> Unit = {
        // 4. Cached Offline Sectors Section Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(if (isLandscape) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Кэшированные офлайн-секторы (${settings.offlineSectors.size})",
                color = UrbexColors.TextPrimary,
                fontSize = if (isLandscape) 14.sp else 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (settings.offlineSectors.isEmpty()) {
                Text(
                    text = "Нет сохранённых секторов карты.",
                    color = UrbexColors.TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                settings.offlineSectors.forEach { sec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = if (isLandscape) 6.dp else 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sec.name, color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "R=${sec.radiusKm} км · Z${sec.zoomMin}–${sec.zoomMax} · ${sec.sizeMb} МБ",
                                color = UrbexColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
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

        // 5. .urbexdb Backup Section Card
        SettingsSectionCard(
            title = "Резервное копирование (.urbexdb)",
            icon = Icons.Default.Storage,
            iconTint = UrbexColors.Amber,
            compact = isLandscape
        ) {
            Text(
                text = "Экспорт и восстановление полной автономной базы (объекты, сообщения Mesh, база знаний, архив, профиль и настройки) с проверкой версии формата и контрольной суммы целостности.",
                color = UrbexColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val exportInteraction = remember { MutableInteractionSource() }
                Button(
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                    onClick = { exportLauncher.launch("urbex-backup.urbexdb") },
                    interactionSource = exportInteraction,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isLandscape) 36.dp else 40.dp)
                        .urbexScaleOnPress(exportInteraction, 0.96f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Экспорт", color = UrbexColors.Background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
                val importInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    shape = RoundedCornerShape(9.dp),
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    interactionSource = importInteraction,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isLandscape) 36.dp else 40.dp)
                        .urbexScaleOnPress(importInteraction, 0.96f)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Импорт", color = UrbexColors.TextPrimary, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }

            AnimatedVisibility(visible = errorBanner != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
                Text(errorBanner.orEmpty(), color = UrbexColors.Danger, fontSize = 12.sp)
            }
            AnimatedVisibility(visible = statusBanner != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
                Text(statusBanner.orEmpty(), color = UrbexColors.GreenBright, fontSize = 12.sp)
            }
        }

        // 6. Reset & Recovery Section Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(if (isLandscape) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Сброс и восстановление", color = UrbexColors.TextPrimary, fontSize = if (isLandscape) 14.sp else 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Возвращает локальную базу к исходному полевому набору сектора (при сбоях или очистке кэша).",
                color = UrbexColors.TextSecondary,
                fontSize = 12.sp
            )
            if (!confirmResetOpen) {
                OutlinedButton(
                    shape = RoundedCornerShape(9.dp),
                    onClick = { confirmResetOpen = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 36.dp else 40.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFF1ACA7), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Сбросить базу к исходной", color = Color(0xFFF1ACA7), fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
                        .padding(10.dp),
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
    }

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
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 14.dp)
    ) {
        // 0. Full-width Regional Database Manager Card
        if (workspaceRepository != null) {
            SettingsSectionCard(
                title = "Менеджер баз данных (регионы и секторы)",
                icon = Icons.Default.Layers,
                iconTint = UrbexColors.GreenBright,
                compact = isLandscape
            ) {
                Text(
                    text = "Создавайте отдельные базы по городам и секторам (Москва, Тюмень и др.), переключайтесь между ними или подключайте несколько сразу («В вид») для совместного отображения на карте и в реестре.",
                    color = UrbexColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        shape = RoundedCornerShape(9.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        onClick = { showCreateDbForm = !showCreateDbForm }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Новая БД", color = UrbexColors.Background, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        onClick = { showTemplateDialog = true }
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Схема и шаблон", color = UrbexColors.TextPrimary, fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }

                AnimatedVisibility(
                    visible = showCreateDbForm,
                    enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UrbexCompactInputField(
                            value = newDbName,
                            onValueChange = { newDbName = it },
                            label = "Название базы",
                            placeholder = "Например: Тюмень · Сектор-1",
                            minHeight = 34.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        UrbexCompactInputField(
                            value = newDbRegion,
                            onValueChange = { newDbRegion = it },
                            label = "Регион / город",
                            placeholder = "Например: Тюменская область",
                            minHeight = 34.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Пустая база",
                                color = if (!newDbCopySeed) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (!newDbCopySeed) UrbexColors.Elevated else UrbexColors.Surface, RoundedCornerShape(7.dp))
                                    .border(1.dp, if (!newDbCopySeed) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(7.dp))
                                    .urbexBounceClick(0.96f) { newDbCopySeed = false }
                                    .padding(vertical = 6.dp)
                            )
                            Text(
                                text = "Со стартовым набором",
                                color = if (newDbCopySeed) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (newDbCopySeed) UrbexColors.Elevated else UrbexColors.Surface, RoundedCornerShape(7.dp))
                                    .border(1.dp, if (newDbCopySeed) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(7.dp))
                                    .urbexBounceClick(0.96f) { newDbCopySeed = true }
                                    .padding(vertical = 6.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showCreateDbForm = false },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Отмена", color = UrbexColors.TextSecondary, fontSize = 11.sp)
                            }
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp),
                                onClick = {
                                    if (newDbName.trim().length < 2) {
                                        dbMessage = "Укажите название новой базы (минимум 2 символа)."
                                        return@Button
                                    }
                                    scope.launch {
                                        val created = workspaceRepository.createDatabase(
                                            name = newDbName,
                                            regionLabel = newDbRegion,
                                            copySeed = newDbCopySeed,
                                            activateImmediately = true
                                        )
                                        if (created != null) {
                                            dbMessage = "Создана и активирована БД «${created.name}»."
                                            newDbName = ""
                                            newDbRegion = ""
                                            showCreateDbForm = false
                                        }
                                    }
                                }
                            ) {
                                Text("Создать БД", color = UrbexColors.Background, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = dbMessage != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
                    Text(dbMessage.orEmpty(), color = UrbexColors.GreenBright, fontSize = 11.sp)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    workspaceState.workspaces.forEach { summary ->
                        val meta = summary.meta
                        val isActive = workspaceState.activeDbId == meta.id
                        val isMounted = meta.id in workspaceState.mountedDbIds

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isActive) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    if (isActive) UrbexColors.Green else if (isMounted) UrbexColors.Amber.copy(alpha = 0.55f) else UrbexColors.Border,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (renamingDbId == meta.id) {
                                UrbexCompactInputField(
                                    value = renameDbName,
                                    onValueChange = { renameDbName = it },
                                    label = "Название",
                                    minHeight = 32.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                UrbexCompactInputField(
                                    value = renameDbRegion,
                                    onValueChange = { renameDbRegion = it },
                                    label = "Регион",
                                    minHeight = 32.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = { renamingDbId = null },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Отмена", color = UrbexColors.TextSecondary, fontSize = 11.sp)
                                    }
                                    Button(
                                        colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp),
                                        onClick = {
                                            scope.launch {
                                                workspaceRepository.renameDatabase(meta.id, renameDbName, renameDbRegion)
                                                renamingDbId = null
                                            }
                                        }
                                    ) {
                                        Text("Сохранить", color = UrbexColors.Background, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = meta.name,
                                                color = UrbexColors.TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isActive) {
                                                Text(
                                                    text = "Активна",
                                                    color = UrbexColors.GreenBright,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier
                                                        .background(UrbexColors.GreenSoft, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${meta.regionLabel} · Объекты: ${summary.counts.objects} · Архив: ${summary.counts.archiveItems} · База знаний: ${summary.counts.baseArticles}",
                                            color = UrbexColors.TextSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .background(if (isMounted) UrbexColors.Amber.copy(alpha = 0.14f) else UrbexColors.Surface, RoundedCornerShape(7.dp))
                                            .border(1.dp, if (isMounted) UrbexColors.Amber else UrbexColors.Border, RoundedCornerShape(7.dp))
                                            .urbexBounceClick(0.95f) {
                                                scope.launch { workspaceRepository.toggleMountDatabase(meta.id) }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        if (isMounted) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(12.dp))
                                        }
                                        Text(
                                            text = if (isMounted) "В вид" else "Подключить",
                                            color = if (isMounted) UrbexColors.Amber else UrbexColors.TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isMounted) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    if (!isActive) {
                                        Text(
                                            text = "Переключить",
                                            color = UrbexColors.Background,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier
                                                .background(UrbexColors.Green, RoundedCornerShape(7.dp))
                                                .urbexBounceClick(0.95f) {
                                                    scope.launch {
                                                        workspaceRepository.switchDatabase(meta.id, preserveMultiMount = false)
                                                        dbMessage = "Активная база переключена на «${meta.name}»."
                                                    }
                                                }
                                                .padding(horizontal = 9.dp, vertical = 5.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            renamingDbId = meta.id
                                            renameDbName = meta.name
                                            renameDbRegion = meta.regionLabel
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Переименовать", tint = UrbexColors.TextSecondary, modifier = Modifier.size(15.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            pendingExportDbId = meta.id
                                            exportLauncher.launch("${meta.name}.urbexdb")
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = "Экспорт БД", tint = UrbexColors.GreenBright, modifier = Modifier.size(15.dp))
                                    }

                                    if (workspaceState.workspaces.size > 1) {
                                        IconButton(
                                            onClick = { confirmDeleteDbId = if (confirmDeleteDbId == meta.id) null else meta.id },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Удалить БД", tint = UrbexColors.Danger, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }

                                if (confirmDeleteDbId == meta.id) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(UrbexColors.DangerSoft, RoundedCornerShape(7.dp))
                                            .border(1.dp, UrbexColors.Danger.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Удалить БД «${meta.name}»?", color = Color(0xFFF1ACA7), fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "Отмена",
                                                color = UrbexColors.TextPrimary,
                                                fontSize = 11.sp,
                                                modifier = Modifier
                                                    .background(UrbexColors.Elevated, RoundedCornerShape(6.dp))
                                                    .urbexBounceClick(0.95f) { confirmDeleteDbId = null }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                            Text(
                                                text = "Удалить",
                                                color = UrbexColors.Background,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier
                                                    .background(UrbexColors.Danger, RoundedCornerShape(6.dp))
                                                    .urbexBounceClick(0.95f) {
                                                        scope.launch {
                                                            workspaceRepository.deleteDatabase(meta.id)
                                                            confirmDeleteDbId = null
                                                        }
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (twoColLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    leftCards()
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rightCards()
                }
            }
        } else {
            leftCards()
            rightCards()
        }
    }

    if (showTemplateDialog) {
        DatabaseTemplateDialog(
            onDismiss = { showTemplateDialog = false },
            onCreateFromTemplate = if (workspaceRepository != null) {
                { bundle ->
                    scope.launch {
                        val created = workspaceRepository.importIntoNewDatabase(
                            name = "Тюмень · Шаблон",
                            regionLabel = "Тюменская область",
                            bundle = bundle
                        )
                        dbMessage = "Создана база «${created.name}» из ручного шаблона (${bundle.payload.objects.size} объект)."
                    }
                }
            } else {
                null
            }
        )
    }

    pendingImport?.let { bundle ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = UrbexColors.Surface,
            title = { Text("Контроль целостности пройден", color = UrbexColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Формат: ${bundle.manifest.format} v${bundle.manifest.version}\n" +
                            "Узел: ${bundle.manifest.deviceNodeId}\n" +
                            "Сумма: ${bundle.manifest.checksum}\n" +
                            "Состав: ${bundle.manifest.counts.objects} объектов, ${bundle.manifest.counts.archiveItems} архив, ${bundle.manifest.counts.meshMessages} пакетов",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = UrbexColors.TextSecondary
                    )
                    if (importWarnings.isNotEmpty()) {
                        Text(
                            text = importWarnings.joinToString("\n"),
                            color = UrbexColors.Amber,
                            fontSize = 11.sp
                        )
                    }

                    if (workspaceRepository != null) {
                        Text("Режим импорта:", color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val isNewMode = importTargetMode == "new_db"
                            Text(
                                text = "Создать новую БД",
                                color = if (isNewMode) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isNewMode) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(7.dp))
                                    .border(1.dp, if (isNewMode) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(7.dp))
                                    .urbexBounceClick(0.96f) { importTargetMode = "new_db" }
                                    .padding(vertical = 7.dp)
                            )
                            Text(
                                text = "Объединить с активной",
                                color = if (!isNewMode) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (!isNewMode) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(7.dp))
                                    .border(1.dp, if (!isNewMode) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(7.dp))
                                    .urbexBounceClick(0.96f) { importTargetMode = "merge" }
                                    .padding(vertical = 7.dp)
                            )
                        }
                    }

                    if (importTargetMode == "new_db" && workspaceRepository != null) {
                        UrbexCompactInputField(
                            value = importNewDbName,
                            onValueChange = { importNewDbName = it },
                            label = "Название новой базы",
                            placeholder = "Например: Тюмень · Сектор А",
                            minHeight = 34.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Разрешение конфликтов по ID:", color = UrbexColors.TextPrimary, fontSize = 12.sp)
                        ConflictStrategy.entries.forEach { strategy ->
                            val label = when (strategy) {
                                ConflictStrategy.KEEP_NEWER -> "Оставлять более новую запись (по времени)"
                                ConflictStrategy.SKIP_EXISTING -> "Пропускать существующие записи"
                                ConflictStrategy.OVERWRITE -> "Перезаписать текущие записи из файла"
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
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(UrbexColors.Background, RoundedCornerShape(6.dp))
                                    .border(1.dp, strategyBorder, RoundedCornerShape(6.dp))
                                    .urbexBounceClick(0.98f) { selectedStrategy = strategy }
                                    .padding(9.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                    onClick = {
                        scope.launch {
                            if (importTargetMode == "new_db" && workspaceRepository != null) {
                                val created = workspaceRepository.importIntoNewDatabase(
                                    name = importNewDbName,
                                    regionLabel = importNewDbName,
                                    bundle = bundle
                                )
                                pendingImport = null
                                statusBanner = "Создана новая база «${created.name}» (${bundle.payload.objects.size} объектов)."
                            } else {
                                val stats = backupRepository.importBundle(bundle, selectedStrategy)
                                pendingImport = null
                                statusBanner = "Импорт завершён: добавлено ${stats.added}, обновлено ${stats.updated}, пропущено ${stats.skipped}."
                            }
                        }
                    }
                ) {
                    Text("Применить импорт", color = UrbexColors.Background, fontWeight = FontWeight.SemiBold)
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
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
            .padding(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            Text(title, color = UrbexColors.TextPrimary, fontSize = if (compact) 14.sp else 15.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    compact: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
            .padding(if (compact) 9.dp else 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = UrbexColors.TextPrimary, fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = UrbexColors.TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

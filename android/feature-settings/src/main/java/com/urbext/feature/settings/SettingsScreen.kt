package com.urbext.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.backup.ParseUrbexDbResult
import com.urbext.core.backup.UrbexDbSerializer
import com.urbext.core.ui.UrbexColors
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.ConflictStrategy
import com.urbext.domain.model.CoordinateFormat
import com.urbext.domain.model.MapLayerMode
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
    backupRepository: BackupRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    var statusBanner by remember { mutableStateOf<String?>(null) }
    var errorBanner by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<UrbexDbBundle?>(null) }
    var selectedStrategy by remember { mutableStateOf(ConflictStrategy.KEEP_NEWER) }

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                    .clickable {
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
            Text(
                text = fmt.label,
                color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor, RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .clickable {
                        scope.launch {
                            settingsRepository.updateSettings(
                                settings.copy(coordinateFormat = fmt, updatedAt = System.currentTimeMillis())
                            )
                        }
                    }
                    .padding(12.dp)
            )
        }

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
                Text("Активирует каналы и работу с LoRa-модемом", color = UrbexColors.TextSecondary, fontSize = 12.sp)
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

        Text("Слой карты", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapLayerMode.entries.forEach { layer ->
                val selected = settings.mapLayer == layer
                Text(
                    text = layer.label,
                    color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) UrbexColors.Elevated else UrbexColors.Surface, RoundedCornerShape(8.dp))
                        .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                        .clickable {
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
                Text("Режим экономии RAM", color = UrbexColors.TextPrimary, fontSize = 14.sp)
                Text("Ограничивает размер кэша изображений", color = UrbexColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = settings.lowMemoryMode,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsRepository.updateSettings(
                            settings.copy(lowMemoryMode = enabled, updatedAt = System.currentTimeMillis())
                        )
                    }
                }
            )
        }

        Text("Резервное копирование (.urbexdb)", color = UrbexColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                onClick = { exportLauncher.launch("urbex-backup.urbexdb") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Экспорт", color = UrbexColors.Background)
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Импорт")
            }
        }

        AnimatedVisibility(visible = errorBanner != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Text(errorBanner.orEmpty(), color = UrbexColors.Danger, fontSize = 12.sp)
        }
        AnimatedVisibility(visible = statusBanner != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Text(statusBanner.orEmpty(), color = UrbexColors.GreenBright, fontSize = 12.sp)
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
                        text = "Узел: ${bundle.manifest.deviceNodeId}\nСумма: ${bundle.manifest.checksum}\nОбъектов: ${bundle.manifest.counts.objects}",
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
                        Text(
                            text = label,
                            color = if (selectedStrategy == strategy) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(UrbexColors.Background, RoundedCornerShape(6.dp))
                                .border(
                                    1.dp,
                                    if (selectedStrategy == strategy) UrbexColors.Green else UrbexColors.Border,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedStrategy = strategy }
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
                            statusBanner = "Импорт: +${stats.added}, обновлено ${stats.updated}, пропущено ${stats.skipped}"
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

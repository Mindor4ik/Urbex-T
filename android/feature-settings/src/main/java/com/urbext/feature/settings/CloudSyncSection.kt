package com.urbext.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.UrbexDropdownField
import com.urbext.core.ui.UrbexDropdownOption
import com.urbext.core.ui.UrbexFilledButton
import com.urbext.core.ui.urbexGlow
import com.urbext.domain.model.CloudSyncConfig
import com.urbext.domain.model.CloudSyncProvider
import com.urbext.domain.model.CloudSyncStatus
import com.urbext.domain.model.ConflictStrategy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CloudSyncSection(
    config: CloudSyncConfig,
    hapticEnabled: Boolean,
    compact: Boolean,
    onSaveConfig: suspend (CloudSyncConfig) -> Unit,
    onSyncNow: (suspend () -> Pair<Boolean, String>)?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var endpointInput by remember(config.provider) { mutableStateOf(config.remoteEndpoint) }
    var tokenInput by remember(config.provider) { mutableStateOf(config.authToken) }
    var usernameInput by remember(config.provider) { mutableStateOf(config.username) }
    var gistIdInput by remember(config.provider, config.gistId) { mutableStateOf(config.gistId) }
    var fileNameInput by remember(config.provider) { mutableStateOf(config.remoteFileName) }
    var passphraseInput by remember(config.provider) { mutableStateOf(config.encryptionPassphrase) }

    var showToken by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var manualStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Keep external updates (e.g., folder URI pick or auto-created gistId) reflected in local text inputs
    LaunchedEffect(config.remoteEndpoint) {
        if (config.provider == CloudSyncProvider.LOCAL_FOLDER && endpointInput != config.remoteEndpoint) {
            endpointInput = config.remoteEndpoint
        }
    }

    // Debounce text input commits so typing is smooth and race-free
    LaunchedEffect(
        endpointInput,
        tokenInput,
        usernameInput,
        gistIdInput,
        fileNameInput,
        passphraseInput
    ) {
        if (
            endpointInput == config.remoteEndpoint &&
            tokenInput == config.authToken &&
            usernameInput == config.username &&
            gistIdInput == config.gistId &&
            fileNameInput == config.remoteFileName &&
            passphraseInput == config.encryptionPassphrase
        ) {
            return@LaunchedEffect
        }
        delay(320)
        onSaveConfig(
            config.copy(
                remoteEndpoint = endpointInput,
                authToken = tokenInput,
                username = usernameInput,
                gistId = gistIdInput,
                remoteFileName = fileNameInput.ifBlank { "urbex-t-sync.urbexdb" },
                encryptionPassphrase = passphraseInput
            )
        )
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        endpointInput = uri.toString()
        scope.launch {
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSaveConfig(
                config.copy(
                    provider = CloudSyncProvider.LOCAL_FOLDER,
                    remoteEndpoint = uri.toString()
                )
            )
        }
    }

    val statusIcon = when (config.lastSyncStatus) {
        CloudSyncStatus.SUCCESS -> Icons.Default.CloudDone
        CloudSyncStatus.SYNCING -> Icons.Default.CloudSync
        else -> Icons.Default.Cloud
    }

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
            Icon(statusIcon, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(17.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Бесплатная облачная автосинхронизация",
                    color = UrbexColors.TextPrimary,
                    fontSize = if (compact) 14.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "GitHub Secret Gist, WebDAV (Яндекс.Диск / Nextcloud), папка Drive/Syncthing или свой HTTP REST с E2EE.",
                    color = UrbexColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }

        // Provider selector dropdown
        UrbexDropdownField(
            selectedValue = config.provider,
            label = "Сервис автосинхронизации",
            compact = compact,
            options = remember {
                CloudSyncProvider.entries.map { provider ->
                    UrbexDropdownOption(
                        value = provider,
                        label = provider.label,
                        subtitle = provider.summary,
                        badge = provider.costLabel
                    )
                }
            },
            onSelect = { provider ->
                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    val resetEndpoint = if (
                        (provider == CloudSyncProvider.LOCAL_FOLDER && config.provider != CloudSyncProvider.LOCAL_FOLDER) ||
                        (provider != CloudSyncProvider.LOCAL_FOLDER && config.provider == CloudSyncProvider.LOCAL_FOLDER)
                    ) {
                        ""
                    } else {
                        endpointInput
                    }
                    endpointInput = resetEndpoint
                    onSaveConfig(
                        config.copy(
                            provider = provider,
                            enabled = if (provider == CloudSyncProvider.NONE) false else config.enabled,
                            remoteEndpoint = resetEndpoint
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = config.provider.summary,
            color = UrbexColors.TextSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp
        )

        if (config.provider != CloudSyncProvider.NONE) {
            // Setup instruction banner
            Text(
                text = config.provider.setupHint,
                color = UrbexColors.Amber,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .urbexGlow(UrbexColors.Amber, radius = 10.dp, cornerRadius = 8.dp, alpha = 0.14f)
                    .background(Color(0xFF1F1B12), RoundedCornerShape(8.dp))
                    .border(1.dp, UrbexColors.Amber.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(9.dp)
            )

            // Provider-specific fields
            when (config.provider) {
                CloudSyncProvider.GITHUB_GIST -> {
                    UrbexCompactInputField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = "GitHub Personal Access Token (scope: gist)",
                        placeholder = "ghp_xxxxxxxxxxxxxxxxxxxx",
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingContent = {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Показать/скрыть токен",
                                tint = UrbexColors.TextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showToken = !showToken }
                            )
                        },
                        minHeight = 34.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    UrbexCompactInputField(
                        value = gistIdInput,
                        onValueChange = { gistIdInput = it },
                        label = "Gist ID (необязательно — создастся автоматически)",
                        placeholder = "Оставьте пустым для автосоздания",
                        minHeight = 34.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                CloudSyncProvider.WEBDAV -> {
                    UrbexCompactInputField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = "WebDAV URL каталога",
                        placeholder = config.provider.endpointPlaceholder,
                        minHeight = 34.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UrbexCompactInputField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            label = "Логин",
                            placeholder = "user@yandex.ru",
                            minHeight = 34.dp,
                            modifier = Modifier.weight(1f)
                        )
                        UrbexCompactInputField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = "Пароль приложения",
                            placeholder = "••••••••",
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingContent = {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Показать/скрыть пароль",
                                    tint = UrbexColors.TextSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { showToken = !showToken }
                                )
                            },
                            minHeight = 34.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                CloudSyncProvider.LOCAL_FOLDER -> {
                    val folderLabel = remember(endpointInput) {
                        if (endpointInput.isBlank()) {
                            "Папка не выбрана"
                        } else {
                            runCatching {
                                Uri.parse(endpointInput).lastPathSegment
                                    ?.substringAfterLast(':')
                                    ?.ifBlank { "Папка выбрана" }
                            }.getOrNull() ?: "Папка выбрана"
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(9.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(9.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Папка синхронизации", color = UrbexColors.TextSecondary, fontSize = 10.sp)
                            Text(
                                text = folderLabel,
                                color = if (endpointInput.isNotBlank()) UrbexColors.GreenBright else UrbexColors.TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp),
                            onClick = { folderPicker.launch(null) }
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Выбрать", color = UrbexColors.TextPrimary, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                CloudSyncProvider.CUSTOM_HTTP -> {
                    UrbexCompactInputField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = "Endpoint URL (GET / PUT)",
                        placeholder = config.provider.endpointPlaceholder,
                        minHeight = 34.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    UrbexCompactInputField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = "Bearer Token (необязательно)",
                        placeholder = "Оставьте пустым, если сервер без ключа",
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingContent = {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Показать/скрыть токен",
                                tint = UrbexColors.TextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showToken = !showToken }
                            )
                        },
                        minHeight = 34.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                CloudSyncProvider.NONE -> Unit
            }

            if (config.provider != CloudSyncProvider.CUSTOM_HTTP) {
                UrbexCompactInputField(
                    value = fileNameInput,
                    onValueChange = { fileNameInput = it },
                    label = "Имя удалённого файла снимка",
                    placeholder = "urbex-t-sync.urbexdb",
                    minHeight = 34.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = UrbexColors.Border)

            // Sync toggles
            SyncToggleRow(
                title = "Включить автосинхронизацию по расписанию",
                subtitle = "Фоновый планировщик WorkManager с учётом энергосбережения Android",
                checked = config.enabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        onSaveConfig(config.copy(enabled = enabled))
                    }
                }
            )

            SyncToggleRow(
                title = "Синхронизация при изменении базы",
                subtitle = "Отправка объединённого снимка через 4 сек после правки объектов/заметок",
                checked = config.autoSyncOnChange,
                onCheckedChange = { checked ->
                    scope.launch {
                        onSaveConfig(config.copy(autoSyncOnChange = checked))
                    }
                }
            )

            if (config.provider != CloudSyncProvider.LOCAL_FOLDER) {
                SyncToggleRow(
                    title = "Только по Wi-Fi",
                    subtitle = "Экономия мобильного трафика в поле (рекомендуется)",
                    checked = config.wifiOnly,
                    onCheckedChange = { checked ->
                        scope.launch {
                            onSaveConfig(config.copy(wifiOnly = checked))
                        }
                    }
                )
            }

            SyncToggleRow(
                title = "Сквозное шифрование E2EE (AES-256-GCM)",
                subtitle = "Парольная фраза не покидает устройство (PBKDF2-SHA256)",
                checked = config.encryptBeforeUpload,
                onCheckedChange = { checked ->
                    scope.launch {
                        onSaveConfig(config.copy(encryptBeforeUpload = checked))
                    }
                }
            )

            if (config.encryptBeforeUpload) {
                UrbexCompactInputField(
                    value = passphraseInput,
                    onValueChange = { passphraseInput = it },
                    label = "Парольная фраза E2EE (общая для ваших устройств)",
                    placeholder = "Введите надёжный пароль шифрования",
                    leadingIcon = Icons.Default.Lock,
                    visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingContent = {
                        Icon(
                            imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Показать/скрыть пароль",
                            tint = UrbexColors.TextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { showPassphrase = !showPassphrase }
                        )
                    },
                    minHeight = 34.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Periodic interval & Conflict strategy dropdowns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UrbexDropdownField(
                    selectedValue = config.autoSyncIntervalMin,
                    label = "Периодичность",
                    compact = compact,
                    options = remember {
                        listOf(
                            UrbexDropdownOption(15, "Каждые 15 мин"),
                            UrbexDropdownOption(30, "Каждые 30 мин"),
                            UrbexDropdownOption(60, "Каждый час"),
                            UrbexDropdownOption(180, "Каждые 3 часа")
                        )
                    },
                    onSelect = { mins ->
                        scope.launch { onSaveConfig(config.copy(autoSyncIntervalMin = mins)) }
                    },
                    modifier = Modifier.weight(1f)
                )

                UrbexDropdownField(
                    selectedValue = config.conflictStrategy,
                    label = "Слияние конфликтов",
                    compact = compact,
                    options = remember {
                        listOf(
                            UrbexDropdownOption(
                                ConflictStrategy.KEEP_NEWER,
                                "Более новая запись",
                                subtitle = "Приоритет по времени правки"
                            ),
                            UrbexDropdownOption(
                                ConflictStrategy.SKIP_EXISTING,
                                "Пропускать свои",
                                subtitle = "Не затирать локальные записи"
                            ),
                            UrbexDropdownOption(
                                ConflictStrategy.OVERWRITE,
                                "Брать из облака",
                                subtitle = "Приоритет удалённого снимка"
                            )
                        )
                    },
                    onSelect = { st ->
                        scope.launch { onSaveConfig(config.copy(conflictStrategy = st)) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (onSyncNow != null) {
                UrbexFilledButton(
                    enabled = !isSyncing,
                    height = if (compact) 36.dp else 40.dp,
                    cornerRadius = 9.dp,
                    glowColor = UrbexColors.GreenBright,
                    glowRadius = 14.dp,
                    glowAlpha = 0.34f,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isSyncing = true
                            manualStatus = null
                            // Flush any pending unsaved input edits before syncing
                            onSaveConfig(
                                config.copy(
                                    remoteEndpoint = endpointInput,
                                    authToken = tokenInput,
                                    username = usernameInput,
                                    gistId = gistIdInput,
                                    remoteFileName = fileNameInput.ifBlank { "urbex-t-sync.urbexdb" },
                                    encryptionPassphrase = passphraseInput
                                )
                            )
                            val res = onSyncNow()
                            manualStatus = res
                            isSyncing = false
                        }
                    }
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSyncing) "Синхронизация..." else "Синхронизировать сейчас",
                        color = UrbexColors.Background,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            val lastTimeStr = config.lastSyncAt?.let { ts ->
                SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ts))
            }
            val statusText = manualStatus?.second ?: config.lastSyncMessage
            val isErr = (manualStatus?.first == false) ||
                (manualStatus == null && config.lastSyncStatus == CloudSyncStatus.ERROR)

            AnimatedVisibility(
                visible = !statusText.isNullOrBlank() || lastTimeStr != null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(120))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .urbexGlow(
                            color = UrbexColors.Danger,
                            radius = 10.dp,
                            cornerRadius = 8.dp,
                            alpha = 0.22f,
                            enabled = isErr
                        )
                        .background(if (isErr) UrbexColors.DangerSoft else UrbexColors.Background, RoundedCornerShape(8.dp))
                        .border(1.dp, if (isErr) UrbexColors.Danger else UrbexColors.Border, RoundedCornerShape(8.dp))
                        .padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (lastTimeStr != null) {
                        Text(
                            text = "Последняя синхронизация: $lastTimeStr",
                            color = UrbexColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                    if (!statusText.isNullOrBlank()) {
                        Text(
                            text = statusText,
                            color = if (isErr) Color(0xFFF1ACA7) else UrbexColors.GreenBright,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .urbexGlow(
                color = UrbexColors.GreenBright,
                radius = 10.dp,
                cornerRadius = 9.dp,
                alpha = 0.16f,
                enabled = checked
            )
            .background(UrbexColors.Background, RoundedCornerShape(9.dp))
            .border(1.dp, if (checked) UrbexColors.Green.copy(alpha = 0.55f) else UrbexColors.Border, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = UrbexColors.TextSecondary, fontSize = 10.sp, lineHeight = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

package com.urbext.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbext.core.backup.ParseUrbexDbResult
import com.urbext.core.backup.UrbexDbSerializer
import com.urbext.core.ui.DatabaseTemplateDialog
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.CoordinateFormat
import com.urbext.domain.model.PdaOrientation
import com.urbext.domain.model.UrbexDbBundle
import com.urbext.domain.model.UserProfile
import com.urbext.domain.repository.ProfileRepository
import com.urbext.domain.repository.SettingsRepository
import com.urbext.domain.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DatabaseWelcomeScreen(
    workspaceRepository: WorkspaceRepository,
    settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository,
    currentSettings: AppSettings,
    currentProfile: UserProfile
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    var step by rememberSaveable { mutableIntStateOf(0) }
    var callsign by rememberSaveable { mutableStateOf(currentProfile.callsign) }
    var enableEditor by rememberSaveable { mutableStateOf(true) }
    var selectedCoordFormat by rememberSaveable { mutableStateOf(currentSettings.coordinateFormat) }
    var selectedOrientation by rememberSaveable { mutableStateOf(currentSettings.pdaOrientation) }

    var dbName by rememberSaveable { mutableStateOf("Мой сектор") }
    var dbRegion by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var pendingImport by remember { mutableStateOf<UrbexDbBundle?>(null) }
    var importWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var importDbName by remember { mutableStateOf("") }
    var showTemplateDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = step > 0 && !busy) {
        errorMessage = null
        step = (step - 1).coerceAtLeast(0)
    }

    suspend fun applyOperatorPreferences() {
        val nextCallsign = callsign.trim()
        if (nextCallsign.isNotEmpty() && nextCallsign != currentProfile.callsign) {
            profileRepository.updateProfile(
                currentProfile.copy(
                    callsign = nextCallsign,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        if (
            currentSettings.editorEnabled != enableEditor ||
            currentSettings.coordinateFormat != selectedCoordFormat ||
            currentSettings.pdaOrientation != selectedOrientation
        ) {
            settingsRepository.updateSettings(
                currentSettings.copy(
                    editorEnabled = enableEditor,
                    coordinateFormat = selectedCoordFormat,
                    pdaOrientation = selectedOrientation,
                    updatedAt = System.currentTimeMillis()
                )
            )
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
            if (raw.isNullOrBlank()) {
                errorMessage = "Не удалось прочитать выбранный файл."
                return@launch
            }
            when (val res = UrbexDbSerializer.parseAndVerify(raw)) {
                is ParseUrbexDbResult.Invalid -> {
                    errorMessage = res.error
                }
                is ParseUrbexDbResult.Valid -> {
                    errorMessage = null
                    importWarnings = res.warnings
                    importDbName = "БД ${res.bundle.manifest.deviceNodeId}"
                    pendingImport = res.bundle
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UrbexColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (isLandscape) 860.dp else 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isLandscape) 18.dp else 16.dp, vertical = if (isLandscape) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top step indicator bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "URBEX-T · ПЕРВЫЙ ЗАПУСК",
                        color = UrbexColors.GreenBright,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Шаг ${step + 1} из 3",
                        color = UrbexColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(3) { idx ->
                        val active = idx <= step
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (active) UrbexColors.GreenBright else UrbexColors.Border)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(120))
            ) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = UrbexColors.Danger,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.DangerSoft, RoundedCornerShape(10.dp))
                        .border(1.dp, UrbexColors.Danger.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState >= initialState
                    val slideIn = slideInHorizontally(tween(190)) { full -> if (forward) full / 5 else -full / 5 }
                    val slideOut = slideOutHorizontally(tween(140)) { full -> if (forward) -full / 5 else full / 5 }
                    (fadeIn(tween(190)) + slideIn) togetherWith (fadeOut(tween(140)) + slideOut)
                },
                label = "OnboardingStep"
            ) { currentStep ->
                when (currentStep) {
                    0 -> WelcomeHeroStep(
                        isLandscape = isLandscape,
                        busy = busy,
                        onStartSetup = {
                            errorMessage = null
                            step = 1
                        },
                        onQuickImport = {
                            errorMessage = null
                            importLauncher.launch(arrayOf("*/*"))
                        }
                    )

                    1 -> SetupPreferencesStep(
                        isLandscape = isLandscape,
                        callsign = callsign,
                        onCallsignChange = { callsign = it },
                        enableEditor = enableEditor,
                        onToggleEditor = { enableEditor = it },
                        coordFormat = selectedCoordFormat,
                        onSelectCoordFormat = { selectedCoordFormat = it },
                        orientation = selectedOrientation,
                        onSelectOrientation = { selectedOrientation = it },
                        onBack = {
                            errorMessage = null
                            step = 0
                        },
                        onNext = {
                            errorMessage = null
                            step = 2
                        }
                    )

                    else -> DatabaseConnectStep(
                        isLandscape = isLandscape,
                        busy = busy,
                        dbName = dbName,
                        onDbNameChange = { dbName = it; errorMessage = null },
                        dbRegion = dbRegion,
                        onDbRegionChange = { dbRegion = it },
                        onBack = {
                            errorMessage = null
                            step = 1
                        },
                        onCreateEmpty = {
                            val trimmed = dbName.trim()
                            if (trimmed.length < 2) {
                                errorMessage = "Укажите название новой базы (минимум 2 символа)."
                                return@DatabaseConnectStep
                            }
                            busy = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    applyOperatorPreferences()
                                    workspaceRepository.createDatabase(
                                        name = trimmed,
                                        regionLabel = dbRegion.trim().ifEmpty { trimmed },
                                        copySeed = false,
                                        activateImmediately = true
                                    )
                                } catch (err: Exception) {
                                    errorMessage = "Ошибка создания базы: ${err.message ?: "повторите попытку"}"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        onImportFile = { importLauncher.launch(arrayOf("*/*")) },
                        onOpenTemplate = { showTemplateDialog = true },
                        onLoadDemo = {
                            busy = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    applyOperatorPreferences()
                                    workspaceRepository.createDatabase(
                                        name = "Москва · Сектор-1",
                                        regionLabel = "Москва и область",
                                        copySeed = true,
                                        activateImmediately = true
                                    )
                                } catch (err: Exception) {
                                    errorMessage = "Ошибка загрузки демо-базы: ${err.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showTemplateDialog) {
        DatabaseTemplateDialog(
            onDismiss = { showTemplateDialog = false },
            onCreateFromTemplate = { templateBundle ->
                showTemplateDialog = false
                scope.launch {
                    busy = true
                    try {
                        applyOperatorPreferences()
                        workspaceRepository.importIntoNewDatabase(
                            name = "База по шаблону",
                            regionLabel = "Локальный сектор",
                            bundle = templateBundle
                        )
                    } catch (err: Exception) {
                        errorMessage = "Не удалось создать базу из шаблона: ${err.message}"
                    } finally {
                        busy = false
                    }
                }
            }
        )
    }

    pendingImport?.let { bundle ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = UrbexColors.Surface,
            title = {
                Text("Подключить базу (.urbexdb)", color = UrbexColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Объектов: ${bundle.payload.objects.size} · База знаний: ${bundle.payload.baseArticles.size} · Архив: ${bundle.payload.archiveItems.size}",
                        color = UrbexColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    if (importWarnings.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Amber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            importWarnings.take(3).forEach { warn ->
                                Text(warn, color = UrbexColors.Amber, fontSize = 11.sp)
                            }
                        }
                    }
                    UrbexCompactInputField(
                        value = importDbName,
                        onValueChange = { importDbName = it },
                        label = "Название базы",
                        placeholder = "Например: Тюмень · Сектор А",
                        minHeight = 36.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                applyOperatorPreferences()
                                val cleanName = importDbName.trim().ifEmpty { "БД ${bundle.manifest.deviceNodeId}" }
                                workspaceRepository.importIntoNewDatabase(
                                    name = cleanName,
                                    regionLabel = cleanName,
                                    bundle = bundle
                                )
                                pendingImport = null
                            } catch (err: Exception) {
                                pendingImport = null
                                errorMessage = "Ошибка импорта: ${err.message}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("Открыть базу", color = UrbexColors.Background, fontWeight = FontWeight.SemiBold)
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
private fun WelcomeHeroStep(
    isLandscape: Boolean,
    busy: Boolean,
    onStartSetup: () -> Unit,
    onQuickImport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(14.dp))
                .border(1.dp, UrbexColors.Green.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .padding(if (isLandscape) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 42.dp else 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(UrbexColors.GreenSoft)
                        .border(1.dp, UrbexColors.Green, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = UrbexColors.GreenBright,
                        modifier = Modifier.size(if (isLandscape) 22.dp else 26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Добро пожаловать в Urbex-T!",
                        color = UrbexColors.TextPrimary,
                        fontSize = if (isLandscape) 19.sp else 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Автономный тактический КПК для карт, реестра объектов, полевого архива и связи.",
                        color = UrbexColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            val highlights = listOf(
                Triple(Icons.Default.Map, "Карты и GPS", "Офлайн-секторы и маркеры"),
                Triple(Icons.Default.Place, "Реестр и зоны", "Карточки точек и периметры"),
                Triple(Icons.Default.Book, "Архив и база", "Фото, заметки и инструкции"),
                Triple(Icons.Default.Sync, "Формат .urbexdb", "Несколько баз и E2EE облако")
            )
            val cols = if (isLandscape) 4 else 2
            highlights.chunked(cols).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { (icon, title, subtitle) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(icon, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(16.dp))
                            Text(title, color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subtitle, color = UrbexColors.TextSecondary, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Text(
                text = "Перед началом давайте за пару шагов настроим ваш позывной, формат экрана и подключим первую базу данных.",
                color = UrbexColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        Button(
            enabled = !busy,
            shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            onClick = onStartSetup
        ) {
            Text(
                text = "Начать настройку",
                color = UrbexColors.Background,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(17.dp))
        }

        OutlinedButton(
            enabled = !busy,
            shape = RoundedCornerShape(11.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            onClick = onQuickImport
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "У меня уже есть файл .urbexdb",
                color = UrbexColors.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SetupPreferencesStep(
    isLandscape: Boolean,
    callsign: String,
    onCallsignChange: (String) -> Unit,
    enableEditor: Boolean,
    onToggleEditor: (Boolean) -> Unit,
    coordFormat: CoordinateFormat,
    onSelectCoordFormat: (CoordinateFormat) -> Unit,
    orientation: PdaOrientation,
    onSelectOrientation: (PdaOrientation) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(14.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(14.dp))
                .padding(if (isLandscape) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(18.dp))
                Column {
                    Text(
                        text = "Давайте настроим терминал",
                        color = UrbexColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Задайте позывной и базовые параметры — потом их можно изменить в настройках.",
                        color = UrbexColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            UrbexCompactInputField(
                value = callsign,
                onValueChange = onCallsignChange,
                label = "Позывной оператора",
                placeholder = "Например: Сокол-1",
                minHeight = 40.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        if (enableEditor) UrbexColors.Green.copy(alpha = 0.55f) else UrbexColors.Border,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onToggleEditor(!enableEditor) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Включить редактор объектов",
                        color = UrbexColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Позволяет сразу создавать точки, зоны и записи в архиве",
                        color = UrbexColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = enableEditor,
                    onCheckedChange = onToggleEditor
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Формат координат",
                    color = UrbexColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        CoordinateFormat.DD to "DD (градусы)",
                        CoordinateFormat.DMS to "DMS (мин/сек)",
                        CoordinateFormat.UTM to "UTM (метры)"
                    ).forEach { (fmt, shortLabel) ->
                        val selected = coordFormat == fmt
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(8.dp))
                                .border(
                                    if (selected) 1.5.dp else 1.dp,
                                    if (selected) UrbexColors.GreenBright else UrbexColors.Border,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectCoordFormat(fmt) }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = shortLabel,
                                color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Ориентация экрана PDA",
                    color = UrbexColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PdaOrientation.entries.forEach { orient ->
                        val selected = orientation == orient
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(8.dp))
                                .border(
                                    if (selected) 1.5.dp else 1.dp,
                                    if (selected) UrbexColors.GreenBright else UrbexColors.Border,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectOrientation(orient) }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = orient.label,
                                color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(44.dp),
                onClick = onBack
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = UrbexColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Назад", color = UrbexColors.TextPrimary, fontSize = 13.sp)
            }
            Button(
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                onClick = onNext
            ) {
                Text(
                    text = "Далее: база данных",
                    color = UrbexColors.Background,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(5.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DatabaseConnectStep(
    isLandscape: Boolean,
    busy: Boolean,
    dbName: String,
    onDbNameChange: (String) -> Unit,
    dbRegion: String,
    onDbRegionChange: (String) -> Unit,
    onBack: () -> Unit,
    onCreateEmpty: () -> Unit,
    onImportFile: () -> Unit,
    onOpenTemplate: () -> Unit,
    onLoadDemo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(14.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(14.dp))
                .padding(if (isLandscape) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(18.dp))
                Column {
                    Text(
                        text = "Подключим базу данных",
                        color = UrbexColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Создайте пустую базу для своего сектора или откройте готовый файл .urbexdb.",
                        color = UrbexColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UrbexCompactInputField(
                        value = dbName,
                        onValueChange = onDbNameChange,
                        label = "Название базы",
                        placeholder = "Например: Москва · Сектор-1",
                        minHeight = 38.dp,
                        modifier = Modifier.weight(1f)
                    )
                    UrbexCompactInputField(
                        value = dbRegion,
                        onValueChange = onDbRegionChange,
                        label = "Регион / город (необязательно)",
                        placeholder = "Например: Центральный округ",
                        minHeight = 38.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                UrbexCompactInputField(
                    value = dbName,
                    onValueChange = onDbNameChange,
                    label = "Название базы",
                    placeholder = "Например: Москва · Сектор-1",
                    minHeight = 40.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                UrbexCompactInputField(
                    value = dbRegion,
                    onValueChange = onDbRegionChange,
                    label = "Регион / город (необязательно)",
                    placeholder = "Например: Центральный округ",
                    minHeight = 40.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                enabled = !busy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                onClick = onCreateEmpty
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Создать базу и начать работу",
                    color = UrbexColors.Background,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(14.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(14.dp))
                .padding(if (isLandscape) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Или выберите готовый вариант",
                color = UrbexColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedButton(
                enabled = !busy,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                onClick = onImportFile
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Выбрать файл базы (.urbexdb)",
                    color = UrbexColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    enabled = !busy,
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    onClick = onOpenTemplate
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Схема и шаблон", color = UrbexColors.TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    enabled = !busy,
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    onClick = onLoadDemo
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Демо-база", color = UrbexColors.TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        OutlinedButton(
            enabled = !busy,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(40.dp),
            onClick = onBack
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = UrbexColors.TextSecondary, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text("Назад к настройкам", color = UrbexColors.TextPrimary, fontSize = 12.sp)
        }
    }
}

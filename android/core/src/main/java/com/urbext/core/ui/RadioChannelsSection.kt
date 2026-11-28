package com.urbext.core.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbext.core.radio.RadioPlanUtils
import com.urbext.domain.model.RadioChannelPreset
import com.urbext.domain.model.RadioEncryptionType
import com.urbext.domain.model.RadioModulation
import com.urbext.domain.model.RadioPowerLevel
import com.urbext.domain.model.ToneSquelchMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun buildBlankRadioChannel(): RadioChannelPreset = RadioChannelPreset(
    id = "rch-${System.currentTimeMillis().toString(36)}",
    name = "",
    rxFreqMhz = "446.00625",
    txFreqMhz = "446.00625",
    mode = RadioModulation.NFM,
    bandwidthKhz = "12.5",
    toneMode = ToneSquelchMode.CTCSS,
    rxTone = "88.5",
    txTone = "88.5",
    squelchLevel = 3,
    powerLevel = RadioPowerLevel.MID,
    encryptionType = RadioEncryptionType.NONE,
    encryptionKey = "",
    txInhibit = false,
    notes = "",
    updatedAt = System.currentTimeMillis()
)

@Composable
fun RadioChannelsSection(
    channels: List<RadioChannelPreset>,
    onSaveChannels: suspend (List<RadioChannelPreset>) -> Unit,
    hapticEnabled: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<RadioChannelPreset?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var statusToast by remember { mutableStateOf<String?>(null) }
    var copiedId by remember { mutableStateOf<String?>(null) }
    val revealedMap = remember { mutableStateMapOf<String, Boolean>() }
    val chirpDateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    LaunchedEffect(statusToast) {
        if (statusToast != null) {
            delay(2800)
            statusToast = null
        }
    }
    LaunchedEffect(copiedId) {
        if (copiedId != null) {
            delay(1600)
            copiedId = null
        }
    }

    val filtered = remember(channels, query) {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) {
            channels
        } else {
            channels.filter { ch ->
                ch.name.lowercase(Locale.getDefault()).contains(q) ||
                    ch.rxFreqMhz.contains(q) ||
                    ch.txFreqMhz.contains(q) ||
                    ch.mode.name.lowercase(Locale.getDefault()).contains(q) ||
                    ch.notes.lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    val chirpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null || channels.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = RadioPlanUtils.buildChirpCsv(channels)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
            }
            statusToast = "Таблица каналов экспортирована в CHIRP CSV."
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
            .padding(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Radio,
                contentDescription = null,
                tint = UrbexColors.GreenBright,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Частотный план радиостанций, CTCSS/DCS и шифрование",
                    color = UrbexColors.TextPrimary,
                    fontSize = if (compact) 13.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "VHF/UHF/PMR/LPD/DMR/LoRa, субтоны шумоподавления и ключи шифрования.",
                    color = UrbexColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                shape = RoundedCornerShape(8.dp),
                enabled = channels.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp),
                onClick = {
                    val dateStamp = chirpDateFmt.format(Date())
                    chirpLauncher.launch("urbex-t-radio-plan-$dateStamp.csv")
                }
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CHIRP CSV", color = UrbexColors.TextPrimary, fontSize = 11.sp, maxLines = 1, softWrap = false)
            }

            if (channels.isEmpty()) {
                OutlinedButton(
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp),
                    onClick = {
                        scope.launch {
                            onSaveChannels(RadioPlanUtils.createDefaultRadioChannels())
                            statusToast = "Загружен стандартный радиоплан."
                        }
                    }
                ) {
                    Text("Стандарт", color = UrbexColors.TextSecondary, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            UrbexFilledButton(
                height = 34.dp,
                cornerRadius = 8.dp,
                glowColor = UrbexColors.GreenBright,
                glowRadius = 12.dp,
                glowAlpha = 0.34f,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    formError = null
                    editing = buildBlankRadioChannel()
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Канал", color = UrbexColors.Background, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }

        if (channels.size > 2) {
            UrbexCompactInputField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Поиск по частоте (446.006), названию или модуляции...",
                leadingIcon = Icons.Default.Search,
                showClearButton = true,
                minHeight = 34.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        AnimatedVisibility(
            visible = statusToast != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(130))
        ) {
            Text(
                text = statusToast.orEmpty(),
                color = UrbexColors.GreenBright,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .urbexGlow(UrbexColors.GreenBright, radius = 10.dp, cornerRadius = 8.dp, alpha = 0.20f)
                    .background(UrbexColors.GreenSoft, RoundedCornerShape(8.dp))
                    .border(1.dp, UrbexColors.Green, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }

        // Channel Add / Edit Form
        AnimatedVisibility(
            visible = editing != null,
            enter = fadeIn(tween(160)) + expandVertically(tween(160)),
            exit = fadeOut(tween(130)) + shrinkVertically(tween(130))
        ) {
            editing?.let { draft ->
                val isExisting = channels.any { it.id == draft.id }
                val toneChoices = if (draft.toneMode == ToneSquelchMode.CTCSS) {
                    RadioPlanUtils.CTCSS_TONES
                } else {
                    RadioPlanUtils.DCS_CODES
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .urbexCardGlow(color = UrbexColors.GreenBright, cornerRadius = 10.dp, alpha = 0.14f, radius = 14.dp)
                        .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                        .border(1.dp, UrbexColors.Green.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isExisting) "Редактирование канала" else "Новый радиоканал",
                            color = UrbexColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = {
                                editing = null
                                formError = null
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = UrbexColors.TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (formError != null) {
                        Text(
                            text = formError.orEmpty(),
                            color = Color(0xFFF1ACA7),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(UrbexColors.DangerSoft, RoundedCornerShape(7.dp))
                                .border(1.dp, UrbexColors.Danger, RoundedCornerShape(7.dp))
                                .padding(8.dp)
                        )
                    }

                    UrbexCompactInputField(
                        value = draft.name,
                        onValueChange = { editing = draft.copy(name = it) },
                        label = "Название канала",
                        placeholder = "Напр. Группа Альфа · Основной",
                        minHeight = 34.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UrbexCompactInputField(
                            value = draft.rxFreqMhz,
                            onValueChange = { v ->
                                editing = draft.copy(
                                    rxFreqMhz = v,
                                    txFreqMhz = if (draft.txFreqMhz == draft.rxFreqMhz) v else draft.txFreqMhz
                                )
                            },
                            label = "RX (МГц)",
                            placeholder = "446.00625",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            minHeight = 34.dp,
                            modifier = Modifier.weight(1f)
                        )
                        UrbexCompactInputField(
                            value = draft.txFreqMhz,
                            onValueChange = { editing = draft.copy(txFreqMhz = it) },
                            label = "TX (МГц)",
                            placeholder = "446.00625",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            minHeight = 34.dp,
                            trailingContent = {
                                Text(
                                    text = "=RX",
                                    color = UrbexColors.GreenBright,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { editing = draft.copy(txFreqMhz = draft.rxFreqMhz) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Modulation & Bandwidth dropdowns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UrbexDropdownField(
                            selectedValue = draft.mode,
                            label = "Модуляция",
                            compact = true,
                            options = remember {
                                RadioModulation.entries.map { m ->
                                    UrbexDropdownOption(value = m, label = m.shortLabel)
                                }
                            },
                            onSelect = { editing = draft.copy(mode = it) },
                            modifier = Modifier.weight(1f)
                        )

                        UrbexDropdownField(
                            selectedValue = draft.bandwidthKhz,
                            label = "Полоса (кГц)",
                            compact = true,
                            options = remember {
                                RadioPlanUtils.BANDWIDTH_OPTIONS.map { (bwValue, bwLabel) ->
                                    UrbexDropdownOption(
                                        value = bwValue,
                                        label = bwLabel.ifBlank { "$bwValue кГц" }
                                    )
                                }
                            },
                            onSelect = { editing = draft.copy(bandwidthKhz = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Power & Squelch dropdowns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UrbexDropdownField(
                            selectedValue = draft.powerLevel,
                            label = "Мощность TX",
                            compact = true,
                            options = remember {
                                listOf(
                                    UrbexDropdownOption(RadioPowerLevel.LOW, "Низкая (LOW)"),
                                    UrbexDropdownOption(RadioPowerLevel.MID, "Средняя (MID)"),
                                    UrbexDropdownOption(RadioPowerLevel.HIGH, "Высокая (HIGH)")
                                )
                            },
                            onSelect = { editing = draft.copy(powerLevel = it) },
                            modifier = Modifier.weight(1f)
                        )

                        UrbexDropdownField(
                            selectedValue = draft.squelchLevel,
                            label = "Шумодав (SQL 0–9)",
                            compact = true,
                            options = remember {
                                (0..9).map { lvl ->
                                    val hint = when {
                                        lvl == 0 -> "(открытый)"
                                        lvl <= 3 -> "(мягкий)"
                                        else -> "(плотный)"
                                    }
                                    UrbexDropdownOption(lvl, "SQL $lvl $hint")
                                }
                            },
                            onSelect = { editing = draft.copy(squelchLevel = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = UrbexColors.Border)

                    // Tone Squelch Mode + RX / TX subtones
                    UrbexDropdownField(
                        selectedValue = draft.toneMode,
                        label = "Субтон / Цифровой код (CTCSS / DCS)",
                        compact = true,
                        options = remember {
                            ToneSquelchMode.entries.map { tm ->
                                UrbexDropdownOption(value = tm, label = tm.label)
                            }
                        },
                        onSelect = { tm ->
                            val defTone = when (tm) {
                                ToneSquelchMode.CTCSS -> "88.5"
                                ToneSquelchMode.NONE -> "NONE"
                                else -> "023"
                            }
                            editing = draft.copy(
                                toneMode = tm,
                                rxTone = defTone,
                                txTone = defTone
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (draft.toneMode != ToneSquelchMode.NONE) {
                        val rxToneOptions = remember(draft.toneMode) {
                            listOf(UrbexDropdownOption("NONE", "Без RX (CSQ)")) +
                                toneChoices.map { code ->
                                    val formatted = if (draft.toneMode == ToneSquelchMode.CTCSS) {
                                        "$code Hz"
                                    } else {
                                        "D$code${if (draft.toneMode == ToneSquelchMode.DCS_INV) "I" else "N"}"
                                    }
                                    UrbexDropdownOption(code, formatted)
                                }
                        }
                        val txToneOptions = remember(draft.toneMode) {
                            listOf(UrbexDropdownOption("NONE", "Без TX тона")) +
                                toneChoices.map { code ->
                                    val formatted = if (draft.toneMode == ToneSquelchMode.CTCSS) {
                                        "$code Hz"
                                    } else {
                                        "D$code${if (draft.toneMode == ToneSquelchMode.DCS_INV) "I" else "N"}"
                                    }
                                    UrbexDropdownOption(code, formatted)
                                }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UrbexDropdownField(
                                selectedValue = draft.rxTone,
                                label = "RX тон (приём)",
                                compact = true,
                                options = rxToneOptions,
                                onSelect = { editing = draft.copy(rxTone = it) },
                                modifier = Modifier.weight(1f)
                            )
                            UrbexDropdownField(
                                selectedValue = draft.txTone,
                                label = "TX тон (передача)",
                                compact = true,
                                options = txToneOptions,
                                onSelect = { editing = draft.copy(txTone = it) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = UrbexColors.Border)

                    // Encryption Type dropdown
                    UrbexDropdownField(
                        selectedValue = draft.encryptionType,
                        label = "Шифрование / скремблер",
                        compact = true,
                        options = remember {
                            RadioEncryptionType.entries.map { enc ->
                                UrbexDropdownOption(value = enc, label = enc.label)
                            }
                        },
                        onSelect = { enc ->
                            val nextKey = when {
                                enc == RadioEncryptionType.NONE -> ""
                                draft.encryptionKey.isNotBlank() && enc == draft.encryptionType -> draft.encryptionKey
                                else -> RadioPlanUtils.generateRadioCryptoKey(enc)
                            }
                            editing = draft.copy(encryptionType = enc, encryptionKey = nextKey)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (draft.encryptionType != RadioEncryptionType.NONE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            UrbexCompactInputField(
                                value = draft.encryptionKey,
                                onValueChange = { editing = draft.copy(encryptionKey = it) },
                                label = if (draft.encryptionType == RadioEncryptionType.SCRAMBLER) "Частота скремблера (Гц)" else "HEX-ключ шифрования",
                                placeholder = if (draft.encryptionType == RadioEncryptionType.SCRAMBLER) "3300" else "HEX-ключ",
                                minHeight = 34.dp,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp),
                                onClick = {
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    editing = draft.copy(
                                        encryptionKey = RadioPlanUtils.generateRadioCryptoKey(draft.encryptionType)
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Сгенерировать ключ", tint = UrbexColors.Amber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Ключ", color = UrbexColors.Amber, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }

                    UrbexCompactInputField(
                        value = draft.notes,
                        onValueChange = { editing = draft.copy(notes = it) },
                        label = "Примечание",
                        placeholder = "Позывные, сектор, репитер...",
                        minHeight = 34.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Только приём (TX Lock)", color = UrbexColors.TextPrimary, fontSize = 12.sp)
                        Switch(
                            checked = draft.txInhibit,
                            onCheckedChange = { editing = draft.copy(txInhibit = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            editing = null
                            formError = null
                        }) {
                            Text("Отмена", color = UrbexColors.TextSecondary, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        UrbexFilledButton(
                            height = 36.dp,
                            cornerRadius = 8.dp,
                            glowColor = UrbexColors.GreenBright,
                            glowRadius = 12.dp,
                            glowAlpha = 0.34f,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            onClick = {
                                val rx = RadioPlanUtils.normalizeFrequencyMhz(draft.rxFreqMhz)
                                val tx = RadioPlanUtils.normalizeFrequencyMhz(
                                    draft.txFreqMhz.ifBlank { draft.rxFreqMhz }
                                )
                                when {
                                    draft.name.trim().isEmpty() -> {
                                        formError = "Укажите название или номер канала."
                                    }
                                    rx == null || tx == null -> {
                                        formError = "Частота должна быть в диапазоне 1.00000 – 1300.00000 МГц."
                                    }
                                    draft.encryptionType != RadioEncryptionType.NONE && draft.encryptionKey.trim().isEmpty() -> {
                                        formError = "Введите ключ шифрования или сгенерируйте его."
                                    }
                                    else -> {
                                        val clean = draft.copy(
                                            name = draft.name.trim(),
                                            rxFreqMhz = rx,
                                            txFreqMhz = tx,
                                            encryptionKey = if (draft.encryptionType == RadioEncryptionType.NONE) "" else draft.encryptionKey.trim(),
                                            rxTone = if (draft.toneMode == ToneSquelchMode.NONE) "NONE" else draft.rxTone.ifBlank { "NONE" },
                                            txTone = if (draft.toneMode == ToneSquelchMode.NONE) "NONE" else draft.txTone.ifBlank { "NONE" },
                                            notes = draft.notes.trim(),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        val next = if (channels.any { it.id == clean.id }) {
                                            channels.map { if (it.id == clean.id) clean else it }
                                        } else {
                                            listOf(clean) + channels
                                        }
                                        scope.launch {
                                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSaveChannels(next)
                                            editing = null
                                            formError = null
                                            statusToast = if (isExisting) "Канал «${clean.name}» обновлён." else "Добавлен канал «${clean.name}»."
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Сохранить канал", color = UrbexColors.Background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Channels list
        if (filtered.isEmpty()) {
            Text(
                text = "Радиоканалы не найдены. Нажмите «Канал» чтобы добавить рабочую частоту и субтон.",
                color = UrbexColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filtered.forEachIndexed { idx, ch ->
                    val duplexInfo = RadioPlanUtils.computeDuplexOffsetMhz(ch.rxFreqMhz, ch.txFreqMhz)
                    val isRevealed = revealedMap[ch.id] == true
                    val hasCrypto = ch.encryptionType != RadioEncryptionType.NONE && ch.encryptionKey.isNotEmpty()
                    val toneBadge = when (ch.toneMode) {
                        ToneSquelchMode.NONE -> "CSQ"
                        ToneSquelchMode.CTCSS -> "CTCSS RX:${ch.rxTone} / TX:${ch.txTone}"
                        ToneSquelchMode.DCS -> "DCS RX:${ch.rxTone} / TX:${ch.txTone}"
                        ToneSquelchMode.DCS_INV -> "DCS-I RX:${ch.rxTone} / TX:${ch.txTone}"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .urbexTileEnter(idx + 1)
                            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                            .border(1.dp, if (hasCrypto) UrbexColors.Green.copy(alpha = 0.45f) else UrbexColors.Border, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = ch.name,
                                        color = UrbexColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (ch.txInhibit) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier
                                                .urbexGlow(UrbexColors.Amber, radius = 6.dp, cornerRadius = 4.dp, alpha = 0.18f)
                                                .background(Color(0xFF2A2218), RoundedCornerShape(4.dp))
                                                .border(1.dp, UrbexColors.Amber.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(10.dp))
                                            Text("RX ONLY", color = UrbexColors.Amber, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RX ${ch.rxFreqMhz} МГц",
                                        color = UrbexColors.GreenBright,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (duplexInfo.duplex.isNotEmpty()) {
                                        Text(
                                            text = "TX ${ch.txFreqMhz} (${duplexInfo.duplex}${duplexInfo.offsetMhz})",
                                            color = UrbexColors.Amber,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    } else {
                                        Text(
                                            text = "Симплекс",
                                            color = UrbexColors.TextDim,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        text = "${ch.mode.shortLabel} · ${ch.bandwidthKhz} кГц · ${ch.powerLevel.name} · SQL ${ch.squelchLevel}",
                                        color = UrbexColors.TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = {
                                        formError = null
                                        editing = ch.copy()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Изменить канал", tint = UrbexColors.TextSecondary, modifier = Modifier.size(15.dp))
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSaveChannels(channels.filterNot { it.id == ch.id })
                                            if (editing?.id == ch.id) editing = null
                                            statusToast = "Канал удалён из радиоплана."
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить канал", tint = UrbexColors.Danger, modifier = Modifier.size(15.dp))
                                }
                            }
                        }

                        HorizontalDivider(color = UrbexColors.Border.copy(alpha = 0.6f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = toneBadge,
                                color = UrbexColors.TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .background(UrbexColors.Surface, RoundedCornerShape(6.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            )

                            if (hasCrypto) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier
                                        .urbexGlow(UrbexColors.GreenBright, radius = 8.dp, cornerRadius = 6.dp, alpha = 0.18f)
                                        .background(Color(0xFF17211C), RoundedCornerShape(6.dp))
                                        .border(1.dp, UrbexColors.Green.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(11.dp))
                                    Text(
                                        text = "${ch.encryptionType.name}: " + if (isRevealed) {
                                            ch.encryptionKey
                                        } else {
                                            "${ch.encryptionKey.take(4)}••••${ch.encryptionKey.takeLast(2)}"
                                        },
                                        color = UrbexColors.GreenBright,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(120.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { revealedMap[ch.id] = !isRevealed }
                                            .padding(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isRevealed) "Скрыть ключ" else "Показать ключ",
                                            tint = UrbexColors.TextSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                clipboard.setText(AnnotatedString(ch.encryptionKey))
                                                copiedId = ch.id
                                            }
                                            .padding(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (copiedId == ch.id) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Скопировать ключ",
                                            tint = if (copiedId == ch.id) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .background(UrbexColors.Surface, RoundedCornerShape(6.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = UrbexColors.TextDim, modifier = Modifier.size(11.dp))
                                    Text("Открытый", color = UrbexColors.TextDim, fontSize = 10.sp)
                                }
                            }
                        }

                        if (ch.notes.isNotBlank()) {
                            Text(
                                text = ch.notes,
                                color = UrbexColors.TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

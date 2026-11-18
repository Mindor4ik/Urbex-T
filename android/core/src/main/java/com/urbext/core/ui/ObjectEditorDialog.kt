package com.urbext.core.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.urbext.core.geo.GeoUtils
import com.urbext.domain.model.EntryDifficulty
import com.urbext.domain.model.GeoPoint
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.ObjectParam
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.SecurityLevel
import com.urbext.domain.model.UrbexObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full-parity create & edit modal matching Web QuickObjectModal.tsx:
 * title, category, status, securityLevel, entryDifficulty, floorsOrDepth, customParams, zone, lat/lon/elev, notes, hazards, and photo URI.
 */
@Composable
fun ObjectEditorDialog(
    initialLat: Double,
    initialLon: Double,
    initialElevationM: Int = 140,
    defaultZoneRadiusM: Int = 80,
    authorNodeId: String,
    editingObject: UrbexObject? = null,
    onDismiss: () -> Unit,
    onSave: (UrbexObject) -> Unit,
    onOpenZoneEditor: ((UrbexObject) -> Unit)? = null
) {
    val context = LocalContext.current
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    var title by remember(editingObject?.id) { mutableStateOf(editingObject?.title ?: "") }
    var category by remember(editingObject?.id) {
        mutableStateOf(editingObject?.category ?: ObjectCategory.INDUSTRIAL)
    }
    var status by remember(editingObject?.id) {
        mutableStateOf(editingObject?.status ?: ReliabilityStatus.UNVERIFIED)
    }
    var securityLevel by remember(editingObject?.id) {
        mutableStateOf(editingObject?.securityLevel ?: SecurityLevel.NONE)
    }
    var entryDifficulty by remember(editingObject?.id) {
        mutableStateOf(editingObject?.entryDifficulty ?: EntryDifficulty.MODERATE)
    }
    var floorsOrDepth by remember(editingObject?.id) {
        mutableStateOf(editingObject?.floorsOrDepth ?: "")
    }
    var latStr by remember(editingObject?.id, initialLat) {
        mutableStateOf(
            String.format(Locale.US, "%.6f", editingObject?.lat ?: initialLat)
        )
    }
    var lonStr by remember(editingObject?.id, initialLon) {
        mutableStateOf(
            String.format(Locale.US, "%.6f", editingObject?.lon ?: initialLon)
        )
    }
    var elevationStr by remember(editingObject?.id, initialElevationM) {
        mutableStateOf((editingObject?.elevationM ?: initialElevationM).toString())
    }
    var description by remember(editingObject?.id) { mutableStateOf(editingObject?.description ?: "") }
    var accessNotes by remember(editingObject?.id) { mutableStateOf(editingObject?.accessNotes ?: "") }
    var hazardsStr by remember(editingObject?.id) {
        mutableStateOf(editingObject?.hazards?.joinToString(", ") ?: "")
    }
    var customParams by remember(editingObject?.id) {
        mutableStateOf(editingObject?.customParams ?: emptyList())
    }
    var paramKeyDraft by remember(editingObject?.id) { mutableStateOf("") }
    var paramValueDraft by remember(editingObject?.id) { mutableStateOf("") }
    var zone by remember(editingObject?.id) {
        mutableStateOf<List<GeoPoint>>(editingObject?.zone ?: emptyList())
    }
    var photoUri by remember(editingObject?.id) { mutableStateOf(editingObject?.photoUri) }
    var errorMsg by remember(editingObject?.id) { mutableStateOf<String?>(null) }

    fun buildValidatedObject(): UrbexObject? {
        val trimmedTitle = title.trim()
        val trimmedDesc = description.trim()
        val lat = latStr.replace(',', '.').toDoubleOrNull()
        val lon = lonStr.replace(',', '.').toDoubleOrNull()
        val elevation = elevationStr.replace(',', '.').toDoubleOrNull()

        if (trimmedTitle.length < 2) {
            errorMsg = "Укажите название объекта (минимум 2 символа)."
            return null
        }
        if (lat == null || lon == null || !GeoUtils.isValidCoordinate(lat, lon)) {
            errorMsg = "Некорректные координаты WGS-84 (широта -90..90, долгота -180..180)."
            return null
        }
        if (trimmedDesc.length < 4) {
            errorMsg = "Добавьте краткое описание объекта (минимум 4 символа)."
            return null
        }

        val now = System.currentTimeMillis()
        val hazards = hazardsStr.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val roundedLat = String.format(Locale.US, "%.6f", lat).toDouble()
        val roundedLon = String.format(Locale.US, "%.6f", lon).toDouble()
        val pendingParams = if (paramKeyDraft.isNotBlank() && paramValueDraft.isNotBlank()) {
            customParams + ObjectParam(paramKeyDraft.trim(), paramValueDraft.trim())
        } else {
            customParams
        }

        return UrbexObject(
            id = editingObject?.id ?: "obj-${now.toString(36)}",
            title = trimmedTitle,
            category = category,
            status = status,
            lat = roundedLat,
            lon = roundedLon,
            elevationM = elevation?.roundToInt() ?: 0,
            description = trimmedDesc,
            accessNotes = accessNotes.trim().ifEmpty { "Подходы уточняются на местности." },
            hazards = hazards,
            photoUri = photoUri,
            securityLevel = securityLevel,
            entryDifficulty = entryDifficulty,
            floorsOrDepth = floorsOrDepth.trim().ifEmpty { null },
            customParams = pendingParams,
            zone = zone,
            createdAt = editingObject?.createdAt ?: now,
            updatedAt = now,
            authorNodeId = editingObject?.authorNodeId ?: authorNodeId
        )
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        photoUri = uri.toString()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (isLandscape) 780.dp else 560.dp)
                .fillMaxWidth()
                .padding(
                    horizontal = if (isLandscape) 16.dp else 14.dp,
                    vertical = if (isLandscape) 6.dp else 20.dp
                ),
            shape = RoundedCornerShape(14.dp),
            color = UrbexColors.Surface,
            border = BorderStroke(1.dp, UrbexColors.Border),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (isLandscape) 14.dp else 18.dp,
                            top = if (isLandscape) 5.dp else 12.dp,
                            end = 6.dp,
                            bottom = if (isLandscape) 5.dp else 12.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingObject != null) "Редактирование объекта" else "Новая точка объекта",
                        color = UrbexColors.TextPrimary,
                        fontSize = if (isLandscape) 15.sp else 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = if (isLandscape) Modifier.size(32.dp) else Modifier
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = UrbexColors.TextSecondary
                        )
                    }
                }
                HorizontalDivider(color = UrbexColors.Border)

                // Scrollable form body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(if (isLandscape) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
                ) {
                    AnimatedVisibility(
                        visible = errorMsg != null,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(120))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(UrbexColors.DangerSoft, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Danger.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = errorMsg.orEmpty(),
                                color = Color(0xFFF1ACA7),
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UrbexCompactInputField(
                                value = title,
                                onValueChange = { title = it },
                                label = "Название объекта",
                                placeholder = "Например: Водонапорная башня депо №2",
                                modifier = Modifier.weight(1.45f)
                            )
                            UrbexCompactInputField(
                                value = floorsOrDepth,
                                onValueChange = { floorsOrDepth = it },
                                label = "Этажность / глубина",
                                placeholder = "-14 м / 2 яруса",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        UrbexCompactInputField(
                            value = title,
                            onValueChange = { title = it },
                            label = "Название объекта",
                            placeholder = "Например: Водонапорная башня депо №2",
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = 40.dp
                        )
                    }

                    Text("Категория", color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ObjectCategory.entries, key = { it.name }) { cat ->
                            val selected = category == cat
                            val bg by animateColorAsState(
                                targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Background,
                                animationSpec = tween(150),
                                label = "catBg"
                            )
                            val border by animateColorAsState(
                                targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
                                animationSpec = tween(150),
                                label = "catBorder"
                            )
                            val txt by animateColorAsState(
                                targetValue = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                animationSpec = tween(150),
                                label = "catText"
                            )
                            Text(
                                text = cat.label,
                                color = txt,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .background(bg, RoundedCornerShape(8.dp))
                                    .border(1.dp, border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.95f) { category = cat }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }

                    Text("Статус достоверности", color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ReliabilityStatus.entries, key = { it.name }) { st ->
                            val selected = status == st
                            val bg by animateColorAsState(
                                targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Background,
                                animationSpec = tween(150),
                                label = "stBg"
                            )
                            val border by animateColorAsState(
                                targetValue = if (selected) st.color() else UrbexColors.Border,
                                animationSpec = tween(150),
                                label = "stBorder"
                            )
                            Text(
                                text = "${st.glyph} ${st.label}",
                                color = if (selected) st.color() else UrbexColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .background(bg, RoundedCornerShape(8.dp))
                                    .border(1.dp, border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.95f) { status = st }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }

                    Text("Охрана / режим", color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(SecurityLevel.entries, key = { it.name }) { sec ->
                            val selected = securityLevel == sec
                            Text(
                                text = sec.label,
                                color = if (selected) UrbexColors.Amber else UrbexColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .background(if (selected) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (selected) UrbexColors.Amber else UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.95f) { securityLevel = sec }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }

                    Text("Сложность доступа", color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(EntryDifficulty.entries, key = { it.name }) { diff ->
                            val selected = entryDifficulty == diff
                            Text(
                                text = diff.label,
                                color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .background(if (selected) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.95f) { entryDifficulty = diff }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }

                    if (!isLandscape) {
                        UrbexCompactInputField(
                            value = floorsOrDepth,
                            onValueChange = { floorsOrDepth = it },
                            label = "Этажность / глубина",
                            placeholder = "Напр.: -14 м / 2 яруса",
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = 40.dp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UrbexCompactInputField(
                            value = latStr,
                            onValueChange = { latStr = it },
                            label = "Широта (Lat)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.weight(1f)
                        )
                        UrbexCompactInputField(
                            value = lonStr,
                            onValueChange = { lonStr = it },
                            label = "Долгота (Lon)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.weight(1f)
                        )
                        UrbexCompactInputField(
                            value = elevationStr,
                            onValueChange = { elevationStr = it },
                            label = "Высота (м)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.weight(0.72f)
                        )
                    }

                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UrbexCompactMultilineField(
                                value = description,
                                onValueChange = { description = it },
                                label = "Описание и состояние конструкций",
                                placeholder = "Год постройки, материал, сохранность перекрытий...",
                                minLines = 2,
                                minHeight = 56.dp,
                                modifier = Modifier.weight(1f)
                            )
                            UrbexCompactMultilineField(
                                value = accessNotes,
                                onValueChange = { accessNotes = it },
                                label = "Заметки по подходу и периметру",
                                placeholder = "Тропа с северной стороны, состояние ограждения...",
                                minLines = 2,
                                minHeight = 56.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        UrbexCompactMultilineField(
                            value = description,
                            onValueChange = { description = it },
                            label = "Описание и состояние конструкций",
                            placeholder = "Год постройки, материал, сохранность перекрытий...",
                            minLines = 3,
                            minHeight = 72.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        UrbexCompactMultilineField(
                            value = accessNotes,
                            onValueChange = { accessNotes = it },
                            label = "Заметки по подходу и периметру",
                            placeholder = "Тропа с северной стороны, состояние ограждения...",
                            minLines = 2,
                            minHeight = 56.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    UrbexCompactInputField(
                        value = hazardsStr,
                        onValueChange = { hazardsStr = it },
                        label = "Факторы риска (через запятую)",
                        placeholder = "Провалы пола, сырость",
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Object Zone / Perimeter Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Зона объекта на карте", color = UrbexColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            val zoneSummary = when {
                                zone.size >= 3 -> {
                                    val area = GeoUtils.formatArea(GeoUtils.computePolygonAreaM2(zone))
                                    val perim = GeoUtils.formatDistance(GeoUtils.computePolygonPerimeterMeters(zone))
                                    "${zone.size} тчк · $area · P=$perim"
                                }
                                zone.isNotEmpty() -> "${zone.size} тчк (нужно ≥3)"
                                else -> "Не задана"
                            }
                            Text(
                                text = zoneSummary,
                                color = if (zone.size >= 3) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (onOpenZoneEditor != null) {
                                Text(
                                    text = if (zone.size >= 3) "На карте" else "Рисовать на карте",
                                    color = UrbexColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .background(UrbexColors.GreenSoft, RoundedCornerShape(8.dp))
                                        .border(1.dp, UrbexColors.Green, RoundedCornerShape(8.dp))
                                        .urbexBounceClick(0.95f) {
                                            val valid = buildValidatedObject()
                                            if (valid != null) {
                                                onOpenZoneEditor(valid)
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                            Text(
                                text = "Круг R=${defaultZoneRadiusM} м",
                                color = UrbexColors.TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.95f) {
                                        val lat = latStr.replace(',', '.').toDoubleOrNull()
                                        val lon = lonStr.replace(',', '.').toDoubleOrNull()
                                        if (lat != null && lon != null && GeoUtils.isValidCoordinate(lat, lon)) {
                                            zone = GeoUtils.buildZoneCircle(lat, lon, defaultZoneRadiusM.toDouble())
                                        } else {
                                            errorMsg = "Укажите корректные координаты для круговой зоны."
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                            if (zone.isNotEmpty()) {
                                Text(
                                    text = "Очистить",
                                    color = Color(0xFFF1ACA7),
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                        .urbexBounceClick(0.95f) { zone = emptyList() }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Custom Key-Value Parameters Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Параметры (${customParams.size})", color = UrbexColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    "O2" to "20.6%",
                                    "Радиация" to "0.14 мкЗв/ч",
                                    "Год" to "1978"
                                ).forEach { (pk, pv) ->
                                    Text(
                                        text = "+$pk",
                                        color = UrbexColors.TextSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .background(UrbexColors.Surface, RoundedCornerShape(6.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                            .urbexBounceClick(0.95f) {
                                                paramKeyDraft = pk
                                                paramValueDraft = pv
                                            }
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        customParams.forEachIndexed { idx, param ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${param.key}: ${param.value}",
                                    color = UrbexColors.TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        customParams = customParams.filterIndexed { i, _ -> i != idx }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить параметр", tint = UrbexColors.Danger, modifier = Modifier.size(15.dp))
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            UrbexCompactInputField(
                                value = paramKeyDraft,
                                onValueChange = { paramKeyDraft = it },
                                label = "Параметр",
                                placeholder = "Влажность",
                                modifier = Modifier.weight(1f)
                            )
                            UrbexCompactInputField(
                                value = paramValueDraft,
                                onValueChange = { paramValueDraft = it },
                                label = "Значение",
                                placeholder = "82%",
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (paramKeyDraft.isNotBlank() && paramValueDraft.isNotBlank()) {
                                        customParams = customParams + ObjectParam(paramKeyDraft.trim(), paramValueDraft.trim())
                                        paramKeyDraft = ""
                                        paramValueDraft = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(UrbexColors.Elevated, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Добавить параметр", tint = UrbexColors.GreenBright)
                            }
                        }
                    }

                    // Photo picker & preview
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Фотоснимок (локальный файл)", color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val photoBtnInteraction = remember { MutableInteractionSource() }
                            OutlinedButton(
                                onClick = { photoPicker.launch(arrayOf("image/*")) },
                                interactionSource = photoBtnInteraction,
                                modifier = Modifier
                                    .weight(1f)
                                    .urbexScaleOnPress(photoBtnInteraction, 0.96f)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (photoUri != null) " Заменить фото" else " Выбрать фото",
                                    fontSize = 12.sp
                                )
                            }
                            if (photoUri != null) {
                                IconButton(onClick = { photoUri = null }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить фото", tint = UrbexColors.Danger)
                                }
                            }
                        }
                        if (photoUri != null) {
                            AsyncImage(
                                model = photoUri,
                                contentDescription = "Превью фото",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }

                HorizontalDivider(color = UrbexColors.Border)

                // Footer buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isLandscape) 14.dp else 16.dp,
                            vertical = if (isLandscape) 6.dp else 12.dp
                        ),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = UrbexColors.TextSecondary)
                    }
                    val saveInteraction = remember { MutableInteractionSource() }
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                        interactionSource = saveInteraction,
                        contentPadding = if (isLandscape) PaddingValues(horizontal = 14.dp, vertical = 6.dp) else ButtonDefaults.ContentPadding,
                        modifier = Modifier.urbexScaleOnPress(saveInteraction, 0.96f),
                        onClick = {
                            val saved = buildValidatedObject()
                            if (saved != null) {
                                onSave(saved)
                            }
                        }
                    ) {
                        Text("Сохранить", color = UrbexColors.Background, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

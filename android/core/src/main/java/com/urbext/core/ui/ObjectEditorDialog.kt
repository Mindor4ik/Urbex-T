package com.urbext.core.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.urbext.core.geo.GeoUtils
import com.urbext.core.media.PhotoStorage
import com.urbext.domain.model.EntryDifficulty
import com.urbext.domain.model.GeoPoint
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.ObjectIcon
import com.urbext.domain.model.ObjectParam
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.SecurityLevel
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.ZONE_COLOR_PRESETS
import com.urbext.domain.model.ZonePattern
import com.urbext.domain.model.ZoneStyle
import com.urbext.domain.model.allPhotos
import com.urbext.domain.model.defaultIcon
import com.urbext.domain.model.defaultZoneHex
import com.urbext.domain.model.effectiveZonePattern
import com.urbext.domain.model.normalizeObjectSubtype
import com.urbext.domain.model.normalizeZoneColor
import com.urbext.domain.model.normalizeZonePattern
import com.urbext.domain.model.subtypePresets
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

data class ObjectEditorDraft(
    val title: String,
    val category: ObjectCategory,
    val subtype: String = "",
    val icon: ObjectIcon?,
    val status: ReliabilityStatus,
    val securityLevel: SecurityLevel,
    val entryDifficulty: EntryDifficulty,
    val floorsOrDepth: String,
    val latStr: String,
    val lonStr: String,
    val elevationStr: String,
    val description: String,
    val accessNotes: String,
    val hazardsStr: String,
    val photos: List<String>,
    val customParams: List<ObjectParam>,
    val zone: List<GeoPoint>,
    val zoneColor: String,
    val zoneStyle: ZoneStyle,
    val zonePattern: ZonePattern = ZonePattern.NONE,
    val zoneHatched: Boolean,
    val editingObject: UrbexObject?
)

/**
 * Full-parity create & edit modal matching Web QuickObjectModal.tsx:
 * - Pinned header & pinned footer (never overflows or clips Save/Cancel buttons)
 * - Soft-keyboard safe (.imePadding() + .heightIn(max = 90% screen height))
 * - Photo URL input + download & persist local JPEG into app DB (or gallery picker -> local JPEG)
 * - Clickable preview thumbnail opening PhotoZoomDialog
 */
@Composable
fun ObjectEditorDialog(
    initialLat: Double,
    initialLon: Double,
    initialElevationM: Int = 140,
    defaultZoneRadiusM: Int = 80,
    authorNodeId: String,
    editingObject: UrbexObject? = null,
    initialDraft: ObjectEditorDraft? = null,
    onDismiss: () -> Unit,
    onSave: (UrbexObject) -> Unit,
    onOpenZoneEditor: ((UrbexObject) -> Unit)? = null,
    onOpenPointMover: ((ObjectEditorDraft) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    val resolvedEditing = initialDraft?.editingObject ?: editingObject
    val draftSeedKey = initialDraft ?: editingObject?.id

    var title by remember(draftSeedKey) { mutableStateOf(initialDraft?.title ?: resolvedEditing?.title ?: "") }
    var category by remember(draftSeedKey) {
        mutableStateOf(initialDraft?.category ?: resolvedEditing?.category ?: ObjectCategory.INDUSTRIAL)
    }
    var subtype by remember(draftSeedKey) {
        mutableStateOf(initialDraft?.subtype ?: resolvedEditing?.subtype ?: "")
    }
    var selectedIcon by remember(draftSeedKey) {
        mutableStateOf(if (initialDraft != null) initialDraft.icon else resolvedEditing?.icon)
    }
    var status by remember(draftSeedKey) {
        mutableStateOf(initialDraft?.status ?: resolvedEditing?.status ?: ReliabilityStatus.UNVERIFIED)
    }
    var securityLevel by remember(draftSeedKey) {
        mutableStateOf(initialDraft?.securityLevel ?: resolvedEditing?.securityLevel ?: SecurityLevel.NONE)
    }
    var entryDifficulty by remember(draftSeedKey) {
        mutableStateOf(initialDraft?.entryDifficulty ?: resolvedEditing?.entryDifficulty ?: EntryDifficulty.MODERATE)
    }
    var floorsOrDepth by remember(draftSeedKey) {
        mutableStateOf(initialDraft?.floorsOrDepth ?: resolvedEditing?.floorsOrDepth ?: "")
    }
    var latStr by remember(draftSeedKey, initialLat) {
        mutableStateOf(
            initialDraft?.latStr
                ?: String.format(Locale.US, "%.6f", resolvedEditing?.lat ?: initialLat)
        )
    }
    var lonStr by remember(draftSeedKey, initialLon) {
        mutableStateOf(
            initialDraft?.lonStr
                ?: String.format(Locale.US, "%.6f", resolvedEditing?.lon ?: initialLon)
        )
    }
    var elevationStr by remember(draftSeedKey, initialElevationM) {
        mutableStateOf(
            initialDraft?.elevationStr
                ?: (resolvedEditing?.elevationM ?: initialElevationM).toString()
        )
    }
    var description by remember(draftSeedKey) { mutableStateOf(initialDraft?.description ?: resolvedEditing?.description ?: "") }
    var accessNotes by remember(draftSeedKey) { mutableStateOf(initialDraft?.accessNotes ?: resolvedEditing?.accessNotes ?: "") }
    var hazardsStr by remember(draftSeedKey) {
        mutableStateOf(initialDraft?.hazardsStr ?: resolvedEditing?.hazards?.joinToString(", ") ?: "")
    }
    var customParams by remember(draftSeedKey) {
        mutableStateOf(initialDraft?.customParams ?: resolvedEditing?.customParams ?: emptyList())
    }
    var paramKeyDraft by remember(draftSeedKey) { mutableStateOf("") }
    var paramValueDraft by remember(draftSeedKey) { mutableStateOf("") }
    var zone by remember(draftSeedKey) {
        mutableStateOf<List<GeoPoint>>(initialDraft?.zone ?: resolvedEditing?.zone ?: emptyList())
    }
    var zoneStyle by remember(draftSeedKey) {
        mutableStateOf(initialDraft?.zoneStyle ?: resolvedEditing?.zoneStyle ?: ZoneStyle.SOLID)
    }
    var zonePattern by remember(draftSeedKey) {
        mutableStateOf(
            when {
                initialDraft != null -> normalizeZonePattern(initialDraft.zonePattern.name, initialDraft.zoneHatched)
                resolvedEditing != null -> resolvedEditing.effectiveZonePattern()
                else -> ZonePattern.NONE
            }
        )
    }
    var zoneColor by remember(draftSeedKey) {
        mutableStateOf(initialDraft?.zoneColor ?: resolvedEditing?.zoneColor ?: "")
    }

    var photos by remember(draftSeedKey) {
        mutableStateOf(initialDraft?.photos ?: resolvedEditing?.allPhotos() ?: emptyList())
    }
    var photoUrlDraft by remember(draftSeedKey) { mutableStateOf("") }
    var isDownloadingPhoto by remember(draftSeedKey) { mutableStateOf(false) }
    var photoHintMsg by remember(draftSeedKey) { mutableStateOf<String?>(null) }
    var previewZoomIndex by remember { mutableStateOf<Int?>(null) }
    var errorMsg by remember(draftSeedKey) { mutableStateOf<String?>(null) }

    fun appendUniquePhotos(current: List<String>, additions: List<String>): List<String> {
        val merged = LinkedHashSet<String>()
        current.forEach { if (it.isNotBlank()) merged.add(it.trim()) }
        additions.forEach { if (it.isNotBlank()) merged.add(it.trim()) }
        return merged.toList()
    }

    fun buildValidatedObject(resolvedPhotos: List<String> = photos): UrbexObject? {
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
        val cleanPhotos = resolvedPhotos.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        return UrbexObject(
            id = resolvedEditing?.id ?: "obj-${now.toString(36)}",
            title = trimmedTitle,
            category = category,
            status = status,
            lat = roundedLat,
            lon = roundedLon,
            elevationM = elevation?.roundToInt() ?: 0,
            description = trimmedDesc,
            accessNotes = accessNotes.trim().ifEmpty { "Подходы уточняются на местности." },
            hazards = hazards,
            photoUri = cleanPhotos.firstOrNull(),
            photos = cleanPhotos,
            securityLevel = securityLevel,
            entryDifficulty = entryDifficulty,
            floorsOrDepth = floorsOrDepth.trim().ifEmpty { null },
            customParams = pendingParams,
            zone = zone,
            zoneColor = normalizeZoneColor(zoneColor),
            zoneStyle = zoneStyle,
            zonePattern = zonePattern,
            zoneHatched = zonePattern != ZonePattern.NONE && zonePattern != ZonePattern.NO_FILL,
            createdAt = resolvedEditing?.createdAt ?: now,
            updatedAt = now,
            authorNodeId = resolvedEditing?.authorNodeId ?: authorNodeId,
            icon = selectedIcon,
            subtype = normalizeObjectSubtype(subtype)
        )
    }

    fun captureDraft(): ObjectEditorDraft = ObjectEditorDraft(
        title = title,
        category = category,
        subtype = subtype,
        icon = selectedIcon,
        status = status,
        securityLevel = securityLevel,
        entryDifficulty = entryDifficulty,
        floorsOrDepth = floorsOrDepth,
        latStr = latStr,
        lonStr = lonStr,
        elevationStr = elevationStr,
        description = description,
        accessNotes = accessNotes,
        hazardsStr = hazardsStr,
        photos = photos,
        customParams = customParams,
        zone = zone,
        zoneColor = zoneColor,
        zoneStyle = zoneStyle,
        zonePattern = zonePattern,
        zoneHatched = zonePattern != ZonePattern.NONE && zonePattern != ZonePattern.NO_FILL,
        editingObject = resolvedEditing
    )

    fun nudgeCoords(northM: Double, eastM: Double) {
        val baseLat = latStr.replace(',', '.').toDoubleOrNull()
        val baseLon = lonStr.replace(',', '.').toDoubleOrNull()
        if (baseLat == null || baseLon == null || !GeoUtils.isValidCoordinate(baseLat, baseLon)) {
            errorMsg = "Укажите корректные координаты перед сдвигом."
            return
        }
        val moved = GeoUtils.offsetCoordinate(baseLat, baseLon, northM, eastM)
        latStr = String.format(Locale.US, "%.6f", moved.lat)
        lonStr = String.format(Locale.US, "%.6f", moved.lon)
        errorMsg = null
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isDownloadingPhoto = true
            errorMsg = null
            val addedUris = mutableListOf<String>()
            var totalBytes = 0L
            for (uri in uris) {
                val res = PhotoStorage.copyLocalPhotoToDb(context, uri)
                res.onSuccess { saved ->
                    addedUris.add(saved.localUri)
                    totalBytes += saved.sizeBytes
                }.onFailure {
                    addedUris.add(uri.toString())
                }
            }
            isDownloadingPhoto = false
            photos = appendUniquePhotos(photos, addedUris)
            photoUrlDraft = ""
            photoHintMsg = if (addedUris.size == 1 && totalBytes > 0L) {
                "Фото оптимизировано и добавлено (${GeoUtils.formatBytes(totalBytes)})."
            } else {
                "Добавлено фото: ${addedUris.size} (${GeoUtils.formatBytes(totalBytes)})."
            }
        }
    }

    fun triggerSave(openZone: Boolean) {
        if (isDownloadingPhoto) return
        val draftUrl = photoUrlDraft.trim()
        val needsRemoteSave = draftUrl.isNotEmpty() &&
            (draftUrl.startsWith("http://") || draftUrl.startsWith("https://"))

        if (!needsRemoteSave) {
            val saved = buildValidatedObject(photos) ?: return
            if (openZone && onOpenZoneEditor != null) {
                onOpenZoneEditor(saved)
            } else {
                onSave(saved)
            }
            return
        }

        // First validate basic fields before attempting download
        if (buildValidatedObject(photos) == null) return

        scope.launch {
            isDownloadingPhoto = true
            errorMsg = null
            val result = PhotoStorage.downloadPhotoToLocalDb(context, draftUrl)
            isDownloadingPhoto = false
            val finalUri = result.fold(
                onSuccess = { saved ->
                    photoHintMsg = "Фото сохранено в базу (${GeoUtils.formatBytes(saved.sizeBytes)})."
                    saved.localUri
                },
                onFailure = { draftUrl }
            )
            val updatedPhotos = appendUniquePhotos(photos, listOf(finalUri))
            photos = updatedPhotos
            photoUrlDraft = ""
            val saved = buildValidatedObject(updatedPhotos) ?: return@launch
            if (openZone && onOpenZoneEditor != null) {
                onOpenZoneEditor(saved)
            } else {
                onSave(saved)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .imePadding()
                .widthIn(max = if (isLandscape) 780.dp else 560.dp)
                .heightIn(max = (cfg.screenHeightDp * 0.90f).dp)
                .fillMaxWidth()
                .padding(
                    horizontal = if (isLandscape) 14.dp else 12.dp,
                    vertical = if (isLandscape) 4.dp else 10.dp
                ),
            shape = RoundedCornerShape(14.dp),
            color = UrbexColors.Surface,
            border = BorderStroke(1.dp, UrbexColors.Border),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Pinned Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (isLandscape) 14.dp else 16.dp,
                            top = if (isLandscape) 5.dp else 8.dp,
                            end = 6.dp,
                            bottom = if (isLandscape) 5.dp else 8.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (resolvedEditing != null) "Редактирование объекта" else "Новая точка объекта",
                        color = UrbexColors.TextPrimary,
                        fontSize = if (isLandscape) 14.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = UrbexColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                HorizontalDivider(color = UrbexColors.Border)

                // Scrollable Form Body (bounded strictly so footer Save/Cancel buttons are always visible)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(if (isLandscape) 10.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 7.dp else 9.dp)
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
                                .padding(8.dp)
                        ) {
                            Text(
                                text = errorMsg.orEmpty(),
                                color = Color(0xFFF1ACA7),
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
                                value = title,
                                onValueChange = { title = it },
                                label = "Название объекта",
                                placeholder = "Например: Водонапорная башня депо №2",
                                minHeight = 36.dp,
                                modifier = Modifier.weight(1.45f)
                            )
                            UrbexCompactInputField(
                                value = floorsOrDepth,
                                onValueChange = { floorsOrDepth = it },
                                label = "Этажность / глубина",
                                placeholder = "-14 м / 2 яруса",
                                minHeight = 36.dp,
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
                            minHeight = 38.dp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UrbexDropdownField(
                            selectedValue = category,
                            label = "Категория",
                            compact = true,
                            options = ObjectCategory.entries.map { cat ->
                                UrbexDropdownOption(value = cat, label = cat.label)
                            },
                            onSelect = { category = it },
                            modifier = Modifier.weight(1f)
                        )
                        UrbexDropdownField(
                            selectedValue = status,
                            label = "Достоверность",
                            compact = true,
                            options = ReliabilityStatus.entries.map { st ->
                                UrbexDropdownOption(
                                    value = st,
                                    label = st.label,
                                    accentColor = st.color()
                                )
                            },
                            onSelect = { status = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UrbexDropdownField(
                            selectedValue = securityLevel,
                            label = "Охрана / режим",
                            compact = true,
                            options = SecurityLevel.entries.map { sec ->
                                UrbexDropdownOption(value = sec, label = sec.label)
                            },
                            onSelect = { securityLevel = it },
                            modifier = Modifier.weight(1f)
                        )
                        UrbexDropdownField(
                            selectedValue = entryDifficulty,
                            label = "Доступ",
                            compact = true,
                            options = EntryDifficulty.entries.map { diff ->
                                UrbexDropdownOption(value = diff, label = diff.label)
                            },
                            onSelect = { entryDifficulty = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!isLandscape) {
                        UrbexCompactInputField(
                            value = floorsOrDepth,
                            onValueChange = { floorsOrDepth = it },
                            label = "Этажность / глубина",
                            placeholder = "Напр.: -14 м / 2 яруса",
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = 36.dp
                        )
                    }

                    // Object Subtype Input + Category Presets
                    val presets = category.subtypePresets()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                            .padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Подтип объекта",
                                color = UrbexColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (subtype.isNotBlank()) {
                                Text(
                                    text = "Сбросить",
                                    color = UrbexColors.TextSecondary,
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .urbexBounceClick(0.94f) { subtype = "" }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        UrbexCompactInputField(
                            value = subtype,
                            onValueChange = { subtype = it },
                            label = "",
                            placeholder = "Выберите пресет или введите свой подтип...",
                            minHeight = 34.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(presets) { _, preset ->
                                val isActive = subtype.trim().equals(preset.label, ignoreCase = true)
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(if (isActive) UrbexColors.GreenSoft else UrbexColors.Surface)
                                        .border(
                                            1.dp,
                                            if (isActive) UrbexColors.GreenBright else UrbexColors.Border,
                                            RoundedCornerShape(7.dp)
                                        )
                                        .urbexBounceClick(0.95f) {
                                            if (isActive) {
                                                subtype = ""
                                            } else {
                                                subtype = preset.label
                                                selectedIcon = preset.icon
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = preset.icon.vector(),
                                        contentDescription = null,
                                        tint = if (isActive) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = preset.label,
                                        color = if (isActive) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // Object Icon Picker (Auto + 72 Icons)
                    val effectivePreviewIcon = selectedIcon ?: category.defaultIcon()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                            .padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = effectivePreviewIcon.vector(),
                                    contentDescription = null,
                                    tint = status.color(),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Иконка (${ObjectIcon.entries.size})",
                                    color = UrbexColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = selectedIcon?.label ?: "Авто (${category.defaultIcon().label})",
                                    color = UrbexColors.TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Авто",
                                color = if (selectedIcon == null) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (selectedIcon == null) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .background(if (selectedIcon == null) UrbexColors.GreenSoft else UrbexColors.Surface, RoundedCornerShape(6.dp))
                                    .border(1.dp, if (selectedIcon == null) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(6.dp))
                                    .urbexBounceClick(0.95f) { selectedIcon = null }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        val iconCols = if (isLandscape) 10 else 8
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 176.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ObjectIcon.entries.chunked(iconCols).forEach { rowIcons ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    rowIcons.forEach { iconOption ->
                                        val isSelected = selectedIcon == iconOption
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(32.dp)
                                                .urbexGlow(
                                                    color = UrbexColors.GreenBright,
                                                    radius = 7.dp,
                                                    cornerRadius = 7.dp,
                                                    alpha = 0.24f,
                                                    enabled = isSelected
                                                )
                                                .background(
                                                    if (isSelected) UrbexColors.GreenSoft else UrbexColors.Surface,
                                                    RoundedCornerShape(7.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) UrbexColors.GreenBright else UrbexColors.Border,
                                                    RoundedCornerShape(7.dp)
                                                )
                                                .urbexBounceClick(0.92f) { selectedIcon = iconOption },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = iconOption.vector(),
                                                contentDescription = iconOption.label,
                                                tint = if (isSelected) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    repeat(iconCols - rowIcons.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
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
                            minHeight = 36.dp,
                            modifier = Modifier.weight(1f)
                        )
                        UrbexCompactInputField(
                            value = lonStr,
                            onValueChange = { lonStr = it },
                            label = "Долгота (Lon)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            minHeight = 36.dp,
                            modifier = Modifier.weight(1f)
                        )
                        UrbexCompactInputField(
                            value = elevationStr,
                            onValueChange = { elevationStr = it },
                            label = "Высота (м)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            minHeight = 36.dp,
                            modifier = Modifier.weight(0.72f)
                        )
                    }

                    // Inline Point Nudges + Move on Map Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(9.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(9.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Сдвиг 5м:", color = UrbexColors.TextSecondary, fontSize = 10.sp)
                            listOf(
                                Triple("С", 5.0, 0.0),
                                Triple("Ю", -5.0, 0.0),
                                Triple("З", 0.0, -5.0),
                                Triple("В", 0.0, 5.0)
                            ).forEach { (dirLabel, dN, dE) ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 28.dp, height = 28.dp)
                                        .background(UrbexColors.Surface, RoundedCornerShape(6.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                        .urbexBounceClick(0.92f) { nudgeCoords(dN, dE) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dirLabel,
                                        color = UrbexColors.TextPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (onOpenPointMover != null) {
                            Row(
                                modifier = Modifier
                                    .height(30.dp)
                                    .urbexGlow(UrbexColors.GreenBright, radius = 8.dp, cornerRadius = 7.dp, alpha = 0.22f)
                                    .background(UrbexColors.GreenSoft, RoundedCornerShape(7.dp))
                                    .border(1.dp, UrbexColors.Green, RoundedCornerShape(7.dp))
                                    .urbexBounceClick(0.95f) {
                                        onOpenPointMover(captureDraft())
                                    }
                                    .padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenWith,
                                    contentDescription = null,
                                    tint = UrbexColors.GreenBright,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Двигать на карте",
                                    color = UrbexColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
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
                                placeholder = "Год постройки, материал, сохранность...",
                                minLines = 2,
                                minHeight = 48.dp,
                                modifier = Modifier.weight(1f)
                            )
                            UrbexCompactMultilineField(
                                value = accessNotes,
                                onValueChange = { accessNotes = it },
                                label = "Заметки по подходу",
                                placeholder = "Тропа с северной стороны...",
                                minLines = 2,
                                minHeight = 48.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        UrbexCompactMultilineField(
                            value = description,
                            onValueChange = { description = it },
                            label = "Описание и состояние конструкций",
                            placeholder = "Год постройки, материал, сохранность перекрытий...",
                            minLines = 2,
                            minHeight = 56.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        UrbexCompactMultilineField(
                            value = accessNotes,
                            onValueChange = { accessNotes = it },
                            label = "Заметки по подходу и периметру",
                            placeholder = "Тропа с северной стороны, состояние ограждения...",
                            minLines = 2,
                            minHeight = 48.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    UrbexCompactInputField(
                        value = hazardsStr,
                        onValueChange = { hazardsStr = it },
                        label = "Факторы риска (через запятую)",
                        placeholder = "Провалы пола, сырость",
                        minHeight = 36.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Object Zone / Perimeter Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                            .padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Зона на карте", color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                                fontSize = 10.sp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (onOpenZoneEditor != null) {
                                Text(
                                    text = if (zone.size >= 3) "На карте" else "Рисовать на карте",
                                    color = UrbexColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .urbexGlow(UrbexColors.GreenBright, radius = 10.dp, cornerRadius = 7.dp, alpha = 0.22f)
                                        .background(UrbexColors.GreenSoft, RoundedCornerShape(7.dp))
                                        .border(1.dp, UrbexColors.Green, RoundedCornerShape(7.dp))
                                        .urbexBounceClick(0.95f) { triggerSave(openZone = true) }
                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                )
                            }
                            Text(
                                text = "Круг R=${defaultZoneRadiusM} м",
                                color = UrbexColors.TextPrimary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .background(UrbexColors.Surface, RoundedCornerShape(7.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(7.dp))
                                    .urbexBounceClick(0.95f) {
                                        val lat = latStr.replace(',', '.').toDoubleOrNull()
                                        val lon = lonStr.replace(',', '.').toDoubleOrNull()
                                        if (lat != null && lon != null && GeoUtils.isValidCoordinate(lat, lon)) {
                                            zone = GeoUtils.buildZoneCircle(lat, lon, defaultZoneRadiusM.toDouble())
                                        } else {
                                            errorMsg = "Укажите корректные координаты для круговой зоны."
                                        }
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                            if (zone.isNotEmpty()) {
                                Text(
                                    text = "Очистить",
                                    color = Color(0xFFF1ACA7),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .background(UrbexColors.Surface, RoundedCornerShape(7.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(7.dp))
                                        .urbexBounceClick(0.95f) { zone = emptyList() }
                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = UrbexColors.Border.copy(alpha = 0.7f))

                        // Zone Border Line Styles & Interior Fill Patterns with Live Previews
                        val previewHex = normalizeZoneColor(zoneColor) ?: status.defaultZoneHex()
                        val previewColor = remember(previewHex) {
                            runCatching { Color(android.graphics.Color.parseColor(previewHex)) }
                                .getOrDefault(UrbexColors.Green)
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Линия границы", color = UrbexColors.TextSecondary, fontSize = 10.sp)
                                Text(zoneStyle.label, color = UrbexColors.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ZoneStyle.entries.forEach { st ->
                                    val selected = zoneStyle == st
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 32.dp)
                                            .background(
                                                if (selected) UrbexColors.Elevated else UrbexColors.Surface,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (selected) UrbexColors.Green else UrbexColors.Border,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .urbexBounceClick(0.95f) { zoneStyle = st }
                                            .padding(horizontal = 9.dp, vertical = 5.dp)
                                    ) {
                                        ZoneLinePreviewGlyph(
                                            style = st,
                                            color = if (selected) previewColor else UrbexColors.TextSecondary
                                        )
                                        Text(
                                            text = st.label,
                                            color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Стиль заливки зоны", color = UrbexColors.TextSecondary, fontSize = 10.sp)
                                Text(zonePattern.label, color = UrbexColors.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ZonePattern.entries.forEach { pat ->
                                    val selected = zonePattern == pat
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 32.dp)
                                            .background(
                                                if (selected) UrbexColors.GreenSoft else UrbexColors.Surface,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (selected) UrbexColors.Green else UrbexColors.Border,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .urbexBounceClick(0.95f) { zonePattern = pat }
                                            .padding(horizontal = 9.dp, vertical = 5.dp)
                                    ) {
                                        ZonePatternPreviewGlyph(
                                            pattern = pat,
                                            color = if (selected) previewColor else UrbexColors.TextSecondary
                                        )
                                        Text(
                                            text = pat.label,
                                            color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // Manual Zone Color (Auto + Swatches + HEX input)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isAutoColor = normalizeZoneColor(zoneColor) == null
                            Text(
                                text = "Авто",
                                color = if (isAutoColor) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isAutoColor) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .urbexGlow(UrbexColors.GreenBright, radius = 8.dp, cornerRadius = 6.dp, alpha = 0.18f, enabled = isAutoColor)
                                    .background(if (isAutoColor) UrbexColors.Elevated else UrbexColors.Surface, RoundedCornerShape(6.dp))
                                    .border(1.dp, if (isAutoColor) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(6.dp))
                                    .urbexBounceClick(0.95f) { zoneColor = "" }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                            ZONE_COLOR_PRESETS.forEach { (hex, _) ->
                                val active = normalizeZoneColor(zoneColor) == hex
                                val swatchColor = runCatching {
                                    Color(android.graphics.Color.parseColor(hex))
                                }.getOrDefault(UrbexColors.Green)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor, CircleShape)
                                        .border(
                                            width = if (active) 2.dp else 1.dp,
                                            color = if (active) UrbexColors.TextPrimary else UrbexColors.Border,
                                            shape = CircleShape
                                        )
                                        .urbexBounceClick(0.92f) { zoneColor = hex }
                                )
                            }
                            UrbexCompactInputField(
                                value = zoneColor,
                                onValueChange = { raw ->
                                    zoneColor = raw.trim().uppercase(Locale.US).take(7)
                                },
                                placeholder = status.defaultZoneHex(),
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                minHeight = 28.dp,
                                modifier = Modifier.widthIn(min = 96.dp, max = 108.dp)
                            )
                        }
                    }

                    // Custom Key-Value Parameters Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                            .padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Параметры (${customParams.size})", color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    "O2" to "20.6%",
                                    "Радиация" to "0.14 мкЗв/ч",
                                    "Год" to "1978"
                                ).forEach { (pk, pv) ->
                                    Text(
                                        text = "+$pk",
                                        color = UrbexColors.TextSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .background(UrbexColors.Surface, RoundedCornerShape(6.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                            .urbexBounceClick(0.95f) {
                                                paramKeyDraft = pk
                                                paramValueDraft = pv
                                            }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        customParams.forEachIndexed { idx, param ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(UrbexColors.Surface, RoundedCornerShape(7.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(7.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${param.key}: ${param.value}",
                                    color = UrbexColors.TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { customParams = customParams.filterIndexed { i, _ -> i != idx } },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить параметр", tint = UrbexColors.Danger, modifier = Modifier.size(14.dp))
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
                                minHeight = 34.dp,
                                modifier = Modifier.weight(1f)
                            )
                            UrbexCompactInputField(
                                value = paramValueDraft,
                                onValueChange = { paramValueDraft = it },
                                label = "Значение",
                                placeholder = "82%",
                                minHeight = 34.dp,
                                modifier = Modifier.weight(1f)
                            )
                            val canAddParam = paramKeyDraft.isNotBlank() && paramValueDraft.isNotBlank()
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .urbexGlow(
                                        color = UrbexColors.GreenBright,
                                        radius = 8.dp,
                                        cornerRadius = 8.dp,
                                        alpha = 0.22f,
                                        enabled = canAddParam
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (canAddParam) UrbexColors.GreenSoft else UrbexColors.Elevated)
                                    .border(
                                        1.dp,
                                        if (canAddParam) UrbexColors.Green else UrbexColors.Border,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .urbexBounceClick(0.92f) {
                                        if (canAddParam) {
                                            customParams = customParams + ObjectParam(paramKeyDraft.trim(), paramValueDraft.trim())
                                            paramKeyDraft = ""
                                            paramValueDraft = ""
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Добавить параметр",
                                    tint = UrbexColors.GreenBright,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Multi-Photo URL + Multi-File Upload -> Save directly to local offline DB
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                            .padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Фотографии объекта (${photos.size})",
                                color = UrbexColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (photos.isNotEmpty()) {
                                val allLocal = photos.all { PhotoStorage.isLocalStoredUri(it) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (allLocal) UrbexColors.GreenBright else UrbexColors.Amber,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (allLocal) "В локальной БД" else "Есть URL",
                                        color = if (allLocal) UrbexColors.GreenBright else UrbexColors.Amber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            UrbexCompactInputField(
                                value = photoUrlDraft,
                                onValueChange = {
                                    photoUrlDraft = it
                                    if (photoHintMsg != null) photoHintMsg = null
                                },
                                label = "URL-ссылка на фото (https://...)",
                                placeholder = "https://site.com/image.jpg",
                                minHeight = 34.dp,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Elevated),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                enabled = !isDownloadingPhoto && photoUrlDraft.isNotBlank(),
                                modifier = Modifier.height(34.dp),
                                onClick = {
                                    val url = photoUrlDraft.trim()
                                    scope.launch {
                                        isDownloadingPhoto = true
                                        photoHintMsg = null
                                        val res = PhotoStorage.downloadPhotoToLocalDb(context, url)
                                        isDownloadingPhoto = false
                                        res.onSuccess { saved ->
                                            photos = appendUniquePhotos(photos, listOf(saved.localUri))
                                            photoUrlDraft = ""
                                            photoHintMsg = "Фото загружено и добавлено в базу (${GeoUtils.formatBytes(saved.sizeBytes)})."
                                        }.onFailure { err ->
                                            photos = appendUniquePhotos(photos, listOf(url))
                                            photoUrlDraft = ""
                                            photoHintMsg = "Не удалось скачать (${err.message ?: "проверьте URL"}). Ссылка добавлена как URL."
                                        }
                                    }
                                }
                            ) {
                                if (isDownloadingPhoto) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = UrbexColors.GreenBright
                                    )
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("В базу", color = UrbexColors.TextPrimary, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                        }

                        if (!photoHintMsg.isNullOrBlank()) {
                            Text(
                                text = photoHintMsg.orEmpty(),
                                color = UrbexColors.Amber,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }

                        if (photos.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(photos) { idx, itemUri ->
                                    Box(
                                        modifier = Modifier
                                            .size(width = 86.dp, height = 74.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (idx == 0) 1.5.dp else 1.dp,
                                                color = if (idx == 0) UrbexColors.Green else UrbexColors.Border,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .urbexBounceClick(0.95f) { previewZoomIndex = idx }
                                    ) {
                                        val density = LocalDensity.current
                                        val thumbReq = remember(context, itemUri) {
                                            val wPx = with(density) { 86.dp.roundToPx().coerceAtLeast(1) }
                                            val hPx = with(density) { 74.dp.roundToPx().coerceAtLeast(1) }
                                            ImageRequest.Builder(context)
                                                .data(itemUri)
                                                .size(wPx, hPx)
                                                .crossfade(false)
                                                .build()
                                        }
                                        AsyncImage(
                                            model = thumbReq,
                                            contentDescription = "Фото ${idx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize()
                                        )
                                        if (idx == 0) {
                                            Text(
                                                text = "Обложка",
                                                color = UrbexColors.Background,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(4.dp)
                                                    .background(UrbexColors.GreenBright, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    val target = photos[idx]
                                                    photos = listOf(target) + photos.filterIndexed { i, _ -> i != idx }
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(3.dp)
                                                    .size(22.dp)
                                                    .background(Color(0xBB0C100C), CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = "Сделать обложкой фото ${idx + 1}",
                                                    tint = UrbexColors.Amber,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                photos = photos.filterIndexed { i, _ -> i != idx }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(3.dp)
                                                .size(22.dp)
                                                .background(Color(0xCC5A1919), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Удалить фото ${idx + 1}",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(3.dp)
                                                .background(Color(0xAA0C100C), RoundedCornerShape(4.dp))
                                                .padding(2.dp)
                                        ) {
                                            Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить", tint = UrbexColors.TextPrimary, modifier = Modifier.size(11.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val photoBtnInteraction = remember { MutableInteractionSource() }
                            OutlinedButton(
                                onClick = { photoPicker.launch(arrayOf("image/*")) },
                                shape = RoundedCornerShape(8.dp),
                                interactionSource = photoBtnInteraction,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .urbexScaleOnPress(photoBtnInteraction, 0.96f)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (photos.isNotEmpty()) "Добавить фото" else "Выбрать фото",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (photos.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        photos = emptyList()
                                        photoUrlDraft = ""
                                        photoHintMsg = null
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Очистить все фото", tint = UrbexColors.Danger, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = UrbexColors.Border)

                // Pinned Footer Buttons — always reachable & unclipped
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isLandscape) 14.dp else 16.dp,
                            vertical = if (isLandscape) 6.dp else 9.dp
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Отмена", color = UrbexColors.TextSecondary, fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                    UrbexFilledButton(
                        height = 36.dp,
                        cornerRadius = 8.dp,
                        enabled = !isDownloadingPhoto,
                        glowColor = UrbexColors.GreenBright,
                        glowRadius = 12.dp,
                        glowAlpha = 0.32f,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        onClick = { triggerSave(openZone = false) }
                    ) {
                        Text(
                            text = if (isDownloadingPhoto) "Сохранение..." else "Сохранить",
                            color = UrbexColors.Background,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }

    val zoomIdx = previewZoomIndex
    if (zoomIdx != null && photos.isNotEmpty()) {
        val safeIdx = zoomIdx.coerceIn(0, photos.size - 1)
        PhotoZoomDialog(
            photoUri = photos[safeIdx],
            photos = photos,
            initialIndex = safeIdx,
            title = title.ifBlank { "Предпросмотр фото" },
            onDismiss = { previewZoomIndex = null }
        )
    }
}

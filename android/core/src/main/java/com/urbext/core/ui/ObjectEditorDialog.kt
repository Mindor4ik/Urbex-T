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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full-parity create & edit modal matching Web QuickObjectModal.tsx:
 * title, category, status, lat, lon, elevationM, description, accessNotes, hazards, and local photo URI.
 */
@Composable
fun ObjectEditorDialog(
    initialLat: Double,
    initialLon: Double,
    initialElevationM: Int = 140,
    authorNodeId: String,
    editingObject: UrbexObject? = null,
    onDismiss: () -> Unit,
    onSave: (UrbexObject) -> Unit
) {
    val context = LocalContext.current

    var title by remember(editingObject?.id) { mutableStateOf(editingObject?.title ?: "") }
    var category by remember(editingObject?.id) {
        mutableStateOf(editingObject?.category ?: ObjectCategory.INDUSTRIAL)
    }
    var status by remember(editingObject?.id) {
        mutableStateOf(editingObject?.status ?: ReliabilityStatus.UNVERIFIED)
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
    var photoUri by remember(editingObject?.id) { mutableStateOf(editingObject?.photoUri) }
    var errorMsg by remember(editingObject?.id) { mutableStateOf<String?>(null) }

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
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp),
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
                        .padding(start = 18.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingObject != null) "Редактирование объекта" else "Новая точка объекта",
                        color = UrbexColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onDismiss) {
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Название объекта") },
                        placeholder = { Text("Например: Водонапорная башня депо №2", color = UrbexColors.TextDim) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = latStr,
                            onValueChange = { latStr = it },
                            label = { Text("Широта (Lat)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lonStr,
                            onValueChange = { lonStr = it },
                            label = { Text("Долгота (Lon)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = elevationStr,
                            onValueChange = { elevationStr = it },
                            label = { Text("Высота (м)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            modifier = Modifier.weight(0.72f)
                        )
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Описание и состояние конструкций") },
                        placeholder = { Text("Год постройки, материал, сохранность перекрытий...", color = UrbexColors.TextDim) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = accessNotes,
                        onValueChange = { accessNotes = it },
                        label = { Text("Заметки по подходу и периметру") },
                        placeholder = { Text("Тропа с северной стороны, состояние ограждения...", color = UrbexColors.TextDim) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = hazardsStr,
                        onValueChange = { hazardsStr = it },
                        label = { Text("Факторы риска (через запятую)") },
                        placeholder = { Text("Провалы пола, сырость", color = UrbexColors.TextDim) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        modifier = Modifier.urbexScaleOnPress(saveInteraction, 0.96f),
                        onClick = {
                            val trimmedTitle = title.trim()
                            val trimmedDesc = description.trim()
                            val lat = latStr.replace(',', '.').toDoubleOrNull()
                            val lon = lonStr.replace(',', '.').toDoubleOrNull()
                            val elevation = elevationStr.replace(',', '.').toDoubleOrNull()

                            if (trimmedTitle.length < 2) {
                                errorMsg = "Укажите название объекта (минимум 2 символа)."
                                return@Button
                            }
                            if (lat == null || lon == null || !GeoUtils.isValidCoordinate(lat, lon)) {
                                errorMsg = "Некорректные координаты WGS-84 (широта -90..90, долгота -180..180)."
                                return@Button
                            }
                            if (trimmedDesc.length < 4) {
                                errorMsg = "Добавьте краткое описание объекта (минимум 4 символа)."
                                return@Button
                            }

                            val now = System.currentTimeMillis()
                            val hazards = hazardsStr.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            val roundedLat = String.format(Locale.US, "%.6f", lat).toDouble()
                            val roundedLon = String.format(Locale.US, "%.6f", lon).toDouble()

                            val saved = UrbexObject(
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
                                createdAt = editingObject?.createdAt ?: now,
                                updatedAt = now,
                                authorNodeId = editingObject?.authorNodeId ?: authorNodeId
                            )
                            onSave(saved)
                        }
                    ) {
                        Text("Сохранить", color = UrbexColors.Background, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

package com.urbext.feature.objects

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.urbext.core.geo.GeoUtils
import com.urbext.core.location.AdaptiveLocationClient
import com.urbext.core.ui.ObjectEditorDialog
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.color
import com.urbext.core.ui.urbexBounceClick
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.ObjectsViewMode
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.PowerProfile
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.ProfileRepository
import com.urbext.domain.repository.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun ObjectsScreen(
    objectRepository: ObjectRepository,
    settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository,
    initialSelectedId: String? = null,
    onEditZoneOnMap: (UrbexObject) -> Unit = {},
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<ReliabilityStatus?>(null) }
    var viewModeOverride by remember { mutableStateOf<ObjectsViewMode?>(null) }
    var activeObjectId by remember { mutableStateOf(initialSelectedId) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var editingTarget by remember { mutableStateOf<UrbexObject?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val objects by objectRepository.observeObjects(statusFilter, query)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val viewMode = viewModeOverride ?: settings.defaultObjectsView
    val profile by profileRepository.observeProfile()
        .collectAsStateWithLifecycle(
            initialValue = UserProfile("local", "Сокол-1", "!a48f20c1", OperatorRole.SCOUT, "", "", 0L)
        )

    val locationClient = remember(context) { AdaptiveLocationClient(context) }
    var currentFix by remember { mutableStateOf(locationClient.getLastKnownFixOrDefault()) }
    val hasLocationPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(hasLocationPermission, settings.powerProfile) {
        if (hasLocationPermission && settings.powerProfile != PowerProfile.MANUAL) {
            locationClient.observeLocationUpdates(settings.powerProfile).collect { fix ->
                currentFix = fix
            }
        }
    }

    LaunchedEffect(initialSelectedId) {
        if (initialSelectedId != null) {
            activeObjectId = initialSelectedId
            confirmDeleteId = null
        }
    }

    var visibleLimit by remember(settings.pageSize, query, statusFilter) {
        mutableIntStateOf(settings.pageSize.coerceAtLeast(8))
    }
    val paginated = remember(objects, visibleLimit) {
        objects.take(visibleLimit)
    }

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
            val distM = GeoUtils.haversineDistanceMeters(currentFix.lat, currentFix.lon, obj.lat, obj.lon)
            val azimuth = GeoUtils.bearingDegrees(currentFix.lat, currentFix.lon, obj.lat, obj.lon)

            // Full-area Object Detail View (2-column tactical dossier in landscape)
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
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.95f) {
                                activeObjectId = null
                                confirmDeleteId = null
                            }
                            .padding(
                                horizontal = if (isLandscape) 10.dp else 12.dp,
                                vertical = if (isLandscape) 5.dp else 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = UrbexColors.TextPrimary, modifier = Modifier.size(15.dp))
                        Text("К списку объектов", color = UrbexColors.TextPrimary, fontSize = if (isLandscape) 12.sp else 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                .urbexBounceClick(0.95f) { editingTarget = obj }
                                .padding(
                                    horizontal = 10.dp,
                                    vertical = if (isLandscape) 5.dp else 8.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = UrbexColors.TextPrimary, modifier = Modifier.size(14.dp))
                            Text("Редактировать", color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        Row(
                            modifier = Modifier
                                .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                .urbexBounceClick(0.95f) { confirmDeleteId = obj.id }
                                .padding(
                                    horizontal = 10.dp,
                                    vertical = if (isLandscape) 5.dp else 8.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = UrbexColors.Danger, modifier = Modifier.size(14.dp))
                            Text("Удалить", color = UrbexColors.Danger, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Delete Confirmation Banner
                AnimatedVisibility(
                    visible = confirmDeleteId == obj.id,
                    enter = fadeIn(tween(160)) + expandVertically(tween(160)),
                    exit = fadeOut(tween(130)) + shrinkVertically(tween(130))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.DangerSoft, RoundedCornerShape(10.dp))
                            .border(1.dp, UrbexColors.Danger.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Удалить объект из локальной базы?",
                            color = Color(0xFFF1ACA7),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Отмена",
                                color = UrbexColors.TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .background(UrbexColors.Elevated, RoundedCornerShape(6.dp))
                                    .urbexBounceClick(0.95f) { confirmDeleteId = null }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                            Text(
                                text = "Удалить",
                                color = UrbexColors.Background,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .background(UrbexColors.Danger, RoundedCornerShape(6.dp))
                                    .urbexBounceClick(0.95f) {
                                        scope.launch {
                                            objectRepository.deleteObject(obj.id)
                                            confirmDeleteId = null
                                            activeObjectId = null
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Header Metadata
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${obj.status.glyph} ${obj.status.label} · ${obj.category.label} · Обновлён ${GeoUtils.formatShortDate(obj.updatedAt)}",
                        color = obj.status.color(),
                        fontSize = if (isLandscape) 11.sp else 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = obj.title,
                        color = UrbexColors.TextPrimary,
                        fontSize = if (isLandscape) 18.sp else 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isLandscape) {
                    // 2-Column Landscape Dossier Top: Media + Telemetry | Status + Map Zone
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (obj.photoUri != null) {
                                AsyncImage(
                                    model = obj.photoUri,
                                    contentDescription = obj.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(165.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(UrbexColors.Surface)
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = UrbexColors.TextDim, modifier = Modifier.size(22.dp))
                                        Text("Локальное фото не добавлено", color = UrbexColors.TextDim, fontSize = 12.sp)
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text("Координаты", color = UrbexColors.TextDim, fontSize = 10.sp)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(13.dp))
                                            Text(
                                                text = GeoUtils.formatCoordinates(obj.lat, obj.lon, settings.coordinateFormat),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = UrbexColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text("Дистанция и азимут", color = UrbexColors.TextDim, fontSize = 10.sp)
                                        Text(
                                            text = "${GeoUtils.formatDistance(distM)} · $azimuth°",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = UrbexColors.TextPrimary
                                        )
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text("Высота", color = UrbexColors.TextDim, fontSize = 10.sp)
                                        Text(
                                            text = "${obj.elevationM} м",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = UrbexColors.TextPrimary
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text("Узел автора", color = UrbexColors.TextDim, fontSize = 10.sp)
                                        Text(
                                            text = obj.authorNodeId,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = UrbexColors.TextPrimary
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Статус достоверности", color = UrbexColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ReliabilityStatus.entries.chunked(2).forEach { rowStatuses ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowStatuses.forEach { st ->
                                            val selected = obj.status == st
                                            Text(
                                                text = "${st.glyph} ${st.label}",
                                                color = if (selected) st.color() else UrbexColors.TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(if (selected) UrbexColors.Elevated else UrbexColors.Surface, RoundedCornerShape(8.dp))
                                                    .border(1.dp, if (selected) st.color() else UrbexColors.Border, RoundedCornerShape(8.dp))
                                                    .urbexBounceClick(0.96f) {
                                                        val updated = obj.copy(status = st, updatedAt = System.currentTimeMillis())
                                                        scope.launch { objectRepository.upsertObject(updated) }
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Зона объекта на карте", color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                if (obj.zone.size >= 3) {
                                    val areaStr = GeoUtils.formatArea(GeoUtils.computePolygonAreaM2(obj.zone))
                                    val perimStr = GeoUtils.formatDistance(GeoUtils.computePolygonPerimeterMeters(obj.zone))
                                    Text(
                                        text = "Точек: ${obj.zone.size} · Площадь: $areaStr · Периметр: $perimStr",
                                        color = UrbexColors.GreenBright,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text(
                                        text = "Периметр не размечен. Вы можете обвести зону на карте.",
                                        color = UrbexColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Button(
                                    onClick = { onEditZoneOnMap(obj) },
                                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.GreenSoft),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                ) {
                                    Text(
                                        text = if (obj.zone.size >= 3) "Изменить зону на карте" else "Задать зону на карте",
                                        color = UrbexColors.TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // 2-Column Landscape Dossier Bottom: Notes | Tactical Parameters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1.15f)
                                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Описание и состояние конструкций", color = UrbexColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(obj.description, color = UrbexColors.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)

                            Text("Подход и периметр", color = UrbexColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(obj.accessNotes, color = UrbexColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)

                            if (obj.hazards.isNotEmpty()) {
                                Text("Факторы риска", color = UrbexColors.Amber, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text(obj.hazards.joinToString(" · "), color = UrbexColors.TextPrimary, fontSize = 12.sp)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Тактические параметры", color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Охрана", color = UrbexColors.TextDim, fontSize = 10.sp)
                                    Text(obj.securityLevel.label, color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Сложность доступа", color = UrbexColors.TextDim, fontSize = 10.sp)
                                    Text(obj.entryDifficulty.label, color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            if (!obj.floorsOrDepth.isNullOrBlank()) {
                                Column {
                                    Text("Этажи / глубина", color = UrbexColors.TextDim, fontSize = 10.sp)
                                    Text(obj.floorsOrDepth.orEmpty(), color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            if (obj.customParams.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    obj.customParams.forEach { p ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(p.key, color = UrbexColors.TextSecondary, fontSize = 11.sp)
                                            Text(p.value, color = UrbexColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Hero Photo or Placeholder
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
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = UrbexColors.TextDim, modifier = Modifier.size(28.dp))
                                Text("Локальное фото не добавлено", color = UrbexColors.TextDim, fontSize = 12.sp)
                            }
                        }
                    }

                    // Telemetry 2x2 Grid
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Координаты", color = UrbexColors.TextDim, fontSize = 11.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = GeoUtils.formatCoordinates(obj.lat, obj.lon, settings.coordinateFormat),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = UrbexColors.TextPrimary
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Дистанция и азимут", color = UrbexColors.TextDim, fontSize = 11.sp)
                                Text(
                                    text = "${GeoUtils.formatDistance(distM)} · $azimuth°",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = UrbexColors.TextPrimary
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Высота", color = UrbexColors.TextDim, fontSize = 11.sp)
                                Text(
                                    text = "${obj.elevationM} м",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = UrbexColors.TextPrimary
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Узел автора", color = UrbexColors.TextDim, fontSize = 11.sp)
                                Text(
                                    text = obj.authorNodeId,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = UrbexColors.TextPrimary
                                )
                            }
                        }
                    }

                    // Reliability Status Selector (2x2 grid matching Web ObjectsView.tsx)
                    Text("Статус достоверности", color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReliabilityStatus.entries.chunked(2).forEach { rowStatuses ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowStatuses.forEach { st ->
                                    val selected = obj.status == st
                                    Text(
                                        text = "${st.glyph} ${st.label}",
                                        color = if (selected) st.color() else UrbexColors.TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(if (selected) UrbexColors.Elevated else UrbexColors.Surface, RoundedCornerShape(8.dp))
                                            .border(1.dp, if (selected) st.color() else UrbexColors.Border, RoundedCornerShape(8.dp))
                                            .urbexBounceClick(0.96f) {
                                                val updated = obj.copy(status = st, updatedAt = System.currentTimeMillis())
                                                scope.launch { objectRepository.upsertObject(updated) }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text("Описание и состояние конструкций", color = UrbexColors.TextSecondary, fontSize = 12.sp)
                    Text(obj.description, color = UrbexColors.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)

                    Text("Подход и периметр", color = UrbexColors.TextSecondary, fontSize = 12.sp)
                    Text(obj.accessNotes, color = UrbexColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)

                    if (obj.hazards.isNotEmpty()) {
                        Text("Факторы риска", color = UrbexColors.Amber, fontSize = 12.sp)
                        Text(obj.hazards.joinToString(" · "), color = UrbexColors.TextPrimary, fontSize = 13.sp)
                    }

                    // Tactical Parameters Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Тактические параметры", color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Охрана", color = UrbexColors.TextDim, fontSize = 11.sp)
                                Text(obj.securityLevel.label, color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Сложность доступа", color = UrbexColors.TextDim, fontSize = 11.sp)
                                Text(obj.entryDifficulty.label, color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (!obj.floorsOrDepth.isNullOrBlank()) {
                            Column {
                                Text("Этажи / глубина", color = UrbexColors.TextDim, fontSize = 11.sp)
                                Text(obj.floorsOrDepth.orEmpty(), color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (obj.customParams.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                obj.customParams.forEach { p ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(p.key, color = UrbexColors.TextSecondary, fontSize = 12.sp)
                                        Text(p.value, color = UrbexColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Map Zone Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Зона объекта на карте", color = UrbexColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (obj.zone.size >= 3) {
                            val areaStr = GeoUtils.formatArea(GeoUtils.computePolygonAreaM2(obj.zone))
                            val perimStr = GeoUtils.formatDistance(GeoUtils.computePolygonPerimeterMeters(obj.zone))
                            Text(
                                text = "Точек: ${obj.zone.size} · Площадь: $areaStr · Периметр: $perimStr",
                                color = UrbexColors.GreenBright,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                text = "Периметр не размечен. Вы можете расставить точки или обвести зону прямо на карте.",
                                color = UrbexColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { onEditZoneOnMap(obj) },
                            colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.GreenSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (obj.zone.size >= 3) "Изменить зону на карте" else "Задать зону на карте",
                                color = UrbexColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else {
            // Objects Browser with unified Toolbar Card + 3 View Modes + Pagination
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UrbexColors.Background)
                    .padding(
                        start = if (isLandscape) 10.dp else 12.dp,
                        top = if (isLandscape) 8.dp else 12.dp,
                        end = if (isLandscape) 10.dp else 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                        .padding(if (isLandscape) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
                ) {
                    if (isLandscape) {
                        // Compact 2-row Landscape Toolbar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UrbexCompactInputField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = "Поиск по объектам...",
                                leadingIcon = Icons.Default.Search,
                                showClearButton = true,
                                minHeight = 36.dp,
                                modifier = Modifier.weight(1f)
                            )

                            Row(
                                modifier = Modifier
                                    .width(235.dp)
                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                ObjectsViewMode.entries.forEach { mode ->
                                    val active = viewMode == mode
                                    Text(
                                        text = mode.label,
                                        color = if (active) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(if (active) UrbexColors.Elevated else Color.Transparent, RoundedCornerShape(6.dp))
                                            .urbexBounceClick(0.96f) { viewModeOverride = mode }
                                            .padding(horizontal = 4.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                                onClick = { showCreateDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Объект", color = UrbexColors.Background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                item(key = "all") {
                                    FilterChipButton(
                                        label = "Все",
                                        selected = statusFilter == null,
                                        compact = true,
                                        onClick = { statusFilter = null }
                                    )
                                }
                                items(ReliabilityStatus.entries, key = { it.name }) { status ->
                                    FilterChipButton(
                                        label = "${status.glyph} ${status.label}",
                                        selected = statusFilter == status,
                                        compact = true,
                                        onClick = { statusFilter = status }
                                    )
                                }
                            }
                            Text(
                                text = "${objects.size}",
                                color = UrbexColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .background(UrbexColors.Background, RoundedCornerShape(6.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        UrbexCompactInputField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Поиск по названию, описанию, подходу...",
                            leadingIcon = Icons.Default.Search,
                            showClearButton = true,
                            minHeight = 40.dp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                ObjectsViewMode.entries.forEach { mode ->
                                    val active = viewMode == mode
                                    Text(
                                        text = mode.label,
                                        color = if (active) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(if (active) UrbexColors.Elevated else Color.Transparent, RoundedCornerShape(6.dp))
                                            .urbexBounceClick(0.96f) { viewModeOverride = mode }
                                            .padding(horizontal = 6.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(16.dp))
                            Text(" Добавить объект", color = UrbexColors.Background, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                    }
                }

                AnimatedContent(
                    targetState = Pair(viewMode, paginated.isEmpty()),
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
                    label = "ObjectsViewModeTransition"
                ) { (mode, isEmpty) ->
                    if (isEmpty) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(bottom = bottomContentPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Объекты по заданному фильтру не найдены.",
                                color = UrbexColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else when (mode) {
                        ObjectsViewMode.LIST -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(if (isLandscape) 2 else 1),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = (if (isLandscape) 12.dp else 24.dp) + bottomContentPadding),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(paginated, key = { it.id }) { item ->
                                    val dist = GeoUtils.haversineDistanceMeters(currentFix.lat, currentFix.lon, item.lat, item.lon)
                                    Row(
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                            .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                                            .urbexBounceClick(0.98f) {
                                                activeObjectId = item.id
                                                confirmDeleteId = null
                                            }
                                            .padding(horizontal = if (isLandscape) 8.dp else 10.dp, vertical = if (isLandscape) 7.dp else 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ObjectThumbnail(photoUri = item.photoUri, title = item.title, size = if (isLandscape) 40.dp else 48.dp)
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(item.title, color = UrbexColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${item.category.label} · ${GeoUtils.formatCoordinates(item.lat, item.lon, settings.coordinateFormat)}", color = UrbexColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text("${item.status.glyph} ${item.status.label}", color = item.status.color(), fontSize = 10.sp)
                                            Text(GeoUtils.formatDistance(dist), color = UrbexColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                        }
                                    }
                                }
                                if (objects.size > paginated.size) {
                                    item(
                                        key = "load-more-list",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        LoadMoreButton(remaining = objects.size - paginated.size) {
                                            visibleLimit += settings.pageSize.coerceAtLeast(8)
                                        }
                                    }
                                }
                            }
                        }

                        ObjectsViewMode.COMPACT -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(if (isLandscape) 2 else 1),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = (if (isLandscape) 12.dp else 24.dp) + bottomContentPadding),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(paginated, key = { it.id }) { item ->
                                    val dist = GeoUtils.haversineDistanceMeters(currentFix.lat, currentFix.lon, item.lat, item.lon)
                                    Row(
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                            .urbexBounceClick(0.98f) {
                                                activeObjectId = item.id
                                                confirmDeleteId = null
                                            }
                                            .padding(if (isLandscape) 9.dp else 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ObjectThumbnail(photoUri = item.photoUri, title = item.title, size = if (isLandscape) 52.dp else 68.dp)
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${item.status.glyph} ${item.status.label}", color = item.status.color(), fontSize = 11.sp)
                                                Text(GeoUtils.formatDistance(dist), color = UrbexColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                            Text(item.title, color = UrbexColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(item.description, color = UrbexColors.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${GeoUtils.formatCoordinates(item.lat, item.lon, settings.coordinateFormat)} · ${item.category.label}", color = UrbexColors.TextDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                if (objects.size > paginated.size) {
                                    item(
                                        key = "load-more-compact",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        LoadMoreButton(remaining = objects.size - paginated.size) {
                                            visibleLimit += settings.pageSize.coerceAtLeast(8)
                                        }
                                    }
                                }
                            }
                        }

                        ObjectsViewMode.CARDS -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = if (isLandscape) 225.dp else 260.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = (if (isLandscape) 12.dp else 24.dp) + bottomContentPadding),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(paginated, key = { it.id }) { item ->
                                    val dist = GeoUtils.haversineDistanceMeters(currentFix.lat, currentFix.lon, item.lat, item.lon)
                                    Column(
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                            .clip(RoundedCornerShape(12.dp))
                                            .urbexBounceClick(0.97f) {
                                                activeObjectId = item.id
                                                confirmDeleteId = null
                                            }
                                    ) {
                                        if (item.photoUri != null) {
                                            AsyncImage(
                                                model = item.photoUri,
                                                contentDescription = item.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(if (isLandscape) 96.dp else 130.dp)
                                                    .background(UrbexColors.Background)
                                            )
                                        }
                                        Column(modifier = Modifier.padding(if (isLandscape) 9.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${item.status.glyph} ${item.status.label}", color = item.status.color(), fontSize = 11.sp)
                                                Text(item.category.label, color = UrbexColors.TextSecondary, fontSize = 11.sp)
                                            }
                                            Text(item.title, color = UrbexColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(item.description, color = UrbexColors.TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(GeoUtils.formatCoordinates(item.lat, item.lon, settings.coordinateFormat), color = UrbexColors.TextDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                Text(GeoUtils.formatDistance(dist), color = UrbexColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                                if (objects.size > paginated.size) {
                                    item(
                                        key = "load-more-cards",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        LoadMoreButton(remaining = objects.size - paginated.size) {
                                            visibleLimit += settings.pageSize.coerceAtLeast(8)
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

    if (showCreateDialog || editingTarget != null) {
        ObjectEditorDialog(
            initialLat = currentFix.lat,
            initialLon = currentFix.lon,
            initialElevationM = currentFix.altitudeM ?: 140,
            defaultZoneRadiusM = settings.defaultZoneRadiusM,
            authorNodeId = profile.nodeId,
            editingObject = editingTarget,
            onDismiss = {
                showCreateDialog = false
                editingTarget = null
            },
            onSave = { savedObj ->
                scope.launch {
                    objectRepository.upsertObject(savedObj)
                    activeObjectId = savedObj.id
                    showCreateDialog = false
                    editingTarget = null
                }
            },
            onOpenZoneEditor = { savedObj ->
                scope.launch {
                    objectRepository.upsertObject(savedObj)
                    activeObjectId = savedObj.id
                    showCreateDialog = false
                    editingTarget = null
                    onEditZoneOnMap(savedObj)
                }
            }
        )
    }
}

@Composable
private fun ObjectThumbnail(photoUri: String?, title: String, size: Dp) {
    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(UrbexColors.Background)
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Image, contentDescription = null, tint = UrbexColors.TextDim, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LoadMoreButton(remaining: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Показать ещё ($remaining)", color = UrbexColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
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
        fontSize = if (compact) 11.sp else 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .urbexBounceClick(0.95f, onClick)
            .padding(
                horizontal = if (compact) 9.dp else 12.dp,
                vertical = if (compact) 5.dp else 8.dp
            )
    )
}

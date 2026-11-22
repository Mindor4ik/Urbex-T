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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ZoomIn
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
import com.urbext.core.ui.ObjectEditorDraft
import com.urbext.core.ui.PhotoZoomDialog
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.UrbexDropdownField
import com.urbext.core.ui.UrbexDropdownOption
import com.urbext.core.ui.color
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexCardGlow
import com.urbext.core.ui.urbexGlow
import com.urbext.core.ui.vector
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.ObjectIcon
import com.urbext.domain.model.ObjectsViewMode
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.PowerProfile
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile
import com.urbext.domain.model.WorkspaceState
import com.urbext.domain.model.ZoneStyle
import com.urbext.domain.model.allPhotos
import com.urbext.domain.model.effectiveIcon
import com.urbext.domain.model.effectiveZoneColor
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.ProfileRepository
import com.urbext.domain.repository.SettingsRepository
import com.urbext.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class ZoomGalleryState(
    val photos: List<String>,
    val initialIndex: Int,
    val title: String
)

@Composable
fun ObjectsScreen(
    objectRepository: ObjectRepository,
    settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository,
    workspaceRepository: WorkspaceRepository? = null,
    initialSelectedId: String? = null,
    resumedEditorDraft: ObjectEditorDraft? = null,
    onConsumeResumedEditorDraft: () -> Unit = {},
    onEditZoneOnMap: (UrbexObject) -> Unit = {},
    onFocusOnMap: (UrbexObject) -> Unit = {},
    onMovePointOnMap: (UrbexObject) -> Unit = {},
    onMovePointFromEditor: (ObjectEditorDraft) -> Unit = {},
    onOpenDbManager: (() -> Unit)? = null,
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
    var resumedDraftState by remember { mutableStateOf<ObjectEditorDraft?>(null) }
    var zoomedGallery by remember { mutableStateOf<ZoomGalleryState?>(null) }

    val objects by objectRepository.observeObjects(statusFilter, query)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val viewMode = viewModeOverride ?: settings.defaultObjectsView
    val profile by profileRepository.observeProfile()
        .collectAsStateWithLifecycle(
            initialValue = UserProfile("local", "Сокол-1", "!a48f20c1", OperatorRole.SCOUT, "", "", 0L)
        )
    val workspaceState by (workspaceRepository?.observeWorkspaces() ?: flowOf(WorkspaceState()))
        .collectAsStateWithLifecycle(initialValue = WorkspaceState())
    val objectSources by (workspaceRepository?.observeObjectSources() ?: flowOf(emptyMap()))
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val activeWorkspaceName = workspaceState.workspaces
        .firstOrNull { it.meta.id == workspaceState.activeDbId }?.meta?.name
    val isMultiMounted = workspaceState.mountedDbIds.size > 1

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

    LaunchedEffect(resumedEditorDraft) {
        val draft = resumedEditorDraft
        if (draft != null) {
            resumedDraftState = draft
            val targetObj = draft.editingObject
            if (targetObj != null) {
                activeObjectId = targetObj.id
                editingTarget = targetObj
                showCreateDialog = false
            } else {
                editingTarget = null
                showCreateDialog = true
            }
            onConsumeResumedEditorDraft()
        }
    }

    LaunchedEffect(settings.editorEnabled) {
        if (!settings.editorEnabled) {
            showCreateDialog = false
            editingTarget = null
            resumedDraftState = null
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
            val objPhotos = remember(obj.id, obj.photoUri, obj.photos) { obj.allPhotos() }
            var activePhotoIdx by remember(obj.id) { mutableIntStateOf(0) }
            val safePhotoIdx = activePhotoIdx.coerceIn(0, (objPhotos.size - 1).coerceAtLeast(0))

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
                // Top Action Bar (uniform height, single-line labels, never wraps "Удалить" across lines)
                val topBtnHeight = if (isLandscape) 34.dp else 38.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .height(topBtnHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.95f) {
                                activeObjectId = null
                                confirmDeleteId = null
                            }
                            .padding(horizontal = if (isLandscape) 10.dp else 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = UrbexColors.TextPrimary, modifier = Modifier.size(15.dp))
                        Text(
                            text = if (isLandscape) "К списку объектов" else "К списку",
                            color = UrbexColors.TextPrimary,
                            fontSize = if (isLandscape) 12.sp else 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (settings.editorEnabled) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(topBtnHeight)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.95f) { editingTarget = obj }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = UrbexColors.TextPrimary, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Редактировать",
                                    color = UrbexColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            if (isLandscape || cfg.screenWidthDp >= 430) {
                                Row(
                                    modifier = Modifier
                                        .height(topBtnHeight)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(UrbexColors.DangerSoft.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                        .border(1.dp, UrbexColors.Danger.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                        .urbexBounceClick(0.95f) {
                                            if (settings.confirmBeforeDelete) {
                                                confirmDeleteId = obj.id
                                            } else {
                                                scope.launch {
                                                    objectRepository.deleteObject(obj.id)
                                                    confirmDeleteId = null
                                                    activeObjectId = null
                                                }
                                            }
                                        }
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = UrbexColors.Danger, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "Удалить",
                                        color = UrbexColors.Danger,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(topBtnHeight)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(UrbexColors.DangerSoft.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                        .border(1.dp, UrbexColors.Danger.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                        .urbexBounceClick(0.94f) {
                                            if (settings.confirmBeforeDelete) {
                                                confirmDeleteId = obj.id
                                            } else {
                                                scope.launch {
                                                    objectRepository.deleteObject(obj.id)
                                                    confirmDeleteId = null
                                                    activeObjectId = null
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить объект", tint = UrbexColors.Danger, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Delete Confirmation Banner
                AnimatedVisibility(
                    visible = settings.editorEnabled && confirmDeleteId == obj.id,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isLandscape) 38.dp else 44.dp)
                            .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                            .border(1.dp, obj.status.color().copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = obj.effectiveIcon().vector(),
                            contentDescription = obj.effectiveIcon().label,
                            tint = obj.status.color(),
                            modifier = Modifier.size(if (isLandscape) 20.dp else 23.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${obj.status.glyph} ${obj.status.label} · ${obj.category.label} · Обновлён ${GeoUtils.formatShortDate(obj.updatedAt)}",
                                color = obj.status.color(),
                                fontSize = if (isLandscape) 11.sp else 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            val detailDbSource = objectSources[obj.id]
                            if (isMultiMounted && detailDbSource != null) {
                                Text(
                                    text = "БД: ${detailDbSource.dbName}",
                                    color = UrbexColors.Amber,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .background(UrbexColors.Amber.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                                        .border(1.dp, UrbexColors.Amber.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = obj.title,
                            color = UrbexColors.TextPrimary,
                            fontSize = if (isLandscape) 18.sp else 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                            if (objPhotos.isNotEmpty()) {
                                val activePhoto = objPhotos[safePhotoIdx]
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(165.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(UrbexColors.Surface)
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                        .urbexBounceClick(0.99f) {
                                            zoomedGallery = ZoomGalleryState(objPhotos, safePhotoIdx, obj.title)
                                        }
                                ) {
                                    AsyncImage(
                                        model = activePhoto,
                                        contentDescription = obj.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize()
                                    )
                                    if (objPhotos.size > 1) {
                                        Text(
                                            text = "${safePhotoIdx + 1} / ${objPhotos.size}",
                                            color = UrbexColors.TextPrimary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp)
                                                .background(Color(0xCC0D110D), RoundedCornerShape(6.dp))
                                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .background(Color(0xCC0D110D), RoundedCornerShape(8.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить", tint = UrbexColors.TextPrimary, modifier = Modifier.size(13.dp))
                                        Text("Приблизить", color = UrbexColors.TextPrimary, fontSize = 11.sp)
                                    }
                                }
                                if (objPhotos.size > 1) {
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        itemsIndexed(objPhotos) { idx, uri ->
                                            val isSelected = idx == safePhotoIdx
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 56.dp, height = 44.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) UrbexColors.GreenBright else UrbexColors.Border,
                                                        shape = RoundedCornerShape(7.dp)
                                                    )
                                                    .urbexBounceClick(0.95f) { activePhotoIdx = idx }
                                            ) {
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = "Фото ${idx + 1}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.matchParentSize()
                                                )
                                            }
                                        }
                                    }
                                }
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
                                        Icon(obj.effectiveIcon().vector(), contentDescription = null, tint = obj.status.color(), modifier = Modifier.size(22.dp))
                                        Text("Локальное фото не добавлено", color = UrbexColors.TextDim, fontSize = 12.sp)
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .urbexCardGlow(cornerRadius = 12.dp)
                                    .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(UrbexColors.Background)
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(7.dp))
                                            .urbexBounceClick(0.96f) { onFocusOnMap(obj) }
                                            .padding(horizontal = 7.dp, vertical = 5.dp),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Координаты", color = UrbexColors.TextDim, fontSize = 10.sp)
                                            Text("На карте →", color = UrbexColors.GreenBright, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.LocationOn, contentDescription = "Перейти к объекту на карте", tint = UrbexColors.Amber, modifier = Modifier.size(13.dp))
                                            Text(
                                                text = GeoUtils.formatDisplayCoordinates(obj.lat, obj.lon, settings.coordinateFormat, settings.maskCoordinatesOnScreen),
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
                                if (settings.editorEnabled) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                            .border(1.dp, UrbexColors.Amber.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                            .urbexBounceClick(0.96f) { onMovePointOnMap(obj) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.OpenWith, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Двигать точку на карте", color = UrbexColors.Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (settings.editorEnabled) {
                                UrbexDropdownField(
                                    selectedValue = obj.status,
                                    label = "Статус достоверности",
                                    compact = true,
                                    options = ReliabilityStatus.entries.map { st ->
                                        UrbexDropdownOption(
                                            value = st,
                                            label = st.label,
                                            accentColor = st.color()
                                        )
                                    },
                                    onSelect = { st ->
                                        val updated = obj.copy(status = st, updatedAt = System.currentTimeMillis())
                                        scope.launch { objectRepository.upsertObject(updated) }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text("Статус достоверности", color = UrbexColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "${obj.status.glyph} ${obj.status.label}",
                                    color = obj.status.color(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                                        .border(1.dp, obj.status.color().copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .urbexCardGlow(
                                        color = if (obj.zone.size >= 3) UrbexColors.GreenBright else UrbexColors.Green,
                                        cornerRadius = 12.dp,
                                        alpha = if (obj.zone.size >= 3) 0.16f else 0.09f
                                    )
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
                                    val zoneHex = obj.effectiveZoneColor()
                                    val zoneDotColor = remember(zoneHex) {
                                        Color(android.graphics.Color.parseColor(zoneHex))
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(zoneDotColor)
                                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                        )
                                        Text(
                                            text = buildString {
                                                append("Стиль: ${obj.zoneStyle.label}")
                                                if (obj.zoneHatched) append(" · Штриховка")
                                                if (obj.zoneColor != null) append(" · $zoneHex")
                                            },
                                            color = UrbexColors.TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Периметр не размечен.",
                                        color = UrbexColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                if (settings.editorEnabled) {
                                    Button(
                                        onClick = { onEditZoneOnMap(obj) },
                                        shape = RoundedCornerShape(9.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.GreenSoft),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .urbexGlow(UrbexColors.GreenBright, radius = 12.dp, cornerRadius = 9.dp, alpha = 0.25f)
                                    ) {
                                        Text(
                                            text = if (obj.zone.size >= 3) "Изменить зону на карте" else "Задать зону на карте",
                                            color = UrbexColors.TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
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
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    obj.customParams.forEach { p ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 9.dp, vertical = 7.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = p.key,
                                                color = UrbexColors.TextDim,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = p.value,
                                                color = UrbexColors.TextPrimary,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Hero Photo Gallery or Placeholder
                    if (objPhotos.isNotEmpty()) {
                        val activePhoto = objPhotos[safePhotoIdx]
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(UrbexColors.Surface)
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                .urbexBounceClick(0.99f) {
                                    zoomedGallery = ZoomGalleryState(objPhotos, safePhotoIdx, obj.title)
                                }
                        ) {
                            AsyncImage(
                                model = activePhoto,
                                contentDescription = obj.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                            if (objPhotos.size > 1) {
                                Text(
                                    text = "${safePhotoIdx + 1} / ${objPhotos.size}",
                                    color = UrbexColors.TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                        .background(Color(0xCC0D110D), RoundedCornerShape(7.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(7.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .background(Color(0xCC0D110D), RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить", tint = UrbexColors.TextPrimary, modifier = Modifier.size(14.dp))
                                Text("Приблизить", color = UrbexColors.TextPrimary, fontSize = 11.sp)
                            }
                        }
                        if (objPhotos.size > 1) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(objPhotos) { idx, uri ->
                                    val isSelected = idx == safePhotoIdx
                                    Box(
                                        modifier = Modifier
                                            .size(width = 68.dp, height = 52.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) UrbexColors.GreenBright else UrbexColors.Border,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .urbexBounceClick(0.95f) { activePhotoIdx = idx }
                                    ) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = "Фото ${idx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    }
                                }
                            }
                        }
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
                                Icon(obj.effectiveIcon().vector(), contentDescription = null, tint = obj.status.color(), modifier = Modifier.size(30.dp))
                                Text("Локальное фото не добавлено", color = UrbexColors.TextDim, fontSize = 12.sp)
                            }
                        }
                    }

                    // Telemetry 2x2 Grid
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .urbexCardGlow(cornerRadius = 12.dp)
                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(UrbexColors.Background)
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.96f) { onFocusOnMap(obj) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Координаты", color = UrbexColors.TextDim, fontSize = 11.sp)
                                    Text("На карте →", color = UrbexColors.GreenBright, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.LocationOn, contentDescription = "Перейти к объекту на карте", tint = UrbexColors.Amber, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = GeoUtils.formatDisplayCoordinates(
                                            obj.lat,
                                            obj.lon,
                                            settings.coordinateFormat,
                                            settings.maskCoordinatesOnScreen
                                        ),
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
                        if (settings.editorEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Amber.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.96f) { onMovePointOnMap(obj) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.OpenWith, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Двигать точку на карте", color = UrbexColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Reliability Status Selector (interactive in Editor Mode, read-only badge otherwise)
                    if (settings.editorEnabled) {
                        UrbexDropdownField(
                            selectedValue = obj.status,
                            label = "Статус достоверности",
                            options = ReliabilityStatus.entries.map { st ->
                                UrbexDropdownOption(
                                    value = st,
                                    label = st.label,
                                    accentColor = st.color()
                                )
                            },
                            onSelect = { st ->
                                val updated = obj.copy(status = st, updatedAt = System.currentTimeMillis())
                                scope.launch { objectRepository.upsertObject(updated) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Статус достоверности", color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${obj.status.glyph} ${obj.status.label}",
                            color = obj.status.color(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                                .border(1.dp, obj.status.color().copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
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
                            .urbexCardGlow(cornerRadius = 12.dp)
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
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                obj.customParams.forEach { p ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = p.key,
                                            color = UrbexColors.TextDim,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = p.value,
                                            color = UrbexColors.TextPrimary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Map Zone Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .urbexCardGlow(
                                color = if (obj.zone.size >= 3) UrbexColors.GreenBright else UrbexColors.Green,
                                cornerRadius = 12.dp,
                                alpha = if (obj.zone.size >= 3) 0.16f else 0.09f
                            )
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
                            val zoneHex = obj.effectiveZoneColor()
                            val zoneDotColor = remember(zoneHex) {
                                Color(android.graphics.Color.parseColor(zoneHex))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(CircleShape)
                                        .background(zoneDotColor)
                                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                )
                                Text(
                                    text = buildString {
                                        append("Стиль: ${obj.zoneStyle.label}")
                                        if (obj.zoneHatched) append(" · Штриховка")
                                        if (obj.zoneColor != null) append(" · $zoneHex")
                                    },
                                    color = UrbexColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Text(
                                text = "Периметр не размечен.",
                                color = UrbexColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        if (settings.editorEnabled) {
                            Button(
                                onClick = { onEditZoneOnMap(obj) },
                                shape = RoundedCornerShape(9.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.GreenSoft),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .urbexGlow(UrbexColors.GreenBright, radius = 12.dp, cornerRadius = 9.dp, alpha = 0.25f)
                            ) {
                                Text(
                                    text = if (obj.zone.size >= 3) "Изменить зону на карте" else "Задать зону на карте",
                                    color = UrbexColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                        .urbexCardGlow(cornerRadius = 12.dp)
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
                                            .urbexGlow(UrbexColors.GreenBright, radius = 7.dp, cornerRadius = 6.dp, alpha = 0.22f, enabled = active)
                                            .background(if (active) UrbexColors.Elevated else Color.Transparent, RoundedCornerShape(6.dp))
                                            .urbexBounceClick(0.96f) { viewModeOverride = mode }
                                            .padding(horizontal = 4.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            if (settings.editorEnabled) {
                                Button(
                                    shape = RoundedCornerShape(9.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                                    onClick = { showCreateDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .urbexGlow(UrbexColors.GreenBright, radius = 12.dp, cornerRadius = 9.dp, alpha = 0.30f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Объект", color = UrbexColors.Background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                }
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
                            if (activeWorkspaceName != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .background(UrbexColors.Background, RoundedCornerShape(6.dp))
                                        .border(1.dp, if (isMultiMounted) UrbexColors.Amber.copy(alpha = 0.5f) else UrbexColors.Border, RoundedCornerShape(6.dp))
                                        .urbexBounceClick(0.95f) { onOpenDbManager?.invoke() }
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = null, tint = if (isMultiMounted) UrbexColors.Amber else UrbexColors.GreenBright, modifier = Modifier.size(12.dp))
                                    Text(
                                        text = if (isMultiMounted) "${activeWorkspaceName.take(14)} +${workspaceState.mountedDbIds.size - 1}" else activeWorkspaceName.take(18),
                                        color = if (isMultiMounted) UrbexColors.Amber else UrbexColors.TextPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        maxLines = 1
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
                                            .urbexGlow(UrbexColors.GreenBright, radius = 7.dp, cornerRadius = 6.dp, alpha = 0.22f, enabled = active)
                                            .background(if (active) UrbexColors.Elevated else Color.Transparent, RoundedCornerShape(6.dp))
                                            .urbexBounceClick(0.96f) { viewModeOverride = mode }
                                            .padding(horizontal = 6.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        if (settings.editorEnabled) {
                            Button(
                                shape = RoundedCornerShape(9.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                                onClick = { showCreateDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .urbexGlow(UrbexColors.GreenBright, radius = 14.dp, cornerRadius = 9.dp, alpha = 0.30f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Добавить объект", color = UrbexColors.Background, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }

                        if (activeWorkspaceName != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isMultiMounted) UrbexColors.Amber.copy(alpha = 0.5f) else UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.97f) { onOpenDbManager?.invoke() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = null, tint = if (isMultiMounted) UrbexColors.Amber else UrbexColors.GreenBright, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = if (isMultiMounted) "Базы: $activeWorkspaceName (+${workspaceState.mountedDbIds.size - 1})" else "БД: $activeWorkspaceName",
                                        color = if (isMultiMounted) UrbexColors.Amber else UrbexColors.TextPrimary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text("${objects.size} тчк", color = UrbexColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
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
                                    val itemPhotos = item.allPhotos()
                                    Row(
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                            .urbexCardGlow(
                                                color = if (item.status == ReliabilityStatus.HAZARDOUS) item.status.color() else UrbexColors.Green,
                                                cornerRadius = 10.dp,
                                                alpha = if (item.status == ReliabilityStatus.HAZARDOUS) 0.18f else 0.08f
                                            )
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
                                        ObjectThumbnail(
                                            photoUri = itemPhotos.firstOrNull(),
                                            photoCount = itemPhotos.size,
                                            title = item.title,
                                            icon = item.effectiveIcon(),
                                            accentColor = item.status.color(),
                                            size = if (isLandscape) 40.dp else 48.dp
                                        )
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(item.title, color = UrbexColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .urbexBounceClick(0.95f) { onFocusOnMap(item) }
                                                    .padding(vertical = 1.dp)
                                            ) {
                                                Icon(Icons.Default.LocationOn, contentDescription = "На карте", tint = UrbexColors.Amber, modifier = Modifier.size(11.dp))
                                                Text(
                                                    text = GeoUtils.formatDisplayCoordinates(item.lat, item.lon, settings.coordinateFormat, settings.maskCoordinatesOnScreen),
                                                    color = UrbexColors.GreenBright,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
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
                                    val itemPhotos = item.allPhotos()
                                    Row(
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                            .urbexCardGlow(
                                                color = if (item.status == ReliabilityStatus.HAZARDOUS) item.status.color() else UrbexColors.Green,
                                                cornerRadius = 12.dp,
                                                alpha = if (item.status == ReliabilityStatus.HAZARDOUS) 0.18f else 0.08f
                                            )
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
                                        ObjectThumbnail(
                                            photoUri = itemPhotos.firstOrNull(),
                                            photoCount = itemPhotos.size,
                                            title = item.title,
                                            icon = item.effectiveIcon(),
                                            accentColor = item.status.color(),
                                            size = if (isLandscape) 52.dp else 68.dp
                                        )
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${item.status.glyph} ${item.status.label}", color = item.status.color(), fontSize = 11.sp)
                                                Text(GeoUtils.formatDistance(dist), color = UrbexColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                            Text(item.title, color = UrbexColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(item.description, color = UrbexColors.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .urbexBounceClick(0.95f) { onFocusOnMap(item) }
                                                    .padding(vertical = 1.dp)
                                            ) {
                                                Icon(Icons.Default.LocationOn, contentDescription = "На карте", tint = UrbexColors.Amber, modifier = Modifier.size(11.dp))
                                                Text(
                                                    text = "${GeoUtils.formatDisplayCoordinates(item.lat, item.lon, settings.coordinateFormat, settings.maskCoordinatesOnScreen)} · ${item.category.label}",
                                                    color = UrbexColors.GreenBright,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
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
                                    val cardSource = objectSources[item.id]
                                    val itemPhotos = item.allPhotos()
                                    val coverPhoto = itemPhotos.firstOrNull()
                                    Column(
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                            .urbexCardGlow(
                                                color = if (item.status == ReliabilityStatus.HAZARDOUS) item.status.color() else UrbexColors.Green,
                                                cornerRadius = 12.dp,
                                                alpha = if (item.status == ReliabilityStatus.HAZARDOUS) 0.18f else 0.08f
                                            )
                                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                            .clip(RoundedCornerShape(12.dp))
                                            .urbexBounceClick(0.97f) {
                                                activeObjectId = item.id
                                                confirmDeleteId = null
                                            }
                                    ) {
                                        if (coverPhoto != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(if (isLandscape) 96.dp else 130.dp)
                                                    .background(UrbexColors.Background)
                                            ) {
                                                AsyncImage(
                                                    model = coverPhoto,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.matchParentSize()
                                                )
                                                if (itemPhotos.size > 1) {
                                                    Text(
                                                        text = "${itemPhotos.size} фото",
                                                        color = UrbexColors.TextPrimary,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .padding(6.dp)
                                                            .background(Color(0xCC0D110D), RoundedCornerShape(6.dp))
                                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .background(Color(0xCC0D110D), RoundedCornerShape(7.dp))
                                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(7.dp))
                                                        .urbexBounceClick(0.9f) {
                                                            zoomedGallery = ZoomGalleryState(itemPhotos, 0, item.title)
                                                        }
                                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить фото", tint = UrbexColors.TextPrimary, modifier = Modifier.size(13.dp))
                                                }
                                            }
                                        }
                                        Column(modifier = Modifier.padding(if (isLandscape) 9.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${item.status.glyph} ${item.status.label}", color = item.status.color(), fontSize = 11.sp)
                                                Text(item.category.label, color = UrbexColors.TextSecondary, fontSize = 11.sp)
                                            }
                                            if (isMultiMounted && cardSource != null) {
                                                Text(
                                                    text = cardSource.dbName,
                                                    color = UrbexColors.Amber,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .background(UrbexColors.Amber.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = item.effectiveIcon().vector(),
                                                    contentDescription = null,
                                                    tint = item.status.color(),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(item.title, color = UrbexColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text(item.description, color = UrbexColors.TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .urbexBounceClick(0.95f) { onFocusOnMap(item) }
                                                        .padding(vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.LocationOn, contentDescription = "На карте", tint = UrbexColors.Amber, modifier = Modifier.size(11.dp))
                                                    Text(
                                                        text = GeoUtils.formatDisplayCoordinates(item.lat, item.lon, settings.coordinateFormat, settings.maskCoordinatesOnScreen),
                                                        color = UrbexColors.GreenBright,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
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

    if (settings.editorEnabled && (showCreateDialog || editingTarget != null || resumedDraftState != null)) {
        ObjectEditorDialog(
            initialLat = currentFix.lat,
            initialLon = currentFix.lon,
            initialElevationM = currentFix.altitudeM ?: 140,
            defaultZoneRadiusM = settings.defaultZoneRadiusM,
            authorNodeId = profile.nodeId,
            editingObject = editingTarget,
            initialDraft = resumedDraftState,
            onDismiss = {
                showCreateDialog = false
                editingTarget = null
                resumedDraftState = null
            },
            onSave = { savedObj ->
                scope.launch {
                    objectRepository.upsertObject(savedObj)
                    activeObjectId = savedObj.id
                    showCreateDialog = false
                    editingTarget = null
                    resumedDraftState = null
                }
            },
            onOpenZoneEditor = { savedObj ->
                scope.launch {
                    objectRepository.upsertObject(savedObj)
                    activeObjectId = savedObj.id
                    showCreateDialog = false
                    editingTarget = null
                    resumedDraftState = null
                    onEditZoneOnMap(savedObj)
                }
            },
            onOpenPointMover = { draft ->
                showCreateDialog = false
                editingTarget = null
                resumedDraftState = null
                onMovePointFromEditor(draft)
            }
        )
    }

    zoomedGallery?.let { gallery ->
        val safeIdx = gallery.initialIndex.coerceIn(0, (gallery.photos.size - 1).coerceAtLeast(0))
        PhotoZoomDialog(
            photoUri = gallery.photos[safeIdx],
            photos = gallery.photos,
            initialIndex = safeIdx,
            title = gallery.title,
            onDismiss = { zoomedGallery = null }
        )
    }
}

@Composable
private fun ObjectThumbnail(
    photoUri: String?,
    photoCount: Int = if (photoUri != null) 1 else 0,
    title: String,
    icon: ObjectIcon = ObjectIcon.PIN,
    accentColor: Color = UrbexColors.GreenBright,
    size: Dp
) {
    if (photoUri != null) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(UrbexColors.Background)
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = photoUri,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            if (photoCount > 1) {
                Text(
                    text = "$photoCount",
                    color = UrbexColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .background(Color(0xCC0D110D), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon.vector(), contentDescription = icon.label, tint = accentColor, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LoadMoreButton(remaining: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        Text("Показать ещё ($remaining)", color = UrbexColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
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
            .urbexGlow(UrbexColors.GreenBright, radius = 9.dp, cornerRadius = 8.dp, alpha = 0.25f, enabled = selected)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .urbexBounceClick(0.95f, onClick)
            .padding(
                horizontal = if (compact) 9.dp else 12.dp,
                vertical = if (compact) 5.dp else 8.dp
            )
    )
}

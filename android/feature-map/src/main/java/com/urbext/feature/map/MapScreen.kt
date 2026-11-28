package com.urbext.feature.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.urbext.core.geo.GeoUtils
import com.urbext.core.geo.MapSearchKind
import com.urbext.core.location.AdaptiveLocationClient
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.UrbexFilledButton
import com.urbext.core.ui.UrbexOutlineButton
import com.urbext.core.ui.ZoneLinePreviewGlyph
import com.urbext.core.ui.ZonePatternPreviewGlyph
import com.urbext.core.ui.color
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexCardGlow
import com.urbext.core.ui.urbexGlow
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.core.ui.vector
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.GeoPoint
import com.urbext.domain.model.GpsFix
import com.urbext.domain.model.MapLayerMode
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.ObjectIcon
import com.urbext.domain.model.OfflineMapSector
import com.urbext.domain.model.PowerProfile
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.SecurityLevel
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.ZONE_COLOR_PRESETS
import com.urbext.domain.model.ZonePattern
import com.urbext.domain.model.ZoneStyle
import com.urbext.domain.model.allPhotos
import com.urbext.domain.model.defaultIcon
import com.urbext.domain.model.defaultZoneHex
import com.urbext.domain.model.effectiveIcon
import com.urbext.domain.model.effectiveZonePattern
import com.urbext.domain.model.normalizeZoneColor
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SATELLITE_STYLE_JSON = """
{
  "version": 8,
  "name": "UrbexSatellite",
  "sources": {
    "esri-satellite": {
      "type": "raster",
      "tiles": [
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
      ],
      "tileSize": 256,
      "maxzoom": 18
    }
  },
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": { "background-color": "#121814" }
    },
    {
      "id": "satellite-layer",
      "type": "raster",
      "source": "esri-satellite",
      "paint": { "raster-brightness-max": 0.85, "raster-contrast": 0.08 }
    }
  ]
}
"""

private const val OFFLINE_DARK_STYLE_JSON = """
{
  "version": 8,
  "name": "UrbexOfflineDark",
  "sources": {},
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": { "background-color": "#0A0D0C" }
    }
  ]
}
"""

data class MapFocusRequest(
    val objectId: String,
    val lat: Double,
    val lon: Double,
    val token: Long
)

data class MapPointMoveRequest(
    val objectId: String?,
    val title: String,
    val status: ReliabilityStatus,
    val category: ObjectCategory,
    val icon: ObjectIcon?,
    val lat: Double,
    val lon: Double,
    val zone: List<GeoPoint> = emptyList(),
    val zoneColor: String? = null,
    val zoneStyle: ZoneStyle = ZoneStyle.SOLID,
    val zonePattern: ZonePattern = ZonePattern.NONE,
    val zoneHatched: Boolean = false,
    val returnToEditor: Boolean = false,
    val token: Long = System.currentTimeMillis()
)

data class MapPointMoveResult(
    val objectId: String?,
    val lat: Double,
    val lon: Double,
    val shiftedZone: List<GeoPoint>?,
    val shiftZoneApplied: Boolean
)

private class MapLifecycleBridge(private val mapView: MapView) {
    private var started = false
    private var resumed = false
    private var destroyed = false

    var hostStarted = false
    var hostResumed = false
    var tabActive = true

    fun sync() {
        if (destroyed) return
        val shouldStart = hostStarted
        val shouldResume = hostStarted && hostResumed && tabActive
        if (shouldStart && !started) {
            mapView.onStart()
            started = true
        }
        if (shouldResume && !resumed) {
            mapView.onResume()
            resumed = true
        } else if (!shouldResume && resumed) {
            mapView.onPause()
            resumed = false
        }
        if (!shouldStart && started) {
            mapView.onStop()
            started = false
        }
    }

    fun destroy() {
        if (destroyed) return
        if (resumed) {
            mapView.onPause()
            resumed = false
        }
        if (started) {
            mapView.onStop()
            started = false
        }
        mapView.onDestroy()
        destroyed = true
    }
}

@Composable
fun MapScreen(
    objectRepository: ObjectRepository,
    settingsRepository: SettingsRepository,
    onOpenObject: (String) -> Unit,
    focusRequest: MapFocusRequest? = null,
    zoneEditingObjectId: String? = null,
    onRequestZoneEdit: (String) -> Unit = {},
    onClearZoneEdit: () -> Unit = {},
    pointMoveRequest: MapPointMoveRequest? = null,
    onRequestPointMove: (UrbexObject) -> Unit = {},
    onCompletePointMove: (MapPointMoveResult) -> Unit = {},
    onCancelPointMove: () -> Unit = {},
    onCursorChange: (Double, Double) -> Unit = { _, _ -> },
    hideHud: Boolean = false,
    isActive: Boolean = true,
    bottomOverlayPadding: Dp = 92.dp
) {
    val context = LocalContext.current
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val sideBySideLandscape = isLandscape && cfg.screenWidthDp >= 700
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val objects by objectRepository.observeObjects().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = AppSettings())

    val locationClient = remember(context) { AdaptiveLocationClient(context) }
    var currentFix by remember { mutableStateOf(locationClient.getLastKnownFixOrDefault()) }
    val cursorLatState = remember { mutableDoubleStateOf(currentFix.lat) }
    val cursorLonState = remember { mutableDoubleStateOf(currentFix.lon) }
    val currentZoomState = remember { mutableDoubleStateOf(14.0) }
    val cameraBearingState = remember { mutableIntStateOf(0) }
    val deviceAzimuthState = remember { mutableStateOf<Float?>(null) }
    var isPollingGps by remember { mutableStateOf(false) }
    var gpsStatusNote by remember { mutableStateOf<String?>(null) }
    val labelsVisibleAtZoom = remember(settings.showObjectLabels) {
        androidx.compose.runtime.derivedStateOf {
            GeoUtils.shouldShowObjectLabels(settings.showObjectLabels, currentZoomState.doubleValue)
        }
    }

    DisposableEffect(context, isActive, settings.showCompassHud) {
        if (!isActive || !settings.showCompassHud) {
            onDispose {}
        } else {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val rotSensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (sm == null || rotSensor == null) {
                onDispose {}
            } else {
                var smoothSin = 0f
                var smoothCos = 1f
                var hasInit = false
                val rotMatrix = FloatArray(9)
                val orientation = FloatArray(3)
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                        SensorManager.getOrientation(rotMatrix, orientation)
                        val rawDeg = ((Math.toDegrees(orientation[0].toDouble()).toFloat() % 360f) + 360f) % 360f
                        val rad = Math.toRadians(rawDeg.toDouble())
                        val s = sin(rad).toFloat()
                        val c = cos(rad).toFloat()
                        if (!hasInit) {
                            smoothSin = s
                            smoothCos = c
                            hasInit = true
                        } else {
                            smoothSin += 0.22f * (s - smoothSin)
                            smoothCos += 0.22f * (c - smoothCos)
                        }
                        val filtered = ((Math.toDegrees(kotlin.math.atan2(smoothSin.toDouble(), smoothCos.toDouble())).toFloat() % 360f) + 360f) % 360f
                        val prev = deviceAzimuthState.value
                        if (prev == null) {
                            deviceAzimuthState.value = filtered
                        } else {
                            val rawDiff = abs(filtered - prev)
                            val delta = if (rawDiff > 180f) 360f - rawDiff else rawDiff
                            if (delta >= 0.8f) {
                                deviceAzimuthState.value = filtered
                            }
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sm.registerListener(listener, rotSensor, SensorManager.SENSOR_DELAY_UI)
                onDispose { sm.unregisterListener(listener) }
            }
        }
    }

    var showContours by remember { mutableStateOf(true) }
    var showSectors by remember { mutableStateOf(true) }
    var toolsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var selectedObjectId by remember { mutableStateOf<String?>(null) }
    var suppressAutoCenter by remember { mutableStateOf(false) }
    var sectorCachedToast by remember { mutableStateOf<String?>(null) }

    val nearbyHazard = remember(objects, currentFix.lat, currentFix.lon, settings.hazardProximityAlertM) {
        if (settings.hazardProximityAlertM <= 0) {
            null
        } else {
            objects
                .filter { it.status == ReliabilityStatus.HAZARDOUS }
                .map { obj -> obj to GeoUtils.haversineDistanceMeters(currentFix.lat, currentFix.lon, obj.lat, obj.lon) }
                .filter { (_, dist) -> dist <= settings.hazardProximityAlertM.toDouble() }
                .minByOrNull { (_, dist) -> dist }
        }
    }

    val selectedObject = remember(objects, selectedObjectId) {
        selectedObjectId?.let { id -> objects.firstOrNull { it.id == id } }
    }
    val zoneEditingObject = remember(objects, zoneEditingObjectId) {
        zoneEditingObjectId?.let { id -> objects.firstOrNull { it.id == id } }
    }
    val activeZoneObj = if (settings.editorEnabled) zoneEditingObject else null
    val currentZoneEditId = activeZoneObj?.id
    val isEditingZone = activeZoneObj != null
    val activeMoveReq = if (settings.editorEnabled && activeZoneObj == null) pointMoveRequest else null
    val isMovingPoint = activeMoveReq != null
    val showNormalHud = !isEditingZone && !isMovingPoint && !hideHud

    // Keyed synchronously on currentZoneEditId so draftZone/style/pattern populate in the exact same
    // composition pass that MapSavedZonesLayer hides the persisted zone (zero 1-frame vanish/flicker).
    var draftZone by remember(currentZoneEditId) { mutableStateOf(activeZoneObj?.zone ?: emptyList()) }
    var draftZoneColor by remember(currentZoneEditId) { mutableStateOf(activeZoneObj?.zoneColor.orEmpty()) }
    var draftZoneStyle by remember(currentZoneEditId) { mutableStateOf(activeZoneObj?.zoneStyle ?: ZoneStyle.SOLID) }
    var draftZonePattern by remember(currentZoneEditId) { mutableStateOf(activeZoneObj?.effectiveZonePattern() ?: ZonePattern.NONE) }
    var zoneDrawMode by remember { mutableStateOf("points") } // "points" | "freehand"
    var zoneHudCollapsed by remember(currentZoneEditId) { mutableStateOf(false) }
    var rawAutoCollapsedForDraw by remember(currentZoneEditId) { mutableStateOf(false) }
    var isAutoCollapsedForDraw by remember(currentZoneEditId) { mutableStateOf(false) }
    var zoneStyleExpanded by remember(currentZoneEditId) { mutableStateOf(false) }

    LaunchedEffect(currentZoneEditId, rawAutoCollapsedForDraw) {
        if (rawAutoCollapsedForDraw) {
            isAutoCollapsedForDraw = true
        } else if (isAutoCollapsedForDraw) {
            delay(110)
            isAutoCollapsedForDraw = false
        }
    }

    var draftPoint by remember(pointMoveRequest?.token) {
        mutableStateOf(pointMoveRequest?.let { GeoPoint(it.lat, it.lon) })
    }
    var nudgeStepM by remember { mutableIntStateOf(5) }
    var shiftZoneWithPoint by remember(pointMoveRequest?.token) { mutableStateOf(true) }
    val cameraTickState = remember { mutableIntStateOf(0) }

    LaunchedEffect(showNormalHud) {
        if (!showNormalHud) {
            toolsOpen = false
            searchOpen = false
        }
    }

    val latestObjects by rememberUpdatedState(objects)
    val latestZoneEditingId by rememberUpdatedState(zoneEditingObjectId)
    val latestZoneDrawMode by rememberUpdatedState(zoneDrawMode)
    val latestPointMoveActive by rememberUpdatedState(isMovingPoint)
    val latestShowZones by rememberUpdatedState(settings.showObjectZones)
    val latestEditorEnabled by rememberUpdatedState(settings.editorEnabled)
    val latestOnCursorChange by rememberUpdatedState(onCursorChange)
    val latestOnAppendDraftPoint by rememberUpdatedState<(GeoPoint) -> Unit> { pt ->
        zoneStyleExpanded = false
        draftZone = draftZone + pt
    }
    val latestOnTapMovePoint by rememberUpdatedState<(GeoPoint) -> Unit> { moved ->
        draftPoint = moved
        cursorLatState.doubleValue = moved.lat
        cursorLonState.doubleValue = moved.lon
    }
    val latestDismissPopups by rememberUpdatedState<() -> Unit> {
        if (searchOpen) searchOpen = false
        if (toolsOpen) toolsOpen = false
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            gpsStatusNote = null
        } else {
            gpsStatusNote = "Разрешение GPS отклонено — используется последняя точка"
        }
    }

    LaunchedEffect(isActive, hasLocationPermission, settings.powerProfile) {
        if (isActive && hasLocationPermission && settings.powerProfile != PowerProfile.MANUAL) {
            locationClient.observeLocationUpdates(settings.powerProfile).collect { fix ->
                currentFix = fix
                gpsStatusNote = null
            }
        }
    }

    LaunchedEffect(sectorCachedToast) {
        if (sectorCachedToast != null) {
            delay(3200)
            sectorCachedToast = null
        }
    }

    val signalLevel = GeoUtils.gpsSignalLevel(currentFix.accuracyM)
    val targetSignalColor = when (signalLevel) {
        GeoUtils.GpsSignalLevel.GOOD -> UrbexColors.GreenBright
        GeoUtils.GpsSignalLevel.MODERATE -> Color(0xFFDF9E43)
        GeoUtils.GpsSignalLevel.POOR -> Color(0xFFDC6860)
    }

    val styleJson = if (settings.mapLayer == MapLayerMode.SATELLITE) SATELLITE_STYLE_JSON else OFFLINE_DARK_STYLE_JSON
    var appliedStyleJson by remember { mutableStateOf<String?>(null) }
    var styleReadyVersion by remember { mutableIntStateOf(0) }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    val gridCache = remember { GridOverlayCache() }
    val patternCache = remember(styleReadyVersion) { ZonePatternImageCache() }

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        val options = MapLibreMapOptions.createFromAttributes(context, null)
            .textureMode(true)
        MapView(context, options).apply {
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (latestPointMoveActive) {
                    cameraTickState.intValue++
                }
            }
            getMapAsync { map ->
                mapInstance = map
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isAttributionEnabled = false
                map.uiSettings.isCompassEnabled = false
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(currentFix.lat, currentFix.lon))
                    .zoom(14.0)
                    .build()

                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        latestDismissPopups()
                    }
                    if (latestPointMoveActive) {
                        cameraTickState.intValue++
                    }
                    if (latestZoneEditingId != null && latestEditorEnabled &&
                        reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
                    ) {
                        rawAutoCollapsedForDraw = true
                    }
                }

                map.addOnCameraMoveListener {
                    val target = map.cameraPosition.target
                    if (target != null) {
                        cursorLatState.doubleValue = target.latitude
                        cursorLonState.doubleValue = target.longitude
                        latestOnCursorChange(target.latitude, target.longitude)
                    }
                    val z = map.cameraPosition.zoom
                    if (abs(z - currentZoomState.doubleValue) >= 0.01) {
                        currentZoomState.doubleValue = z
                    }
                    val nextBearing = ((map.cameraPosition.bearing.roundToInt() % 360) + 360) % 360
                    if (nextBearing != cameraBearingState.intValue) {
                        cameraBearingState.intValue = nextBearing
                    }
                    if (latestPointMoveActive) {
                        cameraTickState.intValue++
                    }
                }

                map.addOnCameraIdleListener {
                    currentZoomState.doubleValue = map.cameraPosition.zoom
                    rawAutoCollapsedForDraw = false
                    if (latestPointMoveActive) {
                        cameraTickState.intValue++
                    }
                }

                map.addOnMapClickListener { latLng ->
                    latestDismissPopups()
                    if (latestZoneEditingId != null && latestEditorEnabled) {
                        if (latestZoneDrawMode == "points") {
                            val pt = GeoPoint(
                                lat = String.format(Locale.US, "%.6f", latLng.latitude).toDouble(),
                                lon = String.format(Locale.US, "%.6f", latLng.longitude).toDouble()
                            )
                            latestOnAppendDraftPoint(pt)
                            return@addOnMapClickListener true
                        }
                        return@addOnMapClickListener false
                    }

                    if (latestPointMoveActive && latestEditorEnabled) {
                        val moved = GeoPoint(
                            lat = String.format(Locale.US, "%.6f", latLng.latitude).toDouble(),
                            lon = String.format(Locale.US, "%.6f", latLng.longitude).toDouble()
                        )
                        latestOnTapMovePoint(moved)
                        return@addOnMapClickListener true
                    }

                    val pt = map.projection.toScreenLocation(latLng)
                    val hitBox = android.graphics.RectF(pt.x - 44f, pt.y - 54f, pt.x + 44f, pt.y + 30f)
                    val hits = map.queryRenderedFeatures(hitBox, OBJECTS_BADGE_LAYER_ID, OBJECTS_DOT_LAYER_ID)
                    val hitObjId = hits.firstOrNull()?.getStringProperty("objectId")
                    if (hitObjId != null && latestObjects.any { it.id == hitObjId }) {
                        selectedObjectId = hitObjId
                        true
                    } else if (latestShowZones) {
                        val polyHit = latestObjects.firstOrNull {
                            it.zone.size >= 3 && GeoUtils.isPointInPolygon(latLng.latitude, latLng.longitude, it.zone)
                        }
                        if (polyHit != null) {
                            selectedObjectId = polyHit.id
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
            }
        }
    }

    LaunchedEffect(settings.editorEnabled) {
        if (!settings.editorEnabled && zoneEditingObjectId != null) {
            onClearZoneEdit()
        }
        if (!settings.editorEnabled && pointMoveRequest != null) {
            onCancelPointMove()
        }
    }

    LaunchedEffect(pointMoveRequest?.token, mapInstance) {
        val req = pointMoveRequest ?: return@LaunchedEffect
        suppressAutoCenter = true
        selectedObjectId = null
        draftPoint = GeoPoint(req.lat, req.lon)
        cameraTickState.intValue++
        val initialCameraPoint = if (!isLandscape) {
            GeoUtils.offsetCoordinate(req.lat, req.lon, -85.0, 0.0)
        } else {
            GeoPoint(req.lat, req.lon)
        }
        mapInstance?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(initialCameraPoint.lat, initialCameraPoint.lon), 16.5)
        )
    }

    LaunchedEffect(currentZoneEditId) {
        val target = activeZoneObj ?: return@LaunchedEffect
        selectedObjectId = null
        mapInstance?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lon), 16.0)
        )
    }

    LaunchedEffect(focusRequest?.token, mapInstance) {
        val req = focusRequest ?: return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        suppressAutoCenter = true
        selectedObjectId = req.objectId
        cursorLatState.doubleValue = req.lat
        cursorLonState.doubleValue = req.lon
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(req.lat, req.lon), 16.5)
        )
    }

    LaunchedEffect(currentFix, settings.autoCenterGps, zoneEditingObjectId, pointMoveRequest?.token, suppressAutoCenter) {
        if (settings.autoCenterGps && zoneEditingObjectId == null && pointMoveRequest == null && !suppressAutoCenter) {
            mapInstance?.animateCamera(
                CameraUpdateFactory.newLatLng(LatLng(currentFix.lat, currentFix.lon))
            )
        }
    }

    val movingHiddenObjectId = if (isMovingPoint) pointMoveRequest?.objectId else null
    val movingZoneHiddenObjectId = if (isMovingPoint && shiftZoneWithPoint) pointMoveRequest?.objectId else null
    val effectiveSelectedObjectId = if (isEditingZone || isMovingPoint) null else selectedObjectId
    val activeDraftColorHex = remember(draftZoneColor, activeZoneObj?.status) {
        normalizeZoneColor(draftZoneColor)
            ?: activeZoneObj?.status?.defaultZoneHex()
            ?: "#5B9D72"
    }

    LaunchedEffect(styleJson) {
        if (appliedStyleJson == styleJson) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(styleJson)) { _ ->
                appliedStyleJson = styleJson
                gridCache.reset()
                styleReadyVersion++
            }
        }
    }

    // Isolated memoized MapLibre overlay layers declared in deterministic bottom-to-top z-order.
    MapEnvironmentLayers(
        mapView = mapView,
        styleReadyVersion = styleReadyVersion,
        offlineSectors = settings.offlineSectors,
        showSectors = showSectors,
        showContours = showContours,
        showMapGrid = settings.showMapGrid,
        lowMemoryMode = settings.lowMemoryMode,
        cursorLatState = cursorLatState,
        cursorLonState = cursorLonState,
        currentZoomState = currentZoomState,
        gridCache = gridCache
    )

    MapSavedZonesLayer(
        mapView = mapView,
        styleReadyVersion = styleReadyVersion,
        objects = objects,
        hiddenZoneObjectId = currentZoneEditId ?: movingZoneHiddenObjectId,
        visible = settings.showObjectZones,
        patternCache = patternCache
    )

    MapGpsLayer(
        mapView = mapView,
        styleReadyVersion = styleReadyVersion,
        fix = currentFix
    )

    MapDraftZoneLayer(
        mapView = mapView,
        styleReadyVersion = styleReadyVersion,
        draftZone = draftZone,
        zoneColorHex = activeDraftColorHex,
        zoneStyle = draftZoneStyle,
        zonePattern = draftZonePattern,
        patternCache = patternCache
    )

    MapPointMoveLayer(
        mapView = mapView,
        styleReadyVersion = styleReadyVersion,
        request = if (isMovingPoint) pointMoveRequest else null,
        draftPoint = draftPoint,
        shiftZoneWithPoint = shiftZoneWithPoint
    )

    MapObjectMarkersLayer(
        context = context,
        mapView = mapView,
        styleReadyVersion = styleReadyVersion,
        objects = objects,
        selectedObjectId = effectiveSelectedObjectId,
        hiddenObjectId = movingHiddenObjectId,
        showLabels = labelsVisibleAtZoom.value,
        patternCache = patternCache
    )

    val lifecycleBridge = remember(mapView) { MapLifecycleBridge(mapView) }

    LaunchedEffect(lifecycleBridge, isActive) {
        lifecycleBridge.tabActive = isActive
        lifecycleBridge.sync()
    }

    DisposableEffect(lifecycleOwner, mapView, lifecycleBridge) {
        lifecycleBridge.hostStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        lifecycleBridge.hostResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        lifecycleBridge.tabActive = isActive
        lifecycleBridge.sync()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    lifecycleBridge.hostStarted = true
                    lifecycleBridge.sync()
                }
                Lifecycle.Event.ON_RESUME -> {
                    lifecycleBridge.hostStarted = true
                    lifecycleBridge.hostResumed = true
                    lifecycleBridge.sync()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    lifecycleBridge.hostResumed = false
                    lifecycleBridge.sync()
                }
                Lifecycle.Event.ON_STOP -> {
                    lifecycleBridge.hostResumed = false
                    lifecycleBridge.hostStarted = false
                    lifecycleBridge.sync()
                }
                Lifecycle.Event.ON_DESTROY -> lifecycleBridge.destroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            lifecycleBridge.destroy()
        }
    }

    // Full-area MapLibre viewport with tactical HUD controls
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UrbexColors.Background)
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        if (activeZoneObj != null && zoneDrawMode == "freehand") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activeZoneObj.id, zoneDrawMode) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                rawAutoCollapsedForDraw = true
                                zoneStyleExpanded = false
                                val map = mapInstance ?: return@detectDragGestures
                                val latLng = map.projection.fromScreenLocation(android.graphics.PointF(offset.x, offset.y))
                                val pt = GeoPoint(
                                    lat = String.format(Locale.US, "%.6f", latLng.latitude).toDouble(),
                                    lon = String.format(Locale.US, "%.6f", latLng.longitude).toDouble()
                                )
                                draftZone = listOf(pt)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val map = mapInstance ?: return@detectDragGestures
                                val latLng = map.projection.fromScreenLocation(android.graphics.PointF(change.position.x, change.position.y))
                                val pt = GeoPoint(
                                    lat = String.format(Locale.US, "%.6f", latLng.latitude).toDouble(),
                                    lon = String.format(Locale.US, "%.6f", latLng.longitude).toDouble()
                                )
                                val last = draftZone.lastOrNull()
                                if (last == null || GeoUtils.haversineDistanceMeters(last.lat, last.lon, pt.lat, pt.lon) >= 5.0) {
                                    draftZone = draftZone + pt
                                }
                            },
                            onDragEnd = {
                                rawAutoCollapsedForDraw = false
                                draftZone = GeoUtils.simplifyPolygonPoints(
                                    draftZone,
                                    minSpacingMeters = 6.0,
                                    maxPoints = 48
                                )
                            },
                            onDragCancel = {
                                rawAutoCollapsedForDraw = false
                            }
                        )
                    }
            )
        }

        // Draggable Target Pin Overlay (Point Move Mode)
        val activeDraftPin = draftPoint
        if (activeMoveReq != null && activeDraftPin != null) {
            MapPointMovePinOverlay(
                activeMoveReq = activeMoveReq,
                draftPoint = activeDraftPin,
                cameraTickState = cameraTickState,
                mapInstance = mapInstance,
                onMoveDraftPoint = { next ->
                    draftPoint = next
                    cursorLatState.doubleValue = next.lat
                    cursorLonState.doubleValue = next.lon
                    cameraTickState.intValue++
                }
            )
        }

        // Subtle Center Camera Crosshair (only during normal map navigation)
        if (showNormalHud) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(1.5.dp)
                        .background(UrbexColors.TextPrimary.copy(alpha = 0.55f))
                )
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(18.dp)
                        .background(UrbexColors.TextPrimary.copy(alpha = 0.55f))
                )
            }
        }

        // Top-Left Compact Telemetry Pill (isolated so 60Hz camera moves never recompose MapScreen)
        MapTopTelemetryHud(
            visible = showNormalHud,
            isLandscape = isLandscape,
            isActive = isActive,
            settings = settings,
            cursorLatState = cursorLatState,
            cursorLonState = cursorLonState,
            currentZoomState = currentZoomState,
            cameraBearingState = cameraBearingState,
            deviceAzimuthState = deviceAzimuthState,
            currentFix = currentFix,
            targetSignalColor = targetSignalColor,
            signalLabel = signalLevel.label,
            gpsStatusNote = gpsStatusNote,
            nearbyHazard = nearbyHazard,
            onResetNorth = {
                mapInstance?.animateCamera(
                    CameraUpdateFactory.bearingTo(0.0),
                    260
                )
                cameraBearingState.intValue = 0
            },
            onSelectHazard = { hazardId -> selectedObjectId = hazardId }
        )

        // Top-Right Unified Zoom/GPS Capsule + Collapsible Map Tools Drawer
        AnimatedVisibility(
            visible = showNormalHud,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(if (isLandscape) 8.dp else 10.dp)
        ) {
            val ctrlSize = if (isLandscape) 34.dp else 38.dp
            val iconSize = if (isLandscape) 17.dp else 19.dp
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = UrbexColors.Surface.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, UrbexColors.Border),
                    shadowElevation = 4.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { mapInstance?.animateCamera(CameraUpdateFactory.zoomIn()) },
                            modifier = Modifier.size(ctrlSize)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Приблизить карту", tint = UrbexColors.TextPrimary, modifier = Modifier.size(iconSize))
                        }
                        HorizontalDivider(color = UrbexColors.Border, modifier = Modifier.width(ctrlSize))
                        IconButton(
                            onClick = { mapInstance?.animateCamera(CameraUpdateFactory.zoomOut()) },
                            modifier = Modifier.size(ctrlSize)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Отдалить карту", tint = UrbexColors.TextPrimary, modifier = Modifier.size(iconSize))
                        }
                        HorizontalDivider(color = UrbexColors.Border, modifier = Modifier.width(ctrlSize))
                        IconButton(
                            onClick = {
                                if (!hasLocationPermission) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    suppressAutoCenter = false
                                    currentFix = locationClient.getLastKnownFixOrDefault(currentFix.lat, currentFix.lon)
                                    mapInstance?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(currentFix.lat, currentFix.lon), 15.0)
                                    )
                                }
                            },
                            modifier = Modifier.size(ctrlSize)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = "Центрировать по GPS", tint = UrbexColors.GreenBright, modifier = Modifier.size(iconSize))
                        }
                    }
                }

                // Map Search Trigger Button
                Box(
                    modifier = Modifier
                        .size(ctrlSize)
                        .urbexGlow(
                            color = UrbexColors.GreenBright,
                            radius = 12.dp,
                            cornerRadius = 12.dp,
                            alpha = 0.28f,
                            enabled = searchOpen
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            (if (searchOpen) UrbexColors.GreenSoft else UrbexColors.Surface).copy(alpha = 0.92f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (searchOpen) UrbexColors.Green else UrbexColors.Border,
                            RoundedCornerShape(12.dp)
                        )
                        .urbexBounceClick(0.92f) {
                            toolsOpen = false
                            searchOpen = !searchOpen
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Поиск по карте",
                        tint = UrbexColors.GreenBright,
                        modifier = Modifier.size(iconSize)
                    )
                }

                // Collapsible Map Tools Trigger Button
                Box(
                    modifier = Modifier
                        .size(ctrlSize)
                        .urbexGlow(
                            color = UrbexColors.GreenBright,
                            radius = 12.dp,
                            cornerRadius = 12.dp,
                            alpha = 0.28f,
                            enabled = toolsOpen
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            (if (toolsOpen) UrbexColors.GreenSoft else UrbexColors.Surface).copy(alpha = 0.92f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (toolsOpen) UrbexColors.Green else UrbexColors.Border,
                            RoundedCornerShape(12.dp)
                        )
                        .urbexBounceClick(0.92f) {
                            searchOpen = false
                            toolsOpen = !toolsOpen
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Инструменты карты",
                        tint = UrbexColors.Amber,
                        modifier = Modifier.size(iconSize)
                    )
                }

                // Collapsible Map Tools Drawer (Layers, Contours, Sectors, GPS, Manual Fix, Cache Sector)
                val drawerEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
                AnimatedVisibility(
                    visible = toolsOpen,
                    enter = fadeIn(tween(160)) +
                        expandVertically(tween(190, easing = drawerEase), expandFrom = Alignment.Top) +
                        scaleIn(
                            initialScale = 0.94f,
                            transformOrigin = TransformOrigin(1f, 0f),
                            animationSpec = tween(180, easing = drawerEase)
                        ),
                    exit = fadeOut(tween(130)) +
                        shrinkVertically(tween(160, easing = drawerEase), shrinkTowards = Alignment.Top) +
                        scaleOut(
                            targetScale = 0.94f,
                            transformOrigin = TransformOrigin(1f, 0f),
                            animationSpec = tween(140, easing = drawerEase)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .width(248.dp)
                            .background(UrbexColors.Surface.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Инструменты карты",
                                color = UrbexColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = settings.powerProfile.label,
                                color = UrbexColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                        HorizontalDivider(color = UrbexColors.Border)

                        MapToolRow(
                            icon = Icons.Default.Public,
                            iconTint = if (settings.mapLayer == MapLayerMode.SATELLITE) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                            label = "Слой карты",
                            statusText = if (settings.mapLayer == MapLayerMode.SATELLITE) "Спутник" else "Вектор",
                            statusColor = UrbexColors.GreenBright,
                            onClick = {
                                val nextLayer = if (settings.mapLayer == MapLayerMode.SATELLITE) {
                                    MapLayerMode.OFFLINE_VECTOR
                                } else {
                                    MapLayerMode.SATELLITE
                                }
                                scope.launch {
                                    settingsRepository.updateSettings(
                                        settings.copy(mapLayer = nextLayer, updatedAt = System.currentTimeMillis())
                                    )
                                }
                            }
                        )
                        MapToolRow(
                            icon = Icons.Default.Layers,
                            iconTint = if (showContours) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                            label = "Изолинии рельефа",
                            statusText = if (showContours) "Вкл" else "Выкл",
                            statusColor = if (showContours) UrbexColors.GreenBright else UrbexColors.TextDim,
                            onClick = { showContours = !showContours }
                        )
                        MapToolRow(
                            icon = Icons.Default.Radar,
                            iconTint = if (showSectors) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                            label = "Офлайн-секторы",
                            statusText = if (showSectors) "Вкл" else "Выкл",
                            statusColor = if (showSectors) UrbexColors.GreenBright else UrbexColors.TextDim,
                            onClick = { showSectors = !showSectors }
                        )
                        MapToolRow(
                            icon = Icons.Default.GridOn,
                            iconTint = if (settings.showMapGrid) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                            label = "Сетка координат",
                            statusText = if (settings.showMapGrid) "Вкл" else "Выкл",
                            statusColor = if (settings.showMapGrid) UrbexColors.GreenBright else UrbexColors.TextDim,
                            onClick = {
                                scope.launch {
                                    settingsRepository.updateSettings(
                                        settings.copy(
                                            showMapGrid = !settings.showMapGrid,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        )

                        HorizontalDivider(color = UrbexColors.Border)

                        MapToolRow(
                            icon = Icons.Default.MyLocation,
                            iconTint = UrbexColors.GreenBright,
                            label = if (isPollingGps) "Опрос GPS..." else "Запросить GPS",
                            onClick = {
                                if (!hasLocationPermission) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    scope.launch {
                                        isPollingGps = true
                                        currentFix = locationClient.getLastKnownFixOrDefault(currentFix.lat, currentFix.lon)
                                        gpsStatusNote = null
                                        delay(650)
                                        isPollingGps = false
                                    }
                                }
                            }
                        )
                        MapToolRow(
                            icon = Icons.Default.Navigation,
                            iconTint = UrbexColors.Amber,
                            label = "Фикс к курсору",
                            onClick = {
                                currentFix = GpsFix(
                                    lat = cursorLatState.doubleValue,
                                    lon = cursorLonState.doubleValue,
                                    altitudeM = currentFix.altitudeM ?: 140,
                                    accuracyM = 12,
                                    timestamp = System.currentTimeMillis(),
                                    source = "manual"
                                )
                                gpsStatusNote = "Ручной фикс установлен"
                                toolsOpen = false
                            }
                        )
                        MapToolRow(
                            icon = Icons.Default.Download,
                            iconTint = UrbexColors.Amber,
                            label = "Кэшировать сектор",
                            onClick = {
                                val now = System.currentTimeMillis()
                                val latVal = cursorLatState.doubleValue
                                val lonVal = cursorLonState.doubleValue
                                val sector = OfflineMapSector(
                                    id = "sec-${now.toString(36)}",
                                    name = String.format(Locale.US, "Сектор %.3f, %.3f", latVal, lonVal),
                                    centerLat = latVal,
                                    centerLon = lonVal,
                                    radiusKm = 3.5,
                                    zoomMin = 12,
                                    zoomMax = 17,
                                    sizeMb = 7.4,
                                    cachedAt = now
                                )
                                scope.launch {
                                    settingsRepository.updateSettings(
                                        settings.copy(
                                            offlineSectors = listOf(sector) + settings.offlineSectors,
                                            updatedAt = now
                                        )
                                    )
                                    sectorCachedToast = "Офлайн-сектор сохранён (${sector.sizeMb} МБ)"
                                    toolsOpen = false
                                }
                            }
                        )
                    }
                }
            }
        }

        // Floating City / Place / Coordinate Search Card
        val searchEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
        AnimatedVisibility(
            visible = showNormalHud && searchOpen,
            enter = fadeIn(tween(160)) +
                expandVertically(tween(200, easing = searchEase), expandFrom = Alignment.Top) +
                scaleIn(
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(1f, 0.12f),
                    animationSpec = tween(190, easing = searchEase)
                ),
            exit = fadeOut(tween(130)) +
                shrinkVertically(tween(170, easing = searchEase), shrinkTowards = Alignment.Top) +
                scaleOut(
                    targetScale = 0.92f,
                    transformOrigin = TransformOrigin(1f, 0.12f),
                    animationSpec = tween(150, easing = searchEase)
                ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = if (isLandscape) 8.dp else 10.dp,
                    end = if (isLandscape) 48.dp else 54.dp,
                    start = 10.dp
                )
        ) {
            MapSearchCard(
                objects = objects,
                isLandscape = isLandscape,
                onSelectResult = { item ->
                    suppressAutoCenter = true
                    searchOpen = false
                    toolsOpen = false
                    cursorLatState.doubleValue = item.lat
                    cursorLonState.doubleValue = item.lon
                    onCursorChange(item.lat, item.lon)
                    mapInstance?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(item.lat, item.lon),
                            item.recommendedZoom
                        ),
                        450
                    )
                    if (item.kind == MapSearchKind.OBJECT && item.objectId != null) {
                        selectedObjectId = item.objectId
                    } else {
                        selectedObjectId = null
                    }
                    sectorCachedToast = "Переход: ${item.title}"
                },
                onClose = { searchOpen = false }
            )
        }

        // Bottom Floating Overlay: Interactive Zone Editor OR clean Selected Object / Toast overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomOverlayPadding)
        ) {
            val activeDraftPt = draftPoint
            var cachedZoneObj by remember { mutableStateOf<UrbexObject?>(null) }
            if (activeZoneObj != null) {
                cachedZoneObj = activeZoneObj
            }
            val zoneHudEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
            AnimatedVisibility(
                visible = activeZoneObj != null && !hideHud,
                enter = fadeIn(tween(210)) + slideInVertically(tween(240, easing = zoneHudEase)) { it / 3 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(180, easing = zoneHudEase)) { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .then(if (isLandscape) Modifier.widthIn(max = 460.dp) else Modifier.fillMaxWidth())
            ) {
                val targetZoneObj = activeZoneObj ?: cachedZoneObj
                if (targetZoneObj != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedVisibility(
                            visible = sectorCachedToast != null,
                            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
                            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(UrbexColors.GreenSoft, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Green, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = sectorCachedToast.orEmpty(),
                                    color = UrbexColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Interactive Compact & Collapsible Zone Drawing HUD (unified smooth container)
                        val normalizedHudColor = normalizeZoneColor(draftZoneColor)
                        val previewHudHex = normalizedHudColor ?: targetZoneObj.status.defaultZoneHex()
                        val previewHudColor = remember(previewHudHex) {
                            runCatching { Color(android.graphics.Color.parseColor(previewHudHex)) }
                                .getOrDefault(UrbexColors.Green)
                        }
                        val presetSwatchColors = remember {
                            ZONE_COLOR_PRESETS.map { (presetHex, _) ->
                                presetHex to runCatching { Color(android.graphics.Color.parseColor(presetHex)) }
                                    .getOrDefault(UrbexColors.Green)
                            }
                        }
                        val canSaveZone = draftZone.isEmpty() || draftZone.size >= 3
                        val saveZoneAction = {
                            if (canSaveZone) {
                                scope.launch {
                                    objectRepository.upsertObject(
                                        targetZoneObj.copy(
                                            zone = draftZone,
                                            zoneColor = normalizeZoneColor(draftZoneColor),
                                            zoneStyle = draftZoneStyle,
                                            zonePattern = draftZonePattern,
                                            zoneHatched = draftZonePattern != ZonePattern.NONE,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                    onClearZoneEdit()
                                }
                            }
                        }
                        val effectiveZoneCollapsed = zoneHudCollapsed || isAutoCollapsedForDraw
                        val zoneMetricsInline = remember(draftZone) {
                            if (draftZone.size >= 3) {
                                val a = GeoUtils.formatArea(GeoUtils.computePolygonAreaM2(draftZone))
                                val p = GeoUtils.formatDistance(GeoUtils.computePolygonPerimeterMeters(draftZone))
                                "${draftZone.size} тчк · $a · P=$p"
                            } else {
                                "${draftZone.size} тчк"
                            }
                        }

                        val cardGlowAlpha by animateFloatAsState(
                            targetValue = if (effectiveZoneCollapsed) 0.16f else 0.19f,
                            animationSpec = tween(durationMillis = 220),
                            label = "zoneCardGlowAlpha"
                        )
                        val metricsColor by animateColorAsState(
                            targetValue = when {
                                draftZone.size in 1..2 -> UrbexColors.Amber
                                effectiveZoneCollapsed -> UrbexColors.GreenBright
                                else -> UrbexColors.TextSecondary
                            },
                            animationSpec = tween(durationMillis = 180),
                            label = "zoneMetricsColor"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .urbexCardGlow(
                                    UrbexColors.GreenBright,
                                    radius = 11.dp,
                                    cornerRadius = 13.dp,
                                    alpha = cardGlowAlpha
                                )
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    UrbexColors.Surface.copy(alpha = 0.95f),
                                    RoundedCornerShape(13.dp)
                                )
                                .border(1.dp, UrbexColors.Green, RoundedCornerShape(13.dp))
                                .padding(horizontal = if (isLandscape) 10.dp else 11.dp, vertical = 9.dp)
                        ) {
                            // Persistent Header Row (fixed left anchor, font size & action height so title never jumps)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(previewHudColor)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = targetZoneObj.title,
                                        color = UrbexColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = zoneMetricsInline,
                                        color = metricsColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        maxLines = 1
                                    )
                                }

                                AnimatedContent(
                                    targetState = effectiveZoneCollapsed to isAutoCollapsedForDraw,
                                    modifier = Modifier.heightIn(min = 30.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                    transitionSpec = {
                                        fadeIn(tween(180, delayMillis = 25)) togetherWith
                                            fadeOut(tween(120)) using
                                            SizeTransform(clip = false) { _, _ ->
                                                tween(durationMillis = 220, easing = zoneHudEase)
                                            }
                                    },
                                    label = "zoneHeaderActions"
                                ) { (collapsed, autoCollapsed) ->
                                    if (collapsed) {
                                        Row(
                                            modifier = Modifier.height(30.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (draftZone.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                                        .urbexBounceClick(0.94f) {
                                                            draftZone = draftZone.dropLast(1)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                                        contentDescription = "Назад",
                                                        tint = UrbexColors.TextPrimary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            if (!autoCollapsed) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (canSaveZone) UrbexColors.Green else UrbexColors.Elevated,
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .urbexBounceClick(0.94f) { saveZoneAction() },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Сохранить зону",
                                                        tint = if (canSaveZone) UrbexColors.Background else UrbexColors.TextDim,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .height(30.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(UrbexColors.Elevated, RoundedCornerShape(8.dp))
                                                        .border(1.dp, UrbexColors.BorderStrong, RoundedCornerShape(8.dp))
                                                        .urbexBounceClick(0.95f) {
                                                            zoneHudCollapsed = false
                                                            rawAutoCollapsedForDraw = false
                                                            isAutoCollapsedForDraw = false
                                                        }
                                                        .padding(horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowUp,
                                                        contentDescription = "Развернуть панель зоны",
                                                        tint = UrbexColors.GreenBright,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = "Панель",
                                                        color = UrbexColors.TextPrimary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .height(30.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                                .urbexBounceClick(0.95f) { zoneHudCollapsed = true }
                                                .padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Свернуть панель зоны",
                                                tint = UrbexColors.TextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Свернуть",
                                                color = UrbexColors.TextSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Smoothly Collapsible & Expandable Zone Editor Body
                            AnimatedVisibility(
                                visible = !effectiveZoneCollapsed,
                                enter = fadeIn(tween(210, delayMillis = 45)) + expandVertically(
                                    animationSpec = tween(durationMillis = 260, easing = zoneHudEase),
                                    expandFrom = Alignment.Bottom
                                ),
                                exit = fadeOut(tween(130)) + shrinkVertically(
                                    animationSpec = tween(durationMillis = 220, easing = zoneHudEase),
                                    shrinkTowards = Alignment.Bottom
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 1.dp)
                                        .heightIn(max = if (isLandscape) 195.dp else 335.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        HudActionPill(
                                            label = "По точкам",
                                            icon = Icons.Default.Place,
                                            accentColor = if (zoneDrawMode == "points") UrbexColors.GreenBright else UrbexColors.TextSecondary,
                                            highlighted = zoneDrawMode == "points",
                                            onClick = { zoneDrawMode = "points" }
                                        )
                                        HudActionPill(
                                            label = "Рисовать",
                                            icon = Icons.Default.Edit,
                                            accentColor = if (zoneDrawMode == "freehand") UrbexColors.GreenBright else UrbexColors.TextSecondary,
                                            highlighted = zoneDrawMode == "freehand",
                                            onClick = { zoneDrawMode = "freehand" }
                                        )
                                        HudActionPill(
                                            label = if (isLandscape) "Круг ${settings.defaultZoneRadiusM}м" else "R=${settings.defaultZoneRadiusM}м",
                                            accentColor = UrbexColors.Amber,
                                            onClick = {
                                                draftZone = GeoUtils.buildZoneCircle(
                                                    targetZoneObj.lat,
                                                    targetZoneObj.lon,
                                                    settings.defaultZoneRadiusM.toDouble()
                                                )
                                            }
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .background(
                                                    if (zoneStyleExpanded) UrbexColors.GreenSoft else UrbexColors.Background,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (zoneStyleExpanded) UrbexColors.Green else UrbexColors.Border,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .urbexBounceClick(0.95f) { zoneStyleExpanded = !zoneStyleExpanded }
                                                .padding(horizontal = 7.dp, vertical = 5.dp)
                                        ) {
                                            if (isLandscape) {
                                                ZoneLinePreviewGlyph(style = draftZoneStyle, color = previewHudColor)
                                                ZonePatternPreviewGlyph(pattern = draftZonePattern, color = previewHudColor)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(previewHudColor)
                                            )
                                            Text(
                                                text = "Стиль",
                                                color = UrbexColors.TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                            Icon(
                                                imageVector = if (zoneStyleExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = UrbexColors.TextSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = zoneStyleExpanded,
                                        enter = fadeIn(tween(140)) + expandVertically(tween(150)),
                                        exit = fadeOut(tween(120)) + shrinkVertically(tween(140))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(UrbexColors.Background, RoundedCornerShape(9.dp))
                                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(9.dp))
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Линия границы", color = UrbexColors.TextSecondary, fontSize = 10.sp)
                                                Text(draftZoneStyle.label, color = UrbexColors.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                ZoneStyle.entries.forEach { st ->
                                                    val selected = draftZoneStyle == st
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier
                                                            .background(
                                                                if (selected) UrbexColors.Elevated else UrbexColors.Surface,
                                                                RoundedCornerShape(8.dp)
                                                            )
                                                            .border(
                                                                1.dp,
                                                                if (selected) UrbexColors.Green else UrbexColors.Border,
                                                                RoundedCornerShape(8.dp)
                                                            )
                                                            .urbexBounceClick(0.95f) { draftZoneStyle = st }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        ZoneLinePreviewGlyph(
                                                            style = st,
                                                            color = if (selected) previewHudColor else UrbexColors.TextSecondary
                                                        )
                                                        Text(
                                                            text = st.label,
                                                            color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                                            fontSize = 10.5.sp,
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
                                                Text(draftZonePattern.label, color = UrbexColors.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                ZonePattern.entries.forEach { pat ->
                                                    val selected = draftZonePattern == pat
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier
                                                            .background(
                                                                if (selected) UrbexColors.GreenSoft else UrbexColors.Surface,
                                                                RoundedCornerShape(8.dp)
                                                            )
                                                            .border(
                                                                1.dp,
                                                                if (selected) UrbexColors.Green else UrbexColors.Border,
                                                                RoundedCornerShape(8.dp)
                                                            )
                                                            .urbexBounceClick(0.95f) { draftZonePattern = pat }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        ZonePatternPreviewGlyph(
                                                            pattern = pat,
                                                            color = if (selected) previewHudColor else UrbexColors.TextSecondary
                                                        )
                                                        Text(
                                                            text = pat.label,
                                                            color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                                            fontSize = 10.5.sp,
                                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Авто",
                                                    color = if (normalizedHudColor == null) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (normalizedHudColor == null) FontWeight.SemiBold else FontWeight.Normal,
                                                    modifier = Modifier
                                                        .background(
                                                            if (normalizedHudColor == null) UrbexColors.Elevated else UrbexColors.Surface,
                                                            RoundedCornerShape(7.dp)
                                                        )
                                                        .border(
                                                            1.dp,
                                                            if (normalizedHudColor == null) UrbexColors.Green else UrbexColors.Border,
                                                            RoundedCornerShape(7.dp)
                                                        )
                                                        .urbexBounceClick(0.94f) { draftZoneColor = "" }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                                presetSwatchColors.forEach { (presetHex, swatchColor) ->
                                                    val isSelected = normalizedHudColor == presetHex
                                                    Box(
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .clip(CircleShape)
                                                            .background(swatchColor)
                                                            .border(
                                                                width = if (isSelected) 2.dp else 1.dp,
                                                                color = if (isSelected) Color.White else UrbexColors.Border,
                                                                shape = CircleShape
                                                            )
                                                            .urbexBounceClick(0.9f) { draftZoneColor = presetHex }
                                                    )
                                                }
                                                UrbexCompactInputField(
                                                    value = draftZoneColor,
                                                    onValueChange = { raw ->
                                                        draftZoneColor = raw.trim().uppercase(Locale.US).take(7)
                                                    },
                                                    placeholder = "#5B9D72",
                                                    minHeight = 28.dp,
                                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp),
                                                    modifier = Modifier.width(92.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (draftZone.size in 1..2) {
                                        Text(
                                            text = "Добавьте ещё ${3 - draftZone.size} тчк для замкнутой зоны.",
                                            color = UrbexColors.Amber,
                                            fontSize = 10.5.sp
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Row(
                                                modifier = Modifier
                                                    .height(33.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                                    .urbexBounceClick(0.95f) {
                                                        if (draftZone.isNotEmpty()) {
                                                            draftZone = draftZone.dropLast(1)
                                                        }
                                                    }
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                                    contentDescription = "Назад",
                                                    tint = if (draftZone.isNotEmpty()) UrbexColors.TextPrimary else UrbexColors.TextDim,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Назад",
                                                    color = if (draftZone.isNotEmpty()) UrbexColors.TextPrimary else UrbexColors.TextDim,
                                                    fontSize = 11.sp,
                                                    maxLines = 1
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(33.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                                    .urbexBounceClick(0.95f) { draftZone = emptyList() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Очистить зону",
                                                    tint = if (draftZone.isNotEmpty()) Color(0xFFF1ACA7) else UrbexColors.TextDim,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .height(33.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                                    .urbexBounceClick(0.95f) { onClearZoneEdit() }
                                                    .padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Отмена",
                                                    color = UrbexColors.TextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .height(33.dp)
                                                    .urbexGlow(UrbexColors.GreenBright, radius = 10.dp, cornerRadius = 8.dp, alpha = 0.28f, enabled = canSaveZone)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (canSaveZone) UrbexColors.Green else UrbexColors.Elevated,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .urbexBounceClick(0.95f) { saveZoneAction() }
                                                    .padding(horizontal = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = if (canSaveZone) UrbexColors.Background else UrbexColors.TextDim,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Сохранить",
                                                    color = if (canSaveZone) UrbexColors.Background else UrbexColors.TextDim,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false
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
            if (activeZoneObj == null && activeMoveReq != null && !hideHud && activeDraftPt != null) {
                val moveDistM = GeoUtils.haversineDistanceMeters(
                    activeMoveReq.lat,
                    activeMoveReq.lon,
                    activeDraftPt.lat,
                    activeDraftPt.lon
                )
                val nudgePoint: (Double, Double) -> Unit = { northM, eastM ->
                    val next = GeoUtils.offsetCoordinate(activeDraftPt.lat, activeDraftPt.lon, northM, eastM)
                    draftPoint = next
                    cursorLatState.doubleValue = next.lat
                    cursorLonState.doubleValue = next.lon
                }
                val finishPointMoveAction: () -> Unit = {
                    val dLat = activeDraftPt.lat - activeMoveReq.lat
                    val dLon = activeDraftPt.lon - activeMoveReq.lon
                    val canShiftZone = activeMoveReq.zone.size >= 3 && shiftZoneWithPoint
                    val shifted = if (canShiftZone) {
                        GeoUtils.translateZone(activeMoveReq.zone, dLat, dLon)
                    } else {
                        null
                    }
                    onCompletePointMove(
                        MapPointMoveResult(
                            objectId = activeMoveReq.objectId,
                            lat = activeDraftPt.lat,
                            lon = activeDraftPt.lon,
                            shiftedZone = shifted,
                            shiftZoneApplied = canShiftZone
                        )
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .then(if (isLandscape) Modifier.widthIn(max = 460.dp) else Modifier.fillMaxWidth())
                        .padding(horizontal = 12.dp)
                        .heightIn(max = if (isLandscape) 268.dp else 440.dp)
                        .urbexCardGlow(UrbexColors.Amber, cornerRadius = 14.dp, alpha = 0.22f)
                        .background(UrbexColors.Surface.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
                        .border(1.dp, UrbexColors.Amber, RoundedCornerShape(14.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(if (isLandscape) 12.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, activeMoveReq.status.color().copy(alpha = 0.65f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = (activeMoveReq.icon ?: activeMoveReq.category.defaultIcon()).vector(),
                                    contentDescription = null,
                                    tint = activeMoveReq.status.color(),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Перемещение точки объекта",
                                    color = UrbexColors.Amber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = activeMoveReq.title.ifBlank { "Новый объект" },
                                    color = UrbexColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Сдвиг ${GeoUtils.formatDistance(moveDistM)}",
                                color = UrbexColors.Amber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = GeoUtils.formatDisplayCoordinates(
                                    activeDraftPt.lat,
                                    activeDraftPt.lon,
                                    settings.coordinateFormat,
                                    settings.maskCoordinatesOnScreen
                                ),
                                color = UrbexColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Text(
                        text = "Перетащите маркер, нажмите на карту или используйте точный сдвиг",
                        color = UrbexColors.TextSecondary,
                        fontSize = 11.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(1, 5, 25).forEach { step ->
                            val selectedStep = nudgeStepM == step
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(
                                        if (selectedStep) UrbexColors.GreenSoft else UrbexColors.Background,
                                        RoundedCornerShape(7.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (selectedStep) UrbexColors.Green else UrbexColors.Border,
                                        RoundedCornerShape(7.dp)
                                    )
                                    .urbexBounceClick(0.95f) { nudgeStepM = step },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$step м",
                                    color = if (selectedStep) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val stepD = nudgeStepM.toDouble()
                        listOf(
                            Triple("С", stepD, 0.0),
                            Triple("Ю", -stepD, 0.0),
                            Triple("З", 0.0, -stepD),
                            Triple("В", 0.0, stepD)
                        ).forEach { (dirLabel, dN, dE) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.94f) { nudgePoint(dN, dE) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$dirLabel ${nudgeStepM}м",
                                    color = UrbexColors.TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                .urbexBounceClick(0.95f) {
                                    val centerTarget = mapInstance?.cameraPosition?.target
                                    val latVal = centerTarget?.latitude ?: cursorLatState.doubleValue
                                    val lonVal = centerTarget?.longitude ?: cursorLonState.doubleValue
                                    val next = GeoPoint(
                                        lat = String.format(Locale.US, "%.6f", latVal).toDouble(),
                                        lon = String.format(Locale.US, "%.6f", lonVal).toDouble()
                                    )
                                    draftPoint = next
                                    cursorLatState.doubleValue = next.lat
                                    cursorLonState.doubleValue = next.lon
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("По центру", color = UrbexColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                .urbexBounceClick(0.95f) {
                                    val resetPt = GeoPoint(activeMoveReq.lat, activeMoveReq.lon)
                                    draftPoint = resetPt
                                    cursorLatState.doubleValue = resetPt.lat
                                    cursorLonState.doubleValue = resetPt.lon
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Сброс", color = UrbexColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                        }
                    }

                    if (activeMoveReq.zone.size >= 3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (shiftZoneWithPoint) UrbexColors.GreenSoft.copy(alpha = 0.55f) else UrbexColors.Background,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (shiftZoneWithPoint) UrbexColors.Green else UrbexColors.Border,
                                    RoundedCornerShape(8.dp)
                                )
                                .urbexBounceClick(0.98f) { shiftZoneWithPoint = !shiftZoneWithPoint }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        if (shiftZoneWithPoint) UrbexColors.Green else UrbexColors.Surface,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (shiftZoneWithPoint) UrbexColors.GreenBright else UrbexColors.Border,
                                        RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (shiftZoneWithPoint) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = UrbexColors.Background,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Сдвигать зону вместе с точкой (${activeMoveReq.zone.size} тчк)",
                                color = UrbexColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                .urbexBounceClick(0.95f) { onCancelPointMove() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Отмена", color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                        }
                        Row(
                            modifier = Modifier
                                .weight(1.25f)
                                .height(36.dp)
                                .urbexGlow(UrbexColors.GreenBright, radius = 10.dp, cornerRadius = 8.dp, alpha = 0.30f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(UrbexColors.Green, RoundedCornerShape(8.dp))
                                .urbexBounceClick(0.95f) { finishPointMoveAction() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = UrbexColors.Background,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (activeMoveReq.returnToEditor) "Применить точку" else "Сохранить точку",
                                color = UrbexColors.Background,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            } else if (showNormalHud && sideBySideLandscape) {
                // Wide Landscape PDA: Toast at BottomStart, Compact Object Card at BottomEnd
                AnimatedVisibility(
                    visible = sectorCachedToast != null,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(UrbexColors.GreenSoft, RoundedCornerShape(8.dp))
                            .border(1.dp, UrbexColors.Green, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = sectorCachedToast.orEmpty(),
                            color = UrbexColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                AnimatedContent(
                    targetState = selectedObject,
                    contentKey = { it?.id },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .widthIn(max = 355.dp)
                        .padding(end = 10.dp),
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.96f, animationSpec = tween(200)) + slideInVertically(tween(200)) { it / 4 })
                            .togetherWith(
                                fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150)) + slideOutVertically(tween(150)) { it / 4 }
                            )
                    },
                    label = "MapSelectedObjectCardLandscape"
                ) { activeObj ->
                    if (activeObj != null) {
                        SelectedObjectMapCard(
                            activeObj = activeObj,
                            currentFix = currentFix,
                            compactLandscape = true,
                            canEdit = settings.editorEnabled,
                            onDismiss = { selectedObjectId = null },
                            onRequestPointMove = { onRequestPointMove(activeObj) },
                            onRequestZoneEdit = { onRequestZoneEdit(activeObj.id) },
                            onOpenObject = { onOpenObject(activeObj.id) }
                        )
                    }
                }
            } else if (showNormalHud) {
                // Portrait & Compact Narrow Bottom Stack (Toast + Object Card only)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(
                        visible = sectorCachedToast != null,
                        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
                        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(UrbexColors.GreenSoft, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Green, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = sectorCachedToast.orEmpty(),
                                color = UrbexColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = selectedObject,
                        contentKey = { it?.id },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(initialScale = 0.96f, animationSpec = tween(200)) + slideInVertically(tween(200)) { it / 4 })
                                .togetherWith(
                                    fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150)) + slideOutVertically(tween(150)) { it / 4 }
                                )
                        },
                        label = "MapSelectedObjectCardPortrait"
                    ) { activeObj ->
                        if (activeObj != null) {
                            SelectedObjectMapCard(
                                activeObj = activeObj,
                                currentFix = currentFix,
                                compactLandscape = isLandscape,
                                canEdit = settings.editorEnabled,
                                onDismiss = { selectedObjectId = null },
                                onRequestPointMove = { onRequestPointMove(activeObj) },
                                onRequestZoneEdit = { onRequestZoneEdit(activeObj.id) },
                                onOpenObject = { onOpenObject(activeObj.id) }
                            )
                        }
                    }
                }
            }
        }

        if (!isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun MapToolRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    statusText: String? = null,
    statusColor: Color = UrbexColors.TextSecondary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
            .urbexBounceClick(0.96f, onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = UrbexColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (statusText != null) {
            Text(
                text = statusText,
                color = statusColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HudActionPill(
    label: String,
    accentColor: Color,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .urbexGlow(UrbexColors.GreenBright, radius = 9.dp, cornerRadius = 8.dp, alpha = 0.25f, enabled = highlighted)
            .background(
                (if (highlighted) UrbexColors.GreenSoft else UrbexColors.Surface).copy(alpha = 0.92f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (highlighted) UrbexColors.Green else UrbexColors.Border,
                RoundedCornerShape(8.dp)
            )
            .urbexBounceClick(0.94f, onClick)
            .padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 5.dp else 7.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(if (compact) 13.dp else 14.dp)
            )
        }
        Text(
            text = label,
            color = UrbexColors.TextPrimary,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun SelectedObjectMapCard(
    activeObj: UrbexObject,
    currentFix: GpsFix,
    compactLandscape: Boolean,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onRequestPointMove: () -> Unit = {},
    onRequestZoneEdit: () -> Unit,
    onOpenObject: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val distM = remember(currentFix.lat, currentFix.lon, activeObj.lat, activeObj.lon) {
        GeoUtils.haversineDistanceMeters(currentFix.lat, currentFix.lon, activeObj.lat, activeObj.lon)
    }
    val azDeg = remember(currentFix.lat, currentFix.lon, activeObj.lat, activeObj.lon) {
        GeoUtils.bearingDegrees(currentFix.lat, currentFix.lon, activeObj.lat, activeObj.lon)
    }
    val cardPhotos = remember(activeObj.id, activeObj.photoUri, activeObj.photos) {
        activeObj.allPhotos()
    }
    val zoneMetrics = remember(activeObj.id, activeObj.zone) {
        if (activeObj.zone.size >= 3) {
            GeoUtils.formatArea(GeoUtils.computePolygonAreaM2(activeObj.zone)) to
                GeoUtils.formatDistance(GeoUtils.computePolygonPerimeterMeters(activeObj.zone))
        } else {
            null
        }
    }
    val coverPhoto = cardPhotos.firstOrNull()
    val thumbSize = if (compactLandscape) 62.dp else 68.dp
    val thumbPx = with(density) { thumbSize.roundToPx().coerceAtLeast(1) }
    val thumbRequest = remember(context, coverPhoto, thumbPx) {
        coverPhoto?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(thumbPx, thumbPx)
                .crossfade(false)
                .build()
        }
    }

    val isHazardous = activeObj.status == ReliabilityStatus.HAZARDOUS
    val statusColor = activeObj.status.color()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = if (compactLandscape) 200.dp else 228.dp)
            .urbexCardGlow(
                color = statusColor,
                cornerRadius = 12.dp,
                alpha = 0.18f,
                enabled = isHazardous
            )
            .background(UrbexColors.Surface.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .border(1.dp, if (isHazardous) statusColor.copy(alpha = 0.55f) else UrbexColors.Border, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(if (compactLandscape) 10.dp else 11.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 5.dp else 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compactLandscape) 9.dp else 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (thumbRequest != null) {
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .clip(RoundedCornerShape(9.dp))
                        .background(UrbexColors.Background)
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(9.dp))
                ) {
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = activeObj.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    if (cardPhotos.size > 1) {
                        Text(
                            text = if (compactLandscape) "${cardPhotos.size}" else "${cardPhotos.size} фото",
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
                        .size(if (compactLandscape) 38.dp else 42.dp)
                        .background(UrbexColors.Background, RoundedCornerShape(9.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.55f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = activeObj.effectiveIcon().vector(),
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(if (compactLandscape) 18.dp else 20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val catText = if (!activeObj.subtype.isNullOrBlank()) {
                    "${activeObj.category.label} · ${activeObj.subtype}"
                } else {
                    activeObj.category.label
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = activeObj.effectiveIcon().vector(),
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${activeObj.status.glyph} ${activeObj.status.label} · $catText",
                        color = statusColor,
                        fontSize = if (compactLandscape) 10.sp else 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = activeObj.title,
                    color = UrbexColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compactLandscape) 13.sp else 14.sp,
                    lineHeight = if (compactLandscape) 16.sp else 17.sp,
                    maxLines = if (compactLandscape) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = GeoUtils.formatDistance(distM),
                        color = UrbexColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "$azDeg°",
                        color = UrbexColors.Amber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    if (!compactLandscape && activeObj.securityLevel != SecurityLevel.NONE) {
                        Text(
                            text = "· ${activeObj.securityLevel.label}",
                            color = UrbexColors.Amber,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (zoneMetrics != null) {
                    val (areaStr, perimStr) = zoneMetrics
                    Text(
                        text = if (compactLandscape) "Зона: $areaStr" else "Зона: $areaStr · P=$perimStr",
                        color = UrbexColors.GreenBright,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (compactLandscape && activeObj.securityLevel != SecurityLevel.NONE) {
                    Text(
                        text = "Режим: ${activeObj.securityLevel.label}",
                        color = UrbexColors.Amber,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Скрыть",
                    tint = UrbexColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (!compactLandscape && activeObj.description.isNotBlank()) {
            Text(
                text = activeObj.description,
                color = UrbexColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val actionHeight = 34.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canEdit) {
                UrbexOutlineButton(
                    onClick = onRequestPointMove,
                    height = actionHeight,
                    cornerRadius = 9.dp,
                    borderColor = UrbexColors.Amber,
                    glowColor = UrbexColors.Amber,
                    glowRadius = 10.dp,
                    glowAlpha = 0.24f,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenWith,
                        contentDescription = null,
                        tint = UrbexColors.Amber,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Точка", color = UrbexColors.Amber, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                UrbexOutlineButton(
                    onClick = onRequestZoneEdit,
                    height = actionHeight,
                    cornerRadius = 9.dp,
                    borderColor = UrbexColors.Green,
                    glowColor = UrbexColors.GreenBright,
                    glowRadius = 10.dp,
                    glowAlpha = 0.24f,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text("Зона", color = UrbexColors.GreenBright, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }
            UrbexFilledButton(
                onClick = onOpenObject,
                height = actionHeight,
                cornerRadius = 9.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (compactLandscape) "Открыть" else "Открыть карточку",
                    color = UrbexColors.Background,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MapIconControl(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    highlighted: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = (if (highlighted) UrbexColors.GreenSoft else UrbexColors.Surface).copy(alpha = 0.93f),
        animationSpec = tween(160),
        label = "ctrlBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) UrbexColors.Green else UrbexColors.Border,
        animationSpec = tween(160),
        label = "ctrlBorder"
    )
    val interaction = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(if (compact) 34.dp else 42.dp)
            .urbexScaleOnPress(interaction, 0.91f)
            .urbexGlow(UrbexColors.GreenBright, radius = 10.dp, cornerRadius = 10.dp, alpha = 0.26f, enabled = highlighted)
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(if (compact) 17.dp else 20.dp)
        )
    }
}


package com.urbext.feature.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import com.urbext.core.location.AdaptiveLocationClient
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.UrbexFilledButton
import com.urbext.core.ui.UrbexOutlineButton
import com.urbext.core.ui.ZoneLinePreviewGlyph
import com.urbext.core.ui.ZonePatternPreviewGlyph
import com.urbext.core.ui.color
import com.urbext.core.ui.drawObjectIconOnCanvas
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
import com.urbext.domain.model.effectiveZoneColor
import com.urbext.domain.model.effectiveZonePattern
import com.urbext.domain.model.isOutlineOnly
import com.urbext.domain.model.normalizeZoneColor
import com.urbext.domain.model.normalizeZonePattern
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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillPattern
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
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

private const val GRID_SOURCE_ID = "urbex-grid-source"
private const val GRID_LINE_LAYER_ID = "urbex-grid-line"
private const val CONTOURS_SOURCE_ID = "urbex-contours-source"
private const val CONTOURS_LINE_LAYER_ID = "urbex-contours-line"
private const val SECTORS_SOURCE_ID = "urbex-sectors-source"
private const val SECTORS_LINE_LAYER_ID = "urbex-sectors-line"
private const val GPS_ACCURACY_SOURCE_ID = "urbex-gps-accuracy-source"
private const val GPS_ACCURACY_FILL_LAYER_ID = "urbex-gps-accuracy-fill"
private const val GPS_ACCURACY_LINE_LAYER_ID = "urbex-gps-accuracy-line"
private const val GPS_CENTER_SOURCE_ID = "urbex-gps-center-source"
private const val GPS_CENTER_LAYER_ID = "urbex-gps-center-dot"
private const val OBJECTS_SOURCE_ID = "urbex-objects-source"
private const val OBJECTS_DOT_LAYER_ID = "urbex-objects-dots"
private const val OBJECTS_BADGE_LAYER_ID = "urbex-objects-badges"
private const val OBJECT_ZONES_SOURCE_ID = "urbex-object-zones-source"
private const val OBJECT_ZONES_FILL_LAYER_ID = "urbex-object-zones-fill"
private const val OBJECT_ZONES_HATCH_LAYER_ID = "urbex-object-zones-hatch"
private const val OBJECT_ZONES_LINE_SOLID_LAYER_ID = "urbex-object-zones-line-solid"
private const val OBJECT_ZONES_LINE_BOLD_LAYER_ID = "urbex-object-zones-line-bold"
private const val OBJECT_ZONES_LINE_GLOW_HALO_LAYER_ID = "urbex-object-zones-line-glow-halo"
private const val OBJECT_ZONES_LINE_GLOW_CORE_LAYER_ID = "urbex-object-zones-line-glow-core"
private const val OBJECT_ZONES_LINE_DOUBLE_OUTER_LAYER_ID = "urbex-object-zones-line-double-outer"
private const val OBJECT_ZONES_LINE_DOUBLE_INNER_LAYER_ID = "urbex-object-zones-line-double-inner"
private const val OBJECT_ZONES_LINE_DASHED_LAYER_ID = "urbex-object-zones-line-dashed"
private const val OBJECT_ZONES_LINE_DOTTED_LAYER_ID = "urbex-object-zones-line-dotted"
private const val OBJECT_ZONES_LINE_DASH_DOT_LAYER_ID = "urbex-object-zones-line-dash-dot"
private const val OBJECT_ZONES_LINE_LONG_DASH_LAYER_ID = "urbex-object-zones-line-long-dash"
private const val ZONE_DRAFT_SOURCE_ID = "urbex-zone-draft-source"
private const val ZONE_DRAFT_FILL_LAYER_ID = "urbex-zone-draft-fill"
private const val ZONE_DRAFT_HATCH_LAYER_ID = "urbex-zone-draft-hatch"
private const val ZONE_EMPTY_PATTERN_ID = "urbex-pat-empty"
private const val ZONE_DRAFT_LINE_SOLID_LAYER_ID = "urbex-zone-draft-line-solid"
private const val ZONE_DRAFT_LINE_BOLD_LAYER_ID = "urbex-zone-draft-line-bold"
private const val ZONE_DRAFT_LINE_GLOW_HALO_LAYER_ID = "urbex-zone-draft-line-glow-halo"
private const val ZONE_DRAFT_LINE_GLOW_CORE_LAYER_ID = "urbex-zone-draft-line-glow-core"
private const val ZONE_DRAFT_LINE_DOUBLE_OUTER_LAYER_ID = "urbex-zone-draft-line-double-outer"
private const val ZONE_DRAFT_LINE_DOUBLE_INNER_LAYER_ID = "urbex-zone-draft-line-double-inner"
private const val ZONE_DRAFT_LINE_DASHED_LAYER_ID = "urbex-zone-draft-line-dashed"
private const val ZONE_DRAFT_LINE_DOTTED_LAYER_ID = "urbex-zone-draft-line-dotted"
private const val ZONE_DRAFT_LINE_DASH_DOT_LAYER_ID = "urbex-zone-draft-line-dash-dot"
private const val ZONE_DRAFT_LINE_LONG_DASH_LAYER_ID = "urbex-zone-draft-line-long-dash"
private const val ZONE_DRAFT_VERTEX_LAYER_ID = "urbex-zone-draft-vertices"
private const val POINT_MOVE_SOURCE_ID = "urbex-point-move-source"
private const val POINT_MOVE_ZONE_FILL_LAYER_ID = "urbex-point-move-zone-fill"
private const val POINT_MOVE_ZONE_LINE_LAYER_ID = "urbex-point-move-zone-line"
private const val POINT_MOVE_TETHER_LAYER_ID = "urbex-point-move-tether"
private const val POINT_MOVE_ORIGIN_LAYER_ID = "urbex-point-move-origin"
private const val POINT_MOVE_TARGET_HALO_LAYER_ID = "urbex-point-move-target-halo"
private const val POINT_MOVE_TARGET_LAYER_ID = "urbex-point-move-target"
private const val POINT_MOVE_TARGET_CENTER_LAYER_ID = "urbex-point-move-target-center"

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
    var cursorLat by remember { mutableDoubleStateOf(currentFix.lat) }
    var cursorLon by remember { mutableDoubleStateOf(currentFix.lon) }
    var currentZoom by remember { mutableDoubleStateOf(14.0) }
    var cameraBearing by remember { mutableIntStateOf(0) }
    val deviceAzimuthState = remember { mutableStateOf<Float?>(null) }
    var isPollingGps by remember { mutableStateOf(false) }
    var gpsStatusNote by remember { mutableStateOf<String?>(null) }

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

    var draftZone by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var draftZoneColor by remember { mutableStateOf("") }
    var draftZoneStyle by remember { mutableStateOf(ZoneStyle.SOLID) }
    var draftZonePattern by remember { mutableStateOf(ZonePattern.NONE) }
    var zoneDrawMode by remember { mutableStateOf("points") } // "points" | "freehand"
    var zoneHudCollapsed by remember { mutableStateOf(false) }
    var isAutoCollapsedForDraw by remember { mutableStateOf(false) }
    var zoneStyleExpanded by remember { mutableStateOf(false) }

    var draftPoint by remember(pointMoveRequest?.token) {
        mutableStateOf(pointMoveRequest?.let { GeoPoint(it.lat, it.lon) })
    }
    var nudgeStepM by remember { mutableIntStateOf(5) }
    var shiftZoneWithPoint by remember(pointMoveRequest?.token) { mutableStateOf(true) }
    var cameraTick by remember { mutableIntStateOf(0) }

    val selectedObject = remember(objects, selectedObjectId) {
        selectedObjectId?.let { id -> objects.firstOrNull { it.id == id } }
    }
    val zoneEditingObject = remember(objects, zoneEditingObjectId) {
        zoneEditingObjectId?.let { id -> objects.firstOrNull { it.id == id } }
    }
    val activeZoneObj = if (settings.editorEnabled) zoneEditingObject else null
    val isEditingZone = activeZoneObj != null
    val activeMoveReq = if (settings.editorEnabled && activeZoneObj == null) pointMoveRequest else null
    val isMovingPoint = activeMoveReq != null
    val showNormalHud = !isEditingZone && !isMovingPoint && !hideHud

    LaunchedEffect(showNormalHud) {
        if (!showNormalHud) {
            toolsOpen = false
        }
    }

    val latestObjects by rememberUpdatedState(objects)
    val latestZoneEditingId by rememberUpdatedState(zoneEditingObjectId)
    val latestZoneDrawMode by rememberUpdatedState(zoneDrawMode)
    val latestPointMoveActive by rememberUpdatedState(isMovingPoint)
    val latestShowZones by rememberUpdatedState(settings.showObjectZones)
    val latestEditorEnabled by rememberUpdatedState(settings.editorEnabled)
    val latestOnCursorChange by rememberUpdatedState(onCursorChange)

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
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    val gridCache = remember { GridOverlayCache() }

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        val options = MapLibreMapOptions.createFromAttributes(context, null)
            .textureMode(true)
        MapView(context, options).apply {
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (latestPointMoveActive) {
                    cameraTick++
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
                    if (latestPointMoveActive) {
                        cameraTick++
                    }
                    if (latestZoneEditingId != null && latestEditorEnabled &&
                        reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
                    ) {
                        isAutoCollapsedForDraw = true
                    }
                }

                map.addOnCameraMoveListener {
                    val target = map.cameraPosition.target
                    if (target != null) {
                        cursorLat = target.latitude
                        cursorLon = target.longitude
                        latestOnCursorChange(target.latitude, target.longitude)
                    }
                    val z = map.cameraPosition.zoom
                    if (abs(z - currentZoom) >= 0.01) {
                        currentZoom = z
                    }
                    val nextBearing = ((map.cameraPosition.bearing.roundToInt() % 360) + 360) % 360
                    if (nextBearing != cameraBearing) {
                        cameraBearing = nextBearing
                    }
                    if (latestPointMoveActive) {
                        cameraTick++
                    }
                }

                map.addOnCameraIdleListener {
                    currentZoom = map.cameraPosition.zoom
                    isAutoCollapsedForDraw = false
                    if (latestPointMoveActive) {
                        cameraTick++
                    }
                }

                map.addOnMapClickListener { latLng ->
                    if (latestZoneEditingId != null && latestEditorEnabled) {
                        if (latestZoneDrawMode == "points") {
                            zoneStyleExpanded = false
                            val pt = GeoPoint(
                                lat = String.format(Locale.US, "%.6f", latLng.latitude).toDouble(),
                                lon = String.format(Locale.US, "%.6f", latLng.longitude).toDouble()
                            )
                            draftZone = draftZone + pt
                            return@addOnMapClickListener true
                        }
                        return@addOnMapClickListener false
                    }

                    if (latestPointMoveActive && latestEditorEnabled) {
                        val moved = GeoPoint(
                            lat = String.format(Locale.US, "%.6f", latLng.latitude).toDouble(),
                            lon = String.format(Locale.US, "%.6f", latLng.longitude).toDouble()
                        )
                        draftPoint = moved
                        cursorLat = moved.lat
                        cursorLon = moved.lon
                        return@addOnMapClickListener true
                    }

                    val pt = map.projection.toScreenLocation(latLng)
                    val hitBox = RectF(pt.x - 44f, pt.y - 54f, pt.x + 44f, pt.y + 30f)
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
        cameraTick++
        val initialCameraPoint = if (!isLandscape) {
            GeoUtils.offsetCoordinate(req.lat, req.lon, -85.0, 0.0)
        } else {
            GeoPoint(req.lat, req.lon)
        }
        mapInstance?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(initialCameraPoint.lat, initialCameraPoint.lon), 16.5)
        )
    }

    LaunchedEffect(zoneEditingObjectId, zoneEditingObject != null) {
        val target = zoneEditingObject
        if (target != null) {
            selectedObjectId = null
            zoneHudCollapsed = false
            isAutoCollapsedForDraw = false
            zoneStyleExpanded = false
            draftZone = target.zone
            draftZoneColor = target.zoneColor.orEmpty()
            draftZoneStyle = target.zoneStyle
            draftZonePattern = target.effectiveZonePattern()
            mapInstance?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lon), 16.0)
            )
        } else if (zoneEditingObjectId == null) {
            zoneHudCollapsed = false
            isAutoCollapsedForDraw = false
            zoneStyleExpanded = false
            draftZone = emptyList()
            draftZoneColor = ""
            draftZoneStyle = ZoneStyle.SOLID
            draftZonePattern = ZonePattern.NONE
        }
    }

    LaunchedEffect(focusRequest?.token, mapInstance) {
        val req = focusRequest ?: return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        suppressAutoCenter = true
        selectedObjectId = req.objectId
        cursorLat = req.lat
        cursorLon = req.lon
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
    val labelsVisibleAtZoom = GeoUtils.shouldShowObjectLabels(settings.showObjectLabels, currentZoom)

    LaunchedEffect(styleJson) {
        if (appliedStyleJson == styleJson) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                appliedStyleJson = styleJson
                gridCache.reset()
                updateContoursOverlay(style, showContours && !settings.lowMemoryMode)
                updateGridOverlay(style, cursorLat, cursorLon, currentZoom, settings.showMapGrid && !settings.lowMemoryMode, gridCache)
                updateSectorsOverlay(style, settings.offlineSectors, showSectors)
                updateObjectZonesOverlay(style, objects, selectedObjectId, zoneEditingObjectId ?: movingZoneHiddenObjectId, settings.showObjectZones)
                updateGpsAccuracyOverlay(style, currentFix.lat, currentFix.lon, currentFix.accuracyM)
                val activeDraftColor = normalizeZoneColor(draftZoneColor)
                    ?: zoneEditingObject?.status?.defaultZoneHex()
                    ?: "#5B9D72"
                updateDraftZoneOverlay(style, draftZone, activeDraftColor, draftZoneStyle, draftZonePattern)
                updatePointMoveOverlay(style, if (isMovingPoint) pointMoveRequest else null, draftPoint, shiftZoneWithPoint)
                updateObjectMarkersOverlay(context, style, objects, selectedObjectId, movingHiddenObjectId, labelsVisibleAtZoom)
            }
        }
    }

    LaunchedEffect(currentFix) {
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateGpsAccuracyOverlay(style, currentFix.lat, currentFix.lon, currentFix.accuracyM)
            }
        }
    }

    LaunchedEffect(objects, selectedObjectId, zoneEditingObjectId, movingHiddenObjectId, movingZoneHiddenObjectId, settings.showObjectZones, labelsVisibleAtZoom) {
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateObjectZonesOverlay(style, objects, selectedObjectId, zoneEditingObjectId ?: movingZoneHiddenObjectId, settings.showObjectZones)
                updateObjectMarkersOverlay(context, style, objects, selectedObjectId, movingHiddenObjectId, labelsVisibleAtZoom)
            }
        }
    }

    LaunchedEffect(isMovingPoint, pointMoveRequest, draftPoint, shiftZoneWithPoint) {
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updatePointMoveOverlay(style, if (isMovingPoint) pointMoveRequest else null, draftPoint, shiftZoneWithPoint)
            }
        }
    }

    LaunchedEffect(draftZone, draftZoneColor, draftZoneStyle, draftZonePattern, zoneEditingObject?.status) {
        val activeDraftColor = normalizeZoneColor(draftZoneColor)
            ?: zoneEditingObject?.status?.defaultZoneHex()
            ?: "#5B9D72"
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateDraftZoneOverlay(style, draftZone, activeDraftColor, draftZoneStyle, draftZonePattern)
            }
        }
    }

    LaunchedEffect(settings.offlineSectors, showSectors) {
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateSectorsOverlay(style, settings.offlineSectors, showSectors)
            }
        }
    }

    LaunchedEffect(showContours, settings.lowMemoryMode) {
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateContoursOverlay(style, showContours && !settings.lowMemoryMode)
            }
        }
    }

    LaunchedEffect(settings.showMapGrid, settings.lowMemoryMode, cursorLat, cursorLon, currentZoom) {
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateGridOverlay(style, cursorLat, cursorLon, currentZoom, settings.showMapGrid && !settings.lowMemoryMode, gridCache)
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    val metersPerPixel = remember(currentZoom, cursorLat) {
        val cosLat = cos(Math.toRadians(cursorLat)).coerceAtLeast(0.2)
        (156543.03392 * cosLat) / 2.0.pow(currentZoom.coerceIn(2.0, 20.0))
    }
    val scaleBarMeters = (metersPerPixel * 96.0).roundToInt().coerceAtLeast(5)

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
                                isAutoCollapsedForDraw = true
                                zoneStyleExpanded = false
                                val map = mapInstance ?: return@detectDragGestures
                                val latLng = map.projection.fromScreenLocation(PointF(offset.x, offset.y))
                                val pt = GeoPoint(
                                    lat = String.format(Locale.US, "%.6f", latLng.latitude).toDouble(),
                                    lon = String.format(Locale.US, "%.6f", latLng.longitude).toDouble()
                                )
                                draftZone = listOf(pt)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val map = mapInstance ?: return@detectDragGestures
                                val latLng = map.projection.fromScreenLocation(PointF(change.position.x, change.position.y))
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
                                isAutoCollapsedForDraw = false
                                draftZone = GeoUtils.simplifyPolygonPoints(
                                    draftZone,
                                    minSpacingMeters = 6.0,
                                    maxPoints = 48
                                )
                            },
                            onDragCancel = {
                                isAutoCollapsedForDraw = false
                            }
                        )
                    }
            )
        }

        // Draggable Target Pin Overlay (Point Move Mode)
        val activeDraftPin = draftPoint
        if (activeMoveReq != null && activeDraftPin != null) {
            val map = mapInstance
            val screenPt = remember(activeDraftPin, cameraTick, map) {
                map?.projection?.toScreenLocation(LatLng(activeDraftPin.lat, activeDraftPin.lon))
            }
            if (screenPt != null && screenPt.x.isFinite() && screenPt.y.isFinite()) {
                val activeIcon = activeMoveReq.icon ?: activeMoveReq.category.defaultIcon()
                val statusColor = activeMoveReq.status.color()
                val density = androidx.compose.ui.platform.LocalDensity.current
                val pinHalfPx = with(density) { 24.dp.toPx() }
                val labelGapPx = with(density) { 4.dp.toPx() }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (screenPt.x - pinHalfPx).roundToInt(),
                                y = (screenPt.y - pinHalfPx).roundToInt()
                            )
                        }
                        .size(48.dp)
                        .pointerInput(activeMoveReq.token, map) {
                            var dragX = 0f
                            var dragY = 0f
                            detectDragGestures(
                                onDragStart = {
                                    val m = mapInstance ?: return@detectDragGestures
                                    val currentScreen = m.projection.toScreenLocation(
                                        LatLng(draftPoint?.lat ?: activeMoveReq.lat, draftPoint?.lon ?: activeMoveReq.lon)
                                    )
                                    dragX = currentScreen.x
                                    dragY = currentScreen.y
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val m = mapInstance ?: return@detectDragGestures
                                    dragX += dragAmount.x
                                    dragY += dragAmount.y
                                    val geo = m.projection.fromScreenLocation(PointF(dragX, dragY))
                                    val next = GeoPoint(
                                        lat = String.format(Locale.US, "%.6f", geo.latitude).toDouble(),
                                        lon = String.format(Locale.US, "%.6f", geo.longitude).toDouble()
                                    )
                                    draftPoint = next
                                    cursorLat = next.lat
                                    cursorLon = next.lon
                                    cameraTick++
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .urbexGlow(UrbexColors.GreenBright, radius = 16.dp, cornerRadius = 24.dp, alpha = 0.50f)
                            .background(UrbexColors.Surface.copy(alpha = 0.96f), CircleShape)
                            .border(2.2.dp, statusColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = activeIcon.vector(),
                            contentDescription = activeMoveReq.title,
                            tint = statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = screenPt.x.roundToInt(),
                                y = (screenPt.y + pinHalfPx + labelGapPx).roundToInt()
                            )
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth))
                            layout(0, 0) {
                                placeable.place(-placeable.width / 2, 0)
                            }
                        }
                ) {
                    Text(
                        text = activeMoveReq.title.ifBlank { "Новая точка" },
                        color = UrbexColors.TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .urbexGlow(UrbexColors.GreenBright, radius = 8.dp, cornerRadius = 5.dp, alpha = 0.28f)
                            .background(UrbexColors.Background.copy(alpha = 0.94f), RoundedCornerShape(5.dp))
                            .border(1.dp, UrbexColors.Green, RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
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

        // Top-Left Compact Telemetry Pill (hidden while editing object zone or modal)
        AnimatedVisibility(
            visible = showNormalHud,
            enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { -it / 3 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 3 },
            modifier = Modifier
                .align(Alignment.TopStart)
                .widthIn(max = if (isLandscape) 520.dp else Dp.Unspecified)
                .padding(
                    start = if (isLandscape) 8.dp else 10.dp,
                    top = if (isLandscape) 6.dp else 10.dp,
                    end = if (isLandscape) 56.dp else 62.dp
                )
        ) {
            val accText = currentFix.accuracyM?.let { "±${it}м" } ?: signalLevel.label
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(UrbexColors.Surface.copy(alpha = 0.88f), RoundedCornerShape(10.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                        .padding(
                            horizontal = if (isLandscape) 8.dp else 10.dp,
                            vertical = if (isLandscape) 5.dp else 6.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = GeoUtils.formatDisplayCoordinates(cursorLat, cursorLon, settings.coordinateFormat, settings.maskCoordinatesOnScreen),
                            color = UrbexColors.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (settings.showCompassHud) {
                            Text("·", color = UrbexColors.TextDim, fontSize = 11.sp)
                            MapHeadingHudLabel(
                                cameraBearing = cameraBearing,
                                deviceAzimuthState = deviceAzimuthState
                            )
                        }
                        Text("·", color = UrbexColors.TextDim, fontSize = 11.sp)
                        Text(
                            text = "${scaleBarMeters}м",
                            color = UrbexColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        Text("·", color = UrbexColors.TextDim, fontSize = 11.sp)
                        MapGpsSignalBadge(
                            isActive = isActive,
                            targetSignalColor = targetSignalColor,
                            accText = accText
                        )
                    }

                    if (gpsStatusNote != null) {
                        Text(
                            text = gpsStatusNote.orEmpty(),
                            color = UrbexColors.Amber,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (settings.showCompassHud) {
                    MiniCompassWidget(
                        cameraBearing = cameraBearing,
                        deviceAzimuthState = deviceAzimuthState,
                        compact = isLandscape,
                        onResetNorth = {
                            mapInstance?.animateCamera(
                                CameraUpdateFactory.bearingTo(0.0),
                                260
                            )
                            cameraBearing = 0
                        }
                    )
                }

                AnimatedVisibility(
                    visible = nearbyHazard != null,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
                ) {
                    nearbyHazard?.let { (hazardObj, hazardDist) ->
                        Row(
                            modifier = Modifier
                                .urbexGlow(UrbexColors.Danger, radius = 12.dp, cornerRadius = 9.dp, alpha = 0.34f)
                                .background(UrbexColors.DangerSoft.copy(alpha = 0.94f), RoundedCornerShape(9.dp))
                                .border(1.dp, UrbexColors.Danger, RoundedCornerShape(9.dp))
                                .urbexBounceClick(0.96f) { selectedObjectId = hazardObj.id }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "⚠ Опасная зона рядом: ${hazardObj.title} (${GeoUtils.formatDistance(hazardDist)})",
                                color = Color(0xFFF1ACA7),
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

        // Top-Right Unified Zoom/GPS Capsule + Collapsible Map Tools Drawer
        AnimatedVisibility(
            visible = showNormalHud,
            enter = fadeIn(tween(160)) + fadeIn(tween(160)),
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
                        .urbexBounceClick(0.92f) { toolsOpen = !toolsOpen },
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
                AnimatedVisibility(
                    visible = toolsOpen,
                    enter = fadeIn(tween(150)) + expandVertically(tween(160)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(140))
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
                                    lat = cursorLat,
                                    lon = cursorLon,
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
                                val sector = OfflineMapSector(
                                    id = "sec-${now.toString(36)}",
                                    name = String.format(Locale.US, "Сектор %.3f, %.3f", cursorLat, cursorLon),
                                    centerLat = cursorLat,
                                    centerLon = cursorLon,
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

        // Bottom Floating Overlay: Interactive Zone Editor OR clean Selected Object / Toast overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomOverlayPadding)
        ) {
            val activeDraftPt = draftPoint
            if (activeZoneObj != null && !hideHud) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .then(if (isLandscape) Modifier.widthIn(max = 460.dp) else Modifier.fillMaxWidth()),
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

                    // Interactive Compact & Collapsible Zone Drawing HUD
                    val normalizedHudColor = normalizeZoneColor(draftZoneColor)
                    val previewHudHex = normalizedHudColor ?: activeZoneObj.status.defaultZoneHex()
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
                                    activeZoneObj.copy(
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

                    if (effectiveZoneCollapsed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .urbexCardGlow(UrbexColors.GreenBright, radius = 10.dp, cornerRadius = 12.dp, alpha = 0.16f)
                                .background(UrbexColors.Surface.copy(alpha = 0.94f), RoundedCornerShape(12.dp))
                                .border(1.dp, UrbexColors.Green, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(previewHudColor)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeZoneObj.title,
                                    color = UrbexColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = zoneMetricsInline,
                                    color = if (draftZone.size in 1..2) UrbexColors.Amber else UrbexColors.GreenBright,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
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
                            if (!isAutoCollapsedForDraw) {
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .heightIn(max = if (isLandscape) 240.dp else 390.dp)
                                .urbexCardGlow(UrbexColors.GreenBright, cornerRadius = 13.dp, alpha = 0.18f)
                                .background(UrbexColors.Surface.copy(alpha = 0.96f), RoundedCornerShape(13.dp))
                                .border(1.dp, UrbexColors.Green, RoundedCornerShape(13.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(if (isLandscape) 10.dp else 11.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = "Зона:",
                                            color = UrbexColors.GreenBright,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = activeZoneObj.title,
                                            color = UrbexColors.TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = zoneMetricsInline,
                                        color = if (draftZone.size in 1..2) UrbexColors.Amber else UrbexColors.TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        maxLines = 1
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(UrbexColors.Background, RoundedCornerShape(7.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(7.dp))
                                        .urbexBounceClick(0.95f) { zoneHudCollapsed = true }
                                        .padding(horizontal = 7.dp),
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
                                            activeZoneObj.lat,
                                            activeZoneObj.lon,
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
            } else if (activeMoveReq != null && !hideHud && activeDraftPt != null) {
                val moveDistM = GeoUtils.haversineDistanceMeters(
                    activeMoveReq.lat,
                    activeMoveReq.lon,
                    activeDraftPt.lat,
                    activeDraftPt.lon
                )
                val nudgePoint: (Double, Double) -> Unit = { northM, eastM ->
                    val next = GeoUtils.offsetCoordinate(activeDraftPt.lat, activeDraftPt.lon, northM, eastM)
                    draftPoint = next
                    cursorLat = next.lat
                    cursorLon = next.lon
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
                                    val latVal = centerTarget?.latitude ?: cursorLat
                                    val lonVal = centerTarget?.longitude ?: cursorLon
                                    val next = GeoPoint(
                                        lat = String.format(Locale.US, "%.6f", latVal).toDouble(),
                                        lon = String.format(Locale.US, "%.6f", lonVal).toDouble()
                                    )
                                    draftPoint = next
                                    cursorLat = next.lat
                                    cursorLon = next.lon
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
                                    cursorLat = resetPt.lat
                                    cursorLon = resetPt.lon
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
private fun MapHeadingHudLabel(
    cameraBearing: Int,
    deviceAzimuthState: State<Float?>
) {
    val hudDeg = if (cameraBearing != 0) cameraBearing else (deviceAzimuthState.value?.roundToInt() ?: 0)
    Text(
        text = "HDG $hudDeg°",
        color = UrbexColors.Amber,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        maxLines = 1
    )
}

@Composable
private fun MapGpsSignalBadge(
    isActive: Boolean,
    targetSignalColor: Color,
    accText: String
) {
    val signalColor by animateColorAsState(
        targetValue = targetSignalColor,
        animationSpec = tween(280),
        label = "gpsSignalColor"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier.size(10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isActive) {
                val gpsPulseTransition = rememberInfiniteTransition(label = "gpsDotPulse")
                val pulseScaleState = gpsPulseTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.9f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1100),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "gpsPulseScale"
                )
                val pulseAlphaState = gpsPulseTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1100),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "gpsPulseAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer {
                            val s = pulseScaleState.value
                            scaleX = s
                            scaleY = s
                            alpha = pulseAlphaState.value
                        }
                        .clip(CircleShape)
                        .background(signalColor)
                )
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(signalColor)
            )
        }
        Text(
            text = accText,
            color = signalColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
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

@Composable
private fun MiniCompassWidget(
    cameraBearing: Int,
    deviceAzimuthState: State<Float?>,
    compact: Boolean,
    onResetNorth: () -> Unit
) {
    val deviceAzimuthDeg = deviceAzimuthState.value
    val isRotated = cameraBearing != 0
    val displayDeg = if (isRotated) cameraBearing else (deviceAzimuthDeg?.roundToInt() ?: 0)
    val cardinal = remember(displayDeg) {
        val dirs = arrayOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
        dirs[((displayDeg + 22) / 45) % 8]
    }
    val dialSize = if (compact) 34.dp else 40.dp
    val edgePad = if (compact) 4.dp else 5.dp
    val endPad = if (compact) 5.dp else 6.dp
    val cornerRadius = if (compact) 15.dp else 17.dp
    val shape = RoundedCornerShape(cornerRadius)
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.35f)
    val textColWidth = ((if (compact) 49f else 54f) * fontScale).dp
    val accentColor = if (isRotated) UrbexColors.Amber else UrbexColors.GreenBright

    Row(
        modifier = Modifier
            .urbexGlow(
                color = UrbexColors.Amber,
                radius = 12.dp,
                cornerRadius = cornerRadius,
                alpha = 0.28f,
                enabled = isRotated
            )
            .clip(shape)
            .background(UrbexColors.Surface.copy(alpha = 0.90f), shape)
            .border(
                1.dp,
                if (isRotated) UrbexColors.Amber.copy(alpha = 0.7f) else UrbexColors.Border,
                shape
            )
            .urbexBounceClick(0.94f, onClick = onResetNorth)
            .padding(start = edgePad, end = endPad, top = edgePad, bottom = edgePad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(dialSize)
                .background(UrbexColors.Background.copy(alpha = 0.92f), CircleShape)
                .border(1.dp, UrbexColors.Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    color = Color(0xFF252E2A),
                    radius = r * 0.56f,
                    center = center,
                    style = Stroke(width = 1.2f)
                )

                // Outer ticks (12 divisions, stronger at cardinal directions)
                for (i in 0 until 12) {
                    val angleRad = Math.toRadians((i * 30.0) - 90.0 - cameraBearing.toDouble())
                    val isCardinal = i % 3 == 0
                    val innerR = r * if (isCardinal) 0.72f else 0.84f
                    val start = Offset(
                        center.x + (cos(angleRad) * innerR).toFloat(),
                        center.y + (sin(angleRad) * innerR).toFloat()
                    )
                    val end = Offset(
                        center.x + (cos(angleRad) * r * 0.96f).toFloat(),
                        center.y + (sin(angleRad) * r * 0.96f).toFloat()
                    )
                    drawLine(
                        color = if (i == 0) Color(0xFFD96459) else if (isCardinal) Color(0xFF98A49E) else Color(0xFF3A4741),
                        start = start,
                        end = end,
                        strokeWidth = if (i == 0) 2.2f else if (isCardinal) 1.5f else 1f,
                        cap = StrokeCap.Round
                    )
                }

                // Live device heading dot on rim if magnetometer/rotation sensor is active
                if (deviceAzimuthDeg != null) {
                    val devAngleRad = Math.toRadians((deviceAzimuthDeg - cameraBearing - 90f).toDouble())
                    val dotPos = Offset(
                        center.x + (cos(devAngleRad) * r * 0.85f).toFloat(),
                        center.y + (sin(devAngleRad) * r * 0.85f).toFloat()
                    )
                    drawCircle(
                        color = UrbexColors.GreenBright,
                        radius = 3.2f,
                        center = dotPos
                    )
                }

                // Rotating North needle (points to geographic North)
                rotate(degrees = -cameraBearing.toFloat(), pivot = center) {
                    val northPath = ComposePath().apply {
                        moveTo(center.x, center.y - r * 0.62f)
                        lineTo(center.x + r * 0.19f, center.y)
                        lineTo(center.x - r * 0.19f, center.y)
                        close()
                    }
                    drawPath(northPath, color = Color(0xFFD96459))

                    val southPath = ComposePath().apply {
                        moveTo(center.x, center.y + r * 0.56f)
                        lineTo(center.x + r * 0.15f, center.y)
                        lineTo(center.x - r * 0.15f, center.y)
                        close()
                    }
                    drawPath(southPath, color = Color(0xFF56635D))

                    drawCircle(
                        color = UrbexColors.TextPrimary,
                        radius = 2.2f,
                        center = center
                    )
                }
            }
        }

        Column(
            modifier = Modifier.width(textColWidth),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = cardinal,
                    color = accentColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "${displayDeg}°",
                    color = UrbexColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (compact) 10.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Text(
                text = if (isRotated) "На север ↺" else "С · Компас",
                color = UrbexColors.TextDim,
                fontSize = 9.sp,
                letterSpacing = (-0.1).sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun ReliabilityStatus.hexColor(): String = when (this) {
    ReliabilityStatus.VERIFIED -> "#6DB386"
    ReliabilityStatus.UNVERIFIED -> "#D39239"
    ReliabilityStatus.CONTESTED -> "#B9A46B"
    ReliabilityStatus.HAZARDOUS -> "#C7564E"
}

private fun ReliabilityStatus.androidColorInt(): Int = when (this) {
    ReliabilityStatus.VERIFIED -> android.graphics.Color.rgb(109, 179, 134)
    ReliabilityStatus.UNVERIFIED -> android.graphics.Color.rgb(211, 146, 57)
    ReliabilityStatus.CONTESTED -> android.graphics.Color.rgb(185, 164, 107)
    ReliabilityStatus.HAZARDOUS -> android.graphics.Color.rgb(199, 86, 78)
}

private class GridOverlayCache {
    var hasSource: Boolean = false
    var visible: Boolean = false
    var snapLatBits: Long = Long.MIN_VALUE
    var snapLonBits: Long = Long.MIN_VALUE
    var stepBits: Long = Long.MIN_VALUE

    fun reset() {
        hasSource = false
        visible = false
        snapLatBits = Long.MIN_VALUE
        snapLonBits = Long.MIN_VALUE
        stepBits = Long.MIN_VALUE
    }
}

private object MapMarkerPaintCache {
    private val boldTypeface: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(230, 235, 232)
        typeface = boldTypeface
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
}

private fun createObjectBadgeBitmap(
    context: Context,
    obj: UrbexObject,
    isSelected: Boolean
): Bitmap {
    val density = context.resources.displayMetrics.density
    val shortTitle = if (obj.title.length > 18) obj.title.take(17) + "…" else obj.title
    val effIcon = obj.effectiveIcon()

    val textPaint = MapMarkerPaintCache.textPaint.apply {
        textSize = 11.5f * density
    }
    val textWidth = textPaint.measureText(shortTitle)
    val padH = 8f * density
    val iconSize = 14f * density
    val iconGap = 5f * density
    val w = (padH * 2f + iconSize + iconGap + textWidth).roundToInt().coerceAtLeast((44f * density).roundToInt())
    val h = (26f * density).roundToInt()

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val rect = RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
    val radius = 6f * density

    val fillPaint = MapMarkerPaintCache.fillPaint.apply {
        color = if (isSelected) {
            android.graphics.Color.argb(245, 26, 32, 30)
        } else {
            android.graphics.Color.argb(232, 19, 24, 22)
        }
    }
    canvas.drawRoundRect(rect, radius, radius, fillPaint)

    val strokePaint = MapMarkerPaintCache.strokePaint.apply {
        strokeWidth = (if (isSelected) 2.0f else 1.2f) * density
        color = if (isSelected) {
            android.graphics.Color.rgb(91, 157, 114)
        } else {
            android.graphics.Color.rgb(37, 46, 42)
        }
    }
    canvas.drawRoundRect(rect, radius, radius, strokePaint)

    drawObjectIconOnCanvas(
        canvas = canvas,
        icon = effIcon,
        cx = padH + iconSize * 0.5f,
        cy = h * 0.5f,
        sizePx = iconSize,
        colorInt = obj.status.androidColorInt()
    )

    val baseline = (h / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(shortTitle, padH + iconSize + iconGap, baseline, textPaint)
    return bitmap
}

private fun createObjectDotBitmap(
    context: Context,
    obj: UrbexObject,
    isSelected: Boolean
): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = ((if (isSelected) 30f else 26f) * density).roundToInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx * 0.5f
    val cy = sizePx * 0.5f
    val r = sizePx * 0.44f

    val bgPaint = MapMarkerPaintCache.fillPaint.apply {
        color = if (isSelected) {
            android.graphics.Color.argb(248, 22, 32, 27)
        } else {
            android.graphics.Color.argb(238, 15, 20, 18)
        }
    }
    canvas.drawCircle(cx, cy, r, bgPaint)

    val ringPaint = MapMarkerPaintCache.strokePaint.apply {
        strokeWidth = (if (isSelected) 2.4f else 1.8f) * density
        color = obj.status.androidColorInt()
    }
    canvas.drawCircle(cx, cy, r, ringPaint)

    drawObjectIconOnCanvas(
        canvas = canvas,
        icon = obj.effectiveIcon(),
        cx = cx,
        cy = cy,
        sizePx = sizePx * 0.50f,
        colorInt = obj.status.androidColorInt()
    )
    return bitmap
}

private fun ensureEmptyZonePatternImage(mapStyle: Style): String {
    if (mapStyle.getImage(ZONE_EMPTY_PATTERN_ID) == null) {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        mapStyle.addImage(ZONE_EMPTY_PATTERN_ID, bitmap)
    }
    return ZONE_EMPTY_PATTERN_ID
}

private fun ensureAllZonePatternImages(mapStyle: Style, hex: String) {
    ZonePattern.entries.forEach { pattern ->
        if (pattern != ZonePattern.NONE) {
            ensureZonePatternImage(mapStyle, hex, pattern)
        }
    }
}

private fun ensureZonePatternImage(
    mapStyle: Style,
    hex: String,
    pattern: ZonePattern
): String {
    if (pattern == ZonePattern.NONE) return ""
    val normalized = normalizeZoneColor(hex) ?: "#5B9D72"
    val imageId = "urbex-pat-${pattern.name}-${normalized.removePrefix("#")}"
    if (mapStyle.getImage(imageId) == null) {
        val size = when (pattern) {
            ZonePattern.HAZARD_STRIPES -> 32
            ZonePattern.DOTS, ZonePattern.GRID -> 24
            else -> 20
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseColor = runCatching { android.graphics.Color.parseColor(normalized) }
            .getOrDefault(android.graphics.Color.rgb(91, 157, 114))
        val drawColor = android.graphics.Color.argb(
            205,
            android.graphics.Color.red(baseColor),
            android.graphics.Color.green(baseColor),
            android.graphics.Color.blue(baseColor)
        )
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = drawColor
            style = Paint.Style.STROKE
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = drawColor
            style = Paint.Style.FILL
        }

        when (pattern) {
            ZonePattern.NONE -> Unit
            ZonePattern.HATCH -> {
                strokePaint.strokeWidth = 3.0f
                for (offset in -size..(size * 2) step 10) {
                    canvas.drawLine(offset.toFloat(), size.toFloat(), (offset + size).toFloat(), 0f, strokePaint)
                }
            }
            ZonePattern.CROSS_HATCH -> {
                strokePaint.strokeWidth = 2.4f
                for (offset in -size..(size * 2) step 10) {
                    canvas.drawLine(offset.toFloat(), size.toFloat(), (offset + size).toFloat(), 0f, strokePaint)
                    canvas.drawLine(offset.toFloat(), 0f, (offset + size).toFloat(), size.toFloat(), strokePaint)
                }
            }
            ZonePattern.GRID -> {
                strokePaint.strokeWidth = 2.0f
                canvas.drawLine(1f, 0f, 1f, size.toFloat(), strokePaint)
                canvas.drawLine(0f, 1f, size.toFloat(), 1f, strokePaint)
                canvas.drawLine(size * 0.5f, 0f, size * 0.5f, size.toFloat(), strokePaint)
                canvas.drawLine(0f, size * 0.5f, size.toFloat(), size * 0.5f, strokePaint)
            }
            ZonePattern.DOTS -> {
                canvas.drawCircle(6f, 6f, 2.8f, fillPaint)
                canvas.drawCircle(18f, 18f, 2.8f, fillPaint)
            }
            ZonePattern.VERTICAL -> {
                strokePaint.strokeWidth = 3.0f
                canvas.drawLine(5f, 0f, 5f, size.toFloat(), strokePaint)
                canvas.drawLine(15f, 0f, 15f, size.toFloat(), strokePaint)
            }
            ZonePattern.HORIZONTAL -> {
                strokePaint.strokeWidth = 3.0f
                canvas.drawLine(0f, 5f, size.toFloat(), 5f, strokePaint)
                canvas.drawLine(0f, 15f, size.toFloat(), 15f, strokePaint)
            }
            ZonePattern.HAZARD_STRIPES -> {
                strokePaint.strokeWidth = 8.5f
                for (offset in -size..(size * 2) step 16) {
                    canvas.drawLine(offset.toFloat(), size.toFloat(), (offset + size).toFloat(), 0f, strokePaint)
                }
            }
        }
        mapStyle.addImage(imageId, bitmap)
    }
    return imageId
}

private fun updateObjectZonesOverlay(
    style: Style,
    objects: List<UrbexObject>,
    selectedObjectId: String?,
    zoneEditingObjectId: String?,
    visible: Boolean
) {
    if (!style.isFullyLoaded) return
    val emptyPatternId = ensureEmptyZonePatternImage(style)
    val visValue = if (visible) Property.VISIBLE else Property.NONE
    val features = objects.mapNotNull { obj ->
        if (obj.id == zoneEditingObjectId || obj.zone.size < 3) return@mapNotNull null
        val zoneHex = obj.effectiveZoneColor()
        val effPattern = obj.effectiveZonePattern()
        val patternId = ensureZonePatternImage(style, zoneHex, effPattern).ifEmpty { emptyPatternId }
        val ring = (obj.zone + obj.zone.first()).map { Point.fromLngLat(it.lon, it.lat) }
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
            addStringProperty("objectId", obj.id)
            addStringProperty("zoneHex", zoneHex)
            addStringProperty("zoneStyle", obj.zoneStyle.name)
            addStringProperty("hasPattern", if (effPattern != ZonePattern.NONE) "1" else "0")
            addStringProperty("patternId", patternId)
        }
    }
    val collection = FeatureCollection.fromFeatures(features)
    val source = style.getSourceAs<GeoJsonSource>(OBJECT_ZONES_SOURCE_ID)
    if (source == null) {
        style.addSource(GeoJsonSource(OBJECT_ZONES_SOURCE_ID, collection))
        style.addLayer(
            FillLayer(OBJECT_ZONES_FILL_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    fillColor(Expression.get("zoneHex")),
                    fillOpacity(0.18f),
                    visibility(visValue)
                )
                .withFilter(
                    Expression.all(
                        Expression.neq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.OUTLINE.name)),
                        Expression.neq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.DASHED_OUTLINE.name))
                    )
                )
        )
        style.addLayer(
            FillLayer(OBJECT_ZONES_HATCH_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    fillPattern(Expression.get("patternId")),
                    fillOpacity(0.58f),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("hasPattern"), Expression.literal("1")))
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_SOLID_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor(Expression.get("zoneHex")),
                    lineWidth(2.1f),
                    visibility(visValue)
                )
                .withFilter(
                    Expression.any(
                        Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.SOLID.name)),
                        Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.OUTLINE.name))
                    )
                )
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_BOLD_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor(Expression.get("zoneHex")),
                    lineWidth(3.8f),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.BOLD.name)))
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_GLOW_HALO_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor(Expression.get("zoneHex")),
                    lineWidth(7.2f),
                    lineOpacity(0.32f),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.GLOW.name)))
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_GLOW_CORE_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor(Expression.get("zoneHex")),
                    lineWidth(2.4f),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.GLOW.name)))
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_DOUBLE_OUTER_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor(Expression.get("zoneHex")),
                    lineWidth(4.6f),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.DOUBLE_LINE.name)))
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_DOUBLE_INNER_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor("#0C0F0E"),
                    lineWidth(1.8f),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.DOUBLE_LINE.name)))
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_DASHED_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor(Expression.get("zoneHex")),
                    lineWidth(2.1f),
                    lineDasharray(arrayOf(4f, 3f)),
                    visibility(visValue)
                )
                .withFilter(
                    Expression.any(
                        Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.DASHED.name)),
                        Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.DASHED_OUTLINE.name))
                    )
                )
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_DOTTED_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor(Expression.get("zoneHex")),
                    lineWidth(2.3f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineDasharray(arrayOf(1.2f, 2.4f)),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.DOTTED.name)))
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_DASH_DOT_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor(Expression.get("zoneHex")),
                    lineWidth(2.2f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineDasharray(arrayOf(5.5f, 2.2f, 1.2f, 2.2f)),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.DASH_DOT.name)))
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_LONG_DASH_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor(Expression.get("zoneHex")),
                    lineWidth(2.2f),
                    lineDasharray(arrayOf(8.5f, 3.2f)),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.LONG_DASH.name)))
        )
    } else {
        source.setGeoJson(collection)
        listOf(
            OBJECT_ZONES_FILL_LAYER_ID,
            OBJECT_ZONES_HATCH_LAYER_ID
        ).forEach { id -> style.getLayerAs<FillLayer>(id)?.setProperties(visibility(visValue)) }
        listOf(
            OBJECT_ZONES_LINE_SOLID_LAYER_ID,
            OBJECT_ZONES_LINE_BOLD_LAYER_ID,
            OBJECT_ZONES_LINE_GLOW_HALO_LAYER_ID,
            OBJECT_ZONES_LINE_GLOW_CORE_LAYER_ID,
            OBJECT_ZONES_LINE_DOUBLE_OUTER_LAYER_ID,
            OBJECT_ZONES_LINE_DOUBLE_INNER_LAYER_ID,
            OBJECT_ZONES_LINE_DASHED_LAYER_ID,
            OBJECT_ZONES_LINE_DOTTED_LAYER_ID,
            OBJECT_ZONES_LINE_DASH_DOT_LAYER_ID,
            OBJECT_ZONES_LINE_LONG_DASH_LAYER_ID
        ).forEach { id -> style.getLayerAs<LineLayer>(id)?.setProperties(visibility(visValue)) }
    }
}

private fun updateDraftZoneOverlay(
    style: Style,
    draftZone: List<GeoPoint>,
    zoneColorHex: String = "#5B9D72",
    zoneStyle: ZoneStyle = ZoneStyle.SOLID,
    zonePattern: ZonePattern = ZonePattern.NONE
) {
    if (!style.isFullyLoaded) return
    val activeHex = normalizeZoneColor(zoneColorHex) ?: "#5B9D72"
    val emptyPatternId = ensureEmptyZonePatternImage(style)
    if (draftZone.size >= 3) {
        ensureAllZonePatternImages(style, activeHex)
    }
    val patternImageId = ensureZonePatternImage(style, activeHex, zonePattern)
    val hasPattern = zonePattern != ZonePattern.NONE && patternImageId.isNotEmpty()
    val safePatternId = patternImageId.ifEmpty { emptyPatternId }
    val features = mutableListOf<Feature>()
    if (draftZone.size >= 3) {
        val ring = (draftZone + draftZone.first()).map { Point.fromLngLat(it.lon, it.lat) }
        features.add(
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
                addStringProperty("kind", "poly")
                addStringProperty("hasPattern", if (hasPattern) "1" else "0")
                addStringProperty("patternId", safePatternId)
            }
        )
        features.add(
            Feature.fromGeometry(LineString.fromLngLats(ring)).apply {
                addStringProperty("kind", "line")
            }
        )
    } else if (draftZone.size == 2) {
        val pts = draftZone.map { Point.fromLngLat(it.lon, it.lat) }
        features.add(
            Feature.fromGeometry(LineString.fromLngLats(pts)).apply {
                addStringProperty("kind", "line")
            }
        )
    }
    draftZone.forEachIndexed { idx, pt ->
        features.add(
            Feature.fromGeometry(Point.fromLngLat(pt.lon, pt.lat)).apply {
                addStringProperty("kind", "vertex")
                addStringProperty("vertexHex", if (idx == 0) "#DF9E43" else activeHex)
            }
        )
    }

    val fillVis = if (!zoneStyle.isOutlineOnly()) Property.VISIBLE else Property.NONE
    val solidVis = if (zoneStyle == ZoneStyle.SOLID || zoneStyle == ZoneStyle.OUTLINE) Property.VISIBLE else Property.NONE
    val boldVis = if (zoneStyle == ZoneStyle.BOLD) Property.VISIBLE else Property.NONE
    val glowVis = if (zoneStyle == ZoneStyle.GLOW) Property.VISIBLE else Property.NONE
    val doubleVis = if (zoneStyle == ZoneStyle.DOUBLE_LINE) Property.VISIBLE else Property.NONE
    val dashedVis = if (zoneStyle == ZoneStyle.DASHED || zoneStyle == ZoneStyle.DASHED_OUTLINE) Property.VISIBLE else Property.NONE
    val dottedVis = if (zoneStyle == ZoneStyle.DOTTED) Property.VISIBLE else Property.NONE
    val dashDotVis = if (zoneStyle == ZoneStyle.DASH_DOT) Property.VISIBLE else Property.NONE
    val longDashVis = if (zoneStyle == ZoneStyle.LONG_DASH) Property.VISIBLE else Property.NONE

    val collection = FeatureCollection.fromFeatures(features)
    val source = style.getSourceAs<GeoJsonSource>(ZONE_DRAFT_SOURCE_ID)
    if (source == null) {
        style.addSource(GeoJsonSource(ZONE_DRAFT_SOURCE_ID, collection))
        style.addLayer(
            FillLayer(ZONE_DRAFT_FILL_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                fillColor(activeHex),
                fillOpacity(0.24f),
                visibility(fillVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("poly")))
        )
        style.addLayer(
            FillLayer(ZONE_DRAFT_HATCH_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                fillPattern(Expression.get("patternId")),
                fillOpacity(0.6f),
                visibility(Property.VISIBLE)
            ).withFilter(
                Expression.all(
                    Expression.eq(Expression.get("kind"), Expression.literal("poly")),
                    Expression.eq(Expression.get("hasPattern"), Expression.literal("1"))
                )
            )
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_SOLID_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(activeHex),
                lineWidth(2.6f),
                visibility(solidVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("line")))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_BOLD_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(activeHex),
                lineWidth(4.4f),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(boldVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("line")))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_GLOW_HALO_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(activeHex),
                lineWidth(8.0f),
                lineOpacity(0.32f),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(glowVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("line")))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_GLOW_CORE_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(activeHex),
                lineWidth(2.8f),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(glowVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("line")))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_DOUBLE_OUTER_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(activeHex),
                lineWidth(5.2f),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(doubleVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("line")))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_DOUBLE_INNER_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor("#0C0F0E"),
                lineWidth(2.0f),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(doubleVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("line")))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_DASHED_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(activeHex),
                lineWidth(2.6f),
                lineDasharray(arrayOf(4f, 3f)),
                visibility(dashedVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("line")))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_DOTTED_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(activeHex),
                lineWidth(2.6f),
                lineCap(Property.LINE_CAP_ROUND),
                lineDasharray(arrayOf(1.2f, 2.4f)),
                visibility(dottedVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("line")))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_DASH_DOT_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(activeHex),
                lineWidth(2.6f),
                lineCap(Property.LINE_CAP_ROUND),
                lineDasharray(arrayOf(5.5f, 2.2f, 1.2f, 2.2f)),
                visibility(dashDotVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("line")))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_LONG_DASH_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(activeHex),
                lineWidth(2.6f),
                lineDasharray(arrayOf(8.5f, 3.2f)),
                visibility(longDashVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("line")))
        )
        style.addLayer(
            CircleLayer(ZONE_DRAFT_VERTEX_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                circleRadius(5.5f),
                circleColor(Expression.get("vertexHex")),
                circleStrokeColor("#0C0F0E"),
                circleStrokeWidth(1.8f)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("vertex")))
        )
    } else {
        source.setGeoJson(collection)
        style.getLayerAs<FillLayer>(ZONE_DRAFT_FILL_LAYER_ID)?.setProperties(fillColor(activeHex), visibility(fillVis))
        style.getLayerAs<FillLayer>(ZONE_DRAFT_HATCH_LAYER_ID)?.setProperties(visibility(Property.VISIBLE))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_SOLID_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(solidVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_BOLD_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(boldVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_GLOW_HALO_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(glowVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_GLOW_CORE_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(glowVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_DOUBLE_OUTER_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(doubleVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_DOUBLE_INNER_LAYER_ID)?.setProperties(visibility(doubleVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_DASHED_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(dashedVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_DOTTED_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(dottedVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_DASH_DOT_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(dashDotVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_LONG_DASH_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(longDashVis))
    }
}

private fun updatePointMoveOverlay(
    style: Style,
    request: MapPointMoveRequest?,
    draftPoint: GeoPoint?,
    shiftZoneWithPoint: Boolean
) {
    if (!style.isFullyLoaded) return
    val features = mutableListOf<Feature>()
    if (request != null && draftPoint != null) {
        features.add(
            Feature.fromGeometry(Point.fromLngLat(request.lon, request.lat)).apply {
                addStringProperty("kind", "origin")
            }
        )
        features.add(
            Feature.fromGeometry(Point.fromLngLat(draftPoint.lon, draftPoint.lat)).apply {
                addStringProperty("kind", "target")
                addStringProperty("statusHex", request.status.hexColor())
            }
        )
        features.add(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        Point.fromLngLat(request.lon, request.lat),
                        Point.fromLngLat(draftPoint.lon, draftPoint.lat)
                    )
                )
            ).apply {
                addStringProperty("kind", "tether")
            }
        )
        if (request.zone.size >= 3) {
            val dLat = if (shiftZoneWithPoint) draftPoint.lat - request.lat else 0.0
            val dLon = if (shiftZoneWithPoint) draftPoint.lon - request.lon else 0.0
            val previewZone = if (shiftZoneWithPoint) {
                GeoUtils.translateZone(request.zone, dLat, dLon)
            } else {
                request.zone
            }
            val ring = (previewZone + previewZone.first()).map { Point.fromLngLat(it.lon, it.lat) }
            features.add(
                Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
                    addStringProperty("kind", "zone")
                }
            )
            features.add(
                Feature.fromGeometry(LineString.fromLngLats(ring)).apply {
                    addStringProperty("kind", "zoneLine")
                }
            )
        }
    }
    val collection = FeatureCollection.fromFeatures(features)
    val source = style.getSourceAs<GeoJsonSource>(POINT_MOVE_SOURCE_ID)
    if (source == null) {
        style.addSource(GeoJsonSource(POINT_MOVE_SOURCE_ID, collection))
        style.addLayer(
            FillLayer(POINT_MOVE_ZONE_FILL_LAYER_ID, POINT_MOVE_SOURCE_ID).withProperties(
                fillColor("#6DB386"),
                fillOpacity(0.22f)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("zone")))
        )
        style.addLayer(
            LineLayer(POINT_MOVE_ZONE_LINE_LAYER_ID, POINT_MOVE_SOURCE_ID).withProperties(
                lineColor("#6DB386"),
                lineWidth(2.2f),
                lineDasharray(arrayOf(3f, 2f))
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("zoneLine")))
        )
        style.addLayer(
            LineLayer(POINT_MOVE_TETHER_LAYER_ID, POINT_MOVE_SOURCE_ID).withProperties(
                lineColor("#DF9E43"),
                lineWidth(2.0f),
                lineDasharray(arrayOf(3f, 2.5f))
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("tether")))
        )
        style.addLayer(
            CircleLayer(POINT_MOVE_ORIGIN_LAYER_ID, POINT_MOVE_SOURCE_ID).withProperties(
                circleRadius(6.0f),
                circleColor("#131816"),
                circleStrokeColor("#DF9E43"),
                circleStrokeWidth(2.0f)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("origin")))
        )
        style.addLayer(
            CircleLayer(POINT_MOVE_TARGET_HALO_LAYER_ID, POINT_MOVE_SOURCE_ID).withProperties(
                circleRadius(18.0f),
                circleColor("#5B9D72"),
                circleOpacity(0.26f),
                circleStrokeColor("#6DB386"),
                circleStrokeWidth(1.5f)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("target")))
        )
        style.addLayer(
            CircleLayer(POINT_MOVE_TARGET_LAYER_ID, POINT_MOVE_SOURCE_ID).withProperties(
                circleRadius(9.5f),
                circleColor("#131816"),
                circleStrokeColor(Expression.get("statusHex")),
                circleStrokeWidth(2.5f)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("target")))
        )
        style.addLayer(
            CircleLayer(POINT_MOVE_TARGET_CENTER_LAYER_ID, POINT_MOVE_SOURCE_ID).withProperties(
                circleRadius(3.2f),
                circleColor("#6DB386")
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("target")))
        )
    } else {
        source.setGeoJson(collection)
    }
}

private fun updateObjectMarkersOverlay(
    context: Context,
    style: Style,
    objects: List<UrbexObject>,
    selectedObjectId: String?,
    hiddenObjectId: String?,
    showLabels: Boolean
) {
    if (!style.isFullyLoaded) return
    val badgeVis = if (showLabels) Property.VISIBLE else Property.NONE
    val dotVis = if (!showLabels) Property.VISIBLE else Property.NONE
    val visibleObjects = if (hiddenObjectId != null) {
        objects.filter { it.id != hiddenObjectId }
    } else {
        objects
    }
    val features = visibleObjects.map { obj ->
        val isSelected = obj.id == selectedObjectId
        val effIcon = obj.effectiveIcon()
        val iconId = "urbex-pin-${obj.id}-${effIcon.name}-${obj.status.name}-${if (isSelected) 1 else 0}-${obj.title.hashCode()}"
        val dotIconId = "urbex-dot-${obj.id}-${effIcon.name}-${obj.status.name}-${if (isSelected) 1 else 0}"
        if (style.getImage(iconId) == null) {
            style.addImage(iconId, createObjectBadgeBitmap(context, obj, isSelected))
        }
        if (style.getImage(dotIconId) == null) {
            style.addImage(dotIconId, createObjectDotBitmap(context, obj, isSelected))
        }

        Feature.fromGeometry(Point.fromLngLat(obj.lon, obj.lat)).apply {
            addStringProperty("objectId", obj.id)
            addStringProperty("iconId", iconId)
            addStringProperty("dotIconId", dotIconId)
            addStringProperty("statusHex", obj.status.hexColor())
        }
    }
    val collection = FeatureCollection.fromFeatures(features)
    val source = style.getSourceAs<GeoJsonSource>(OBJECTS_SOURCE_ID)
    if (source == null) {
        style.addSource(GeoJsonSource(OBJECTS_SOURCE_ID, collection))
        style.addLayer(
            CircleLayer("urbex-objects-anchor-dots", OBJECTS_SOURCE_ID).withProperties(
                circleRadius(4.2f),
                circleColor(Expression.get("statusHex")),
                circleStrokeColor("#E6EBE8"),
                circleStrokeWidth(1.2f),
                visibility(badgeVis)
            )
        )
        style.addLayer(
            SymbolLayer(OBJECTS_DOT_LAYER_ID, OBJECTS_SOURCE_ID).withProperties(
                iconImage(Expression.get("dotIconId")),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconAnchor(Property.ICON_ANCHOR_CENTER),
                visibility(dotVis)
            )
        )
        style.addLayer(
            SymbolLayer(OBJECTS_BADGE_LAYER_ID, OBJECTS_SOURCE_ID).withProperties(
                iconImage(Expression.get("iconId")),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                iconOffset(arrayOf(0f, -8f)),
                visibility(badgeVis)
            )
        )
    } else {
        source.setGeoJson(collection)
        style.getLayerAs<CircleLayer>("urbex-objects-anchor-dots")?.setProperties(visibility(badgeVis))
        style.getLayerAs<SymbolLayer>(OBJECTS_DOT_LAYER_ID)?.setProperties(visibility(dotVis))
        style.getLayerAs<SymbolLayer>(OBJECTS_BADGE_LAYER_ID)?.setProperties(visibility(badgeVis))
    }
}

private fun updateSectorsOverlay(
    style: Style,
    sectors: List<OfflineMapSector>,
    visible: Boolean
) {
    if (!style.isFullyLoaded) return
    val features = sectors.map { sec ->
        val ring = GeoUtils.buildCirclePolygonPoints(
            lat = sec.centerLat,
            lon = sec.centerLon,
            radiusMeters = sec.radiusKm * 1000.0,
            steps = 64,
            maxRadiusMeters = 50_000.0
        ).map { (pLon, pLat) -> Point.fromLngLat(pLon, pLat) }
        Feature.fromGeometry(LineString.fromLngLats(ring))
    }
    val collection = FeatureCollection.fromFeatures(features)
    val visValue = if (visible) Property.VISIBLE else Property.NONE
    val source = style.getSourceAs<GeoJsonSource>(SECTORS_SOURCE_ID)
    if (source == null) {
        style.addSource(GeoJsonSource(SECTORS_SOURCE_ID, collection))
        style.addLayer(
            LineLayer(SECTORS_LINE_LAYER_ID, SECTORS_SOURCE_ID).withProperties(
                lineColor("#5B9D72"),
                lineWidth(1.6f),
                lineDasharray(arrayOf(4f, 4f)),
                visibility(visValue)
            )
        )
    } else {
        source.setGeoJson(collection)
        style.getLayerAs<LineLayer>(SECTORS_LINE_LAYER_ID)?.setProperties(visibility(visValue))
    }
}

private fun updateContoursOverlay(
    style: Style,
    visible: Boolean
) {
    if (!style.isFullyLoaded) return
    val visValue = if (visible) Property.VISIBLE else Property.NONE
    val existing = style.getLayerAs<LineLayer>(CONTOURS_LINE_LAYER_ID)
    if (existing != null) {
        existing.setProperties(visibility(visValue))
        return
    }
    val contourFeatures = (-4..4).map { idx ->
        val baseLat = 55.752 + idx * 0.0045
        val pts = (-12..12).map { step ->
            val lon = 37.618 + step * 0.003
            val wave = sin(step * 0.45 + idx * 0.9) * 0.0014
            Point.fromLngLat(lon, baseLat + wave)
        }
        Feature.fromGeometry(LineString.fromLngLats(pts))
    }
    val railPoints = listOf(
        Point.fromLngLat(37.604, 55.764),
        Point.fromLngLat(37.617, 55.756),
        Point.fromLngLat(37.6248, 55.7554),
        Point.fromLngLat(37.634, 55.747)
    )
    val all = contourFeatures + Feature.fromGeometry(LineString.fromLngLats(railPoints))
    style.addSource(GeoJsonSource(CONTOURS_SOURCE_ID, FeatureCollection.fromFeatures(all)))
    style.addLayer(
        LineLayer(CONTOURS_LINE_LAYER_ID, CONTOURS_SOURCE_ID).withProperties(
            lineColor("#6DB386"),
            lineWidth(1.0f),
            lineDasharray(arrayOf(3f, 3f)),
            visibility(visValue)
        )
    )
}

private fun updateGridOverlay(
    style: Style,
    centerLat: Double,
    centerLon: Double,
    zoom: Double,
    visible: Boolean,
    cache: GridOverlayCache? = null
) {
    if (!style.isFullyLoaded) return
    val visValue = if (visible) Property.VISIBLE else Property.NONE
    val source = style.getSourceAs<GeoJsonSource>(GRID_SOURCE_ID)
    if (!visible && source != null) {
        if (cache == null || cache.visible) {
            style.getLayerAs<LineLayer>(GRID_LINE_LAYER_ID)?.setProperties(visibility(visValue))
            cache?.visible = false
        }
        return
    }

    val step = when {
        zoom >= 15.0 -> 0.005
        zoom >= 12.0 -> 0.02
        zoom >= 9.0 -> 0.1
        else -> 0.5
    }
    val snapLat = kotlin.math.floor(centerLat / step) * step
    val snapLon = kotlin.math.floor(centerLon / step) * step
    val snapLatBits = snapLat.toBits()
    val snapLonBits = snapLon.toBits()
    val stepBits = step.toBits()

    if (
        source != null &&
        cache != null &&
        cache.hasSource &&
        cache.snapLatBits == snapLatBits &&
        cache.snapLonBits == snapLonBits &&
        cache.stepBits == stepBits
    ) {
        if (cache.visible != visible) {
            style.getLayerAs<LineLayer>(GRID_LINE_LAYER_ID)?.setProperties(visibility(visValue))
            cache.visible = visible
        }
        return
    }

    val minLat = snapLat - step * 6
    val maxLat = snapLat + step * 6
    val minLon = snapLon - step * 6
    val maxLon = snapLon + step * 6

    val features = ArrayList<Feature>(26)
    for (i in -6..6) {
        val lat = (snapLat + i * step).coerceIn(-85.0, 85.0)
        features.add(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        Point.fromLngLat(minLon, lat),
                        Point.fromLngLat(maxLon, lat)
                    )
                )
            )
        )
        val lon = snapLon + i * step
        features.add(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        Point.fromLngLat(lon, minLat.coerceIn(-85.0, 85.0)),
                        Point.fromLngLat(lon, maxLat.coerceIn(-85.0, 85.0))
                    )
                )
            )
        )
    }
    val collection = FeatureCollection.fromFeatures(features)
    if (source == null) {
        style.addSource(GeoJsonSource(GRID_SOURCE_ID, collection))
        style.addLayer(
            LineLayer(GRID_LINE_LAYER_ID, GRID_SOURCE_ID).withProperties(
                lineColor("#355243"),
                lineWidth(0.8f),
                lineDasharray(arrayOf(2f, 4f)),
                visibility(visValue)
            )
        )
    } else {
        source.setGeoJson(collection)
        if (cache == null || cache.visible != visible) {
            style.getLayerAs<LineLayer>(GRID_LINE_LAYER_ID)?.setProperties(visibility(visValue))
        }
    }
    cache?.apply {
        hasSource = true
        this.visible = visible
        this.snapLatBits = snapLatBits
        this.snapLonBits = snapLonBits
        this.stepBits = stepBits
    }
}

private fun updateGpsAccuracyOverlay(
    style: Style,
    lat: Double,
    lon: Double,
    accuracyM: Int?
) {
    if (!style.isFullyLoaded) return
    val signalLevel = GeoUtils.gpsSignalLevel(accuracyM)
    val hex = signalLevel.hexColor
    val ring = GeoUtils.buildCirclePolygonPoints(
        lat = lat,
        lon = lon,
        radiusMeters = (accuracyM ?: 60).toDouble()
    ).map { (pLon, pLat) -> Point.fromLngLat(pLon, pLat) }
    val accuracyFeature = Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
    val centerFeature = Feature.fromGeometry(Point.fromLngLat(lon, lat))

    val accSource = style.getSourceAs<GeoJsonSource>(GPS_ACCURACY_SOURCE_ID)
    if (accSource == null) {
        style.addSource(GeoJsonSource(GPS_ACCURACY_SOURCE_ID, accuracyFeature))
        style.addLayer(
            FillLayer(GPS_ACCURACY_FILL_LAYER_ID, GPS_ACCURACY_SOURCE_ID).withProperties(
                fillColor(hex),
                fillOpacity(0.18f)
            )
        )
        style.addLayer(
            LineLayer(GPS_ACCURACY_LINE_LAYER_ID, GPS_ACCURACY_SOURCE_ID).withProperties(
                lineColor(hex),
                lineWidth(1.8f),
                lineDasharray(arrayOf(4f, 3f))
            )
        )
    } else {
        accSource.setGeoJson(accuracyFeature)
        style.getLayerAs<FillLayer>(GPS_ACCURACY_FILL_LAYER_ID)?.setProperties(fillColor(hex))
        style.getLayerAs<LineLayer>(GPS_ACCURACY_LINE_LAYER_ID)?.setProperties(lineColor(hex))
    }

    val centerSource = style.getSourceAs<GeoJsonSource>(GPS_CENTER_SOURCE_ID)
    if (centerSource == null) {
        style.addSource(GeoJsonSource(GPS_CENTER_SOURCE_ID, centerFeature))
        style.addLayer(
            CircleLayer(GPS_CENTER_LAYER_ID, GPS_CENTER_SOURCE_ID).withProperties(
                circleRadius(6f),
                circleColor("#E6EBE8"),
                circleStrokeColor(hex),
                circleStrokeWidth(2.5f)
            )
        )
    } else {
        centerSource.setGeoJson(centerFeature)
        style.getLayerAs<CircleLayer>(GPS_CENTER_LAYER_ID)?.setProperties(circleStrokeColor(hex))
    }
}

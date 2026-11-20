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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.urbext.core.geo.GeoUtils
import com.urbext.core.location.AdaptiveLocationClient
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.color
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.GeoPoint
import com.urbext.domain.model.GpsFix
import com.urbext.domain.model.MapLayerMode
import com.urbext.domain.model.OfflineMapSector
import com.urbext.domain.model.PowerProfile
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.SecurityLevel
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.ZONE_COLOR_PRESETS
import com.urbext.domain.model.ZoneStyle
import com.urbext.domain.model.allPhotos
import com.urbext.domain.model.defaultZoneHex
import com.urbext.domain.model.effectiveZoneColor
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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
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
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
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
private const val OBJECT_ZONES_LINE_DASHED_LAYER_ID = "urbex-object-zones-line-dashed"
private const val OBJECT_ZONES_LINE_DOTTED_LAYER_ID = "urbex-object-zones-line-dotted"
private const val ZONE_DRAFT_SOURCE_ID = "urbex-zone-draft-source"
private const val ZONE_DRAFT_FILL_LAYER_ID = "urbex-zone-draft-fill"
private const val ZONE_DRAFT_HATCH_LAYER_ID = "urbex-zone-draft-hatch"
private const val ZONE_DRAFT_LINE_SOLID_LAYER_ID = "urbex-zone-draft-line-solid"
private const val ZONE_DRAFT_LINE_DASHED_LAYER_ID = "urbex-zone-draft-line-dashed"
private const val ZONE_DRAFT_LINE_DOTTED_LAYER_ID = "urbex-zone-draft-line-dotted"
private const val ZONE_DRAFT_VERTEX_LAYER_ID = "urbex-zone-draft-vertices"

data class MapFocusRequest(
    val objectId: String,
    val lat: Double,
    val lon: Double,
    val token: Long
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
    var isPollingGps by remember { mutableStateOf(false) }
    var gpsStatusNote by remember { mutableStateOf<String?>(null) }

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
    var draftZoneHatched by remember { mutableStateOf(false) }
    var zoneDrawMode by remember { mutableStateOf("points") } // "points" | "freehand"

    val selectedObject = remember(objects, selectedObjectId) {
        selectedObjectId?.let { id -> objects.firstOrNull { it.id == id } }
    }
    val zoneEditingObject = remember(objects, zoneEditingObjectId) {
        zoneEditingObjectId?.let { id -> objects.firstOrNull { it.id == id } }
    }
    val isEditingZone = settings.editorEnabled && zoneEditingObject != null
    val showNormalHud = !isEditingZone && !hideHud

    LaunchedEffect(showNormalHud) {
        if (!showNormalHud) {
            toolsOpen = false
        }
    }

    LaunchedEffect(cursorLat, cursorLon) {
        onCursorChange(cursorLat, cursorLon)
    }

    val latestObjects by rememberUpdatedState(objects)
    val latestZoneEditingId by rememberUpdatedState(zoneEditingObjectId)
    val latestZoneDrawMode by rememberUpdatedState(zoneDrawMode)
    val latestShowZones by rememberUpdatedState(settings.showObjectZones)
    val latestEditorEnabled by rememberUpdatedState(settings.editorEnabled)

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

    LaunchedEffect(hasLocationPermission, settings.powerProfile) {
        if (hasLocationPermission && settings.powerProfile != PowerProfile.MANUAL) {
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
    val signalColor by animateColorAsState(
        targetValue = targetSignalColor,
        animationSpec = tween(280),
        label = "gpsSignalColor"
    )
    val gpsPulseTransition = rememberInfiniteTransition(label = "gpsDotPulse")
    val gpsPulseScale by gpsPulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Restart
        ),
        label = "gpsPulseScale"
    )
    val gpsPulseAlpha by gpsPulseTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Restart
        ),
        label = "gpsPulseAlpha"
    )

    val styleJson = if (settings.mapLayer == MapLayerMode.SATELLITE) SATELLITE_STYLE_JSON else OFFLINE_DARK_STYLE_JSON
    var appliedStyleJson by remember { mutableStateOf<String?>(null) }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        val options = MapLibreMapOptions.createFromAttributes(context, null)
            .textureMode(true)
        MapView(context, options).apply {
            getMapAsync { map ->
                mapInstance = map
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isAttributionEnabled = false
                map.uiSettings.isCompassEnabled = false
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(currentFix.lat, currentFix.lon))
                    .zoom(14.0)
                    .build()

                map.addOnCameraMoveListener {
                    val target = map.cameraPosition.target
                    if (target != null) {
                        cursorLat = target.latitude
                        cursorLon = target.longitude
                    }
                    currentZoom = map.cameraPosition.zoom
                    cameraBearing = ((map.cameraPosition.bearing.roundToInt() % 360) + 360) % 360
                }

                map.addOnMapClickListener { latLng ->
                    if (latestZoneEditingId != null && latestEditorEnabled) {
                        if (latestZoneDrawMode == "points") {
                            val pt = GeoPoint(
                                lat = String.format(Locale.US, "%.6f", latLng.latitude).toDouble(),
                                lon = String.format(Locale.US, "%.6f", latLng.longitude).toDouble()
                            )
                            draftZone = draftZone + pt
                            return@addOnMapClickListener true
                        }
                        return@addOnMapClickListener false
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
    }

    LaunchedEffect(zoneEditingObjectId) {
        val target = zoneEditingObjectId?.let { id -> objects.firstOrNull { it.id == id } }
        if (target != null) {
            selectedObjectId = null
            draftZone = target.zone
            draftZoneColor = target.zoneColor.orEmpty()
            draftZoneStyle = target.zoneStyle
            draftZoneHatched = target.zoneHatched
            mapInstance?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lon), 16.0)
            )
        } else {
            draftZone = emptyList()
            draftZoneColor = ""
            draftZoneStyle = ZoneStyle.SOLID
            draftZoneHatched = false
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

    LaunchedEffect(currentFix, settings.autoCenterGps, zoneEditingObjectId, suppressAutoCenter) {
        if (settings.autoCenterGps && zoneEditingObjectId == null && !suppressAutoCenter) {
            mapInstance?.animateCamera(
                CameraUpdateFactory.newLatLng(LatLng(currentFix.lat, currentFix.lon))
            )
        }
    }

    LaunchedEffect(styleJson) {
        if (appliedStyleJson == styleJson) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                appliedStyleJson = styleJson
                updateContoursOverlay(style, showContours && !settings.lowMemoryMode)
                updateGridOverlay(style, cursorLat, cursorLon, currentZoom, settings.showMapGrid && !settings.lowMemoryMode)
                updateSectorsOverlay(style, settings.offlineSectors, showSectors)
                updateObjectZonesOverlay(style, objects, selectedObjectId, zoneEditingObjectId, settings.showObjectZones)
                updateGpsAccuracyOverlay(style, currentFix.lat, currentFix.lon, currentFix.accuracyM)
                val activeDraftColor = normalizeZoneColor(draftZoneColor)
                    ?: zoneEditingObject?.status?.defaultZoneHex()
                    ?: "#5B9D72"
                updateDraftZoneOverlay(style, draftZone, activeDraftColor, draftZoneStyle, draftZoneHatched)
                updateObjectMarkersOverlay(context, style, objects, selectedObjectId, settings.showObjectLabels)
            }
        }
    }

    LaunchedEffect(currentFix) {
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                updateGpsAccuracyOverlay(style, currentFix.lat, currentFix.lon, currentFix.accuracyM)
            }
        }
    }

    LaunchedEffect(objects, selectedObjectId, zoneEditingObjectId, settings.showObjectZones, settings.showObjectLabels) {
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                updateObjectZonesOverlay(style, objects, selectedObjectId, zoneEditingObjectId, settings.showObjectZones)
                updateObjectMarkersOverlay(context, style, objects, selectedObjectId, settings.showObjectLabels)
            }
        }
    }

    LaunchedEffect(draftZone, draftZoneColor, draftZoneStyle, draftZoneHatched, zoneEditingObject?.status) {
        val activeDraftColor = normalizeZoneColor(draftZoneColor)
            ?: zoneEditingObject?.status?.defaultZoneHex()
            ?: "#5B9D72"
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                updateDraftZoneOverlay(style, draftZone, activeDraftColor, draftZoneStyle, draftZoneHatched)
            }
        }
    }

    LaunchedEffect(settings.offlineSectors, showSectors) {
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                updateSectorsOverlay(style, settings.offlineSectors, showSectors)
            }
        }
    }

    LaunchedEffect(showContours, settings.lowMemoryMode) {
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                updateContoursOverlay(style, showContours && !settings.lowMemoryMode)
            }
        }
    }

    LaunchedEffect(settings.showMapGrid, settings.lowMemoryMode, cursorLat, cursorLon, currentZoom) {
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                updateGridOverlay(style, cursorLat, cursorLon, currentZoom, settings.showMapGrid && !settings.lowMemoryMode)
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

        if (settings.editorEnabled && zoneEditingObject != null && zoneDrawMode == "freehand") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(zoneEditingObject.id, zoneDrawMode) {
                        detectDragGestures(
                            onDragStart = { offset ->
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
                                draftZone = GeoUtils.simplifyPolygonPoints(
                                    draftZone,
                                    minSpacingMeters = 6.0,
                                    maxPoints = 48
                                )
                            }
                        )
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
                            Text(
                                text = "HDG $cameraBearing°",
                                color = UrbexColors.Amber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                maxLines = 1
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
                        Box(
                            modifier = Modifier.size(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .graphicsLayer {
                                        scaleX = gpsPulseScale
                                        scaleY = gpsPulseScale
                                        alpha = gpsPulseAlpha
                                    }
                                    .clip(CircleShape)
                                    .background(signalColor)
                            )
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

                AnimatedVisibility(
                    visible = nearbyHazard != null,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
                ) {
                    nearbyHazard?.let { (hazardObj, hazardDist) ->
                        Row(
                            modifier = Modifier
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
            if (isEditingZone && !hideHud && zoneEditingObject != null) {
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

                    // Interactive Zone Drawing HUD
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .heightIn(max = if (isLandscape) 275.dp else 520.dp)
                            .background(UrbexColors.Surface.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
                            .border(1.dp, UrbexColors.Green, RoundedCornerShape(14.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(if (isLandscape) 12.dp else 14.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp)
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Разметка зоны объекта",
                                color = UrbexColors.GreenBright,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = zoneEditingObject.title,
                                color = UrbexColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${draftZone.size} тчк",
                                color = UrbexColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                            if (draftZone.size >= 3) {
                                val a = GeoUtils.formatArea(GeoUtils.computePolygonAreaM2(draftZone))
                                val p = GeoUtils.formatDistance(GeoUtils.computePolygonPerimeterMeters(draftZone))
                                Text(
                                    text = "$a · P=$p",
                                    color = UrbexColors.TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            label = "Круг R=${settings.defaultZoneRadiusM} м",
                            accentColor = UrbexColors.Amber,
                            onClick = {
                                draftZone = GeoUtils.buildZoneCircle(
                                    zoneEditingObject.lat,
                                    zoneEditingObject.lon,
                                    settings.defaultZoneRadiusM.toDouble()
                                )
                            }
                        )
                    }

                    HorizontalDivider(color = UrbexColors.Border, thickness = 1.dp)

                    Text("Стиль и штриховка зоны", color = UrbexColors.TextSecondary, fontSize = 10.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ZoneStyle.entries.forEach { st ->
                            val selected = draftZoneStyle == st
                            Text(
                                text = st.label,
                                color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .background(
                                        if (selected) UrbexColors.Elevated else UrbexColors.Background,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) UrbexColors.Green else UrbexColors.Border,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .urbexBounceClick(0.95f) { draftZoneStyle = st }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                        Text(
                            text = if (draftZoneHatched) "Штриховка: Вкл" else "Штриховка: Выкл",
                            color = if (draftZoneHatched) UrbexColors.Amber else UrbexColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (draftZoneHatched) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .background(
                                    if (draftZoneHatched) UrbexColors.Amber.copy(alpha = 0.14f) else UrbexColors.Background,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (draftZoneHatched) UrbexColors.Amber else UrbexColors.Border,
                                    RoundedCornerShape(8.dp)
                                )
                                .urbexBounceClick(0.95f) { draftZoneHatched = !draftZoneHatched }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }

                    val normalizedHudColor = normalizeZoneColor(draftZoneColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    if (normalizedHudColor == null) UrbexColors.Elevated else UrbexColors.Background,
                                    RoundedCornerShape(7.dp)
                                )
                                .border(
                                    1.dp,
                                    if (normalizedHudColor == null) UrbexColors.Green else UrbexColors.Border,
                                    RoundedCornerShape(7.dp)
                                )
                                .urbexBounceClick(0.94f) { draftZoneColor = "" }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                        ZONE_COLOR_PRESETS.forEach { (presetHex, presetLabel) ->
                            val isSelected = normalizedHudColor == presetHex
                            val swatchColor = remember(presetHex) { Color(android.graphics.Color.parseColor(presetHex)) }
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
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
                            onValueChange = { draftZoneColor = it },
                            placeholder = "#5B9D72",
                            minHeight = 30.dp,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = if (zoneDrawMode == "points") {
                            "Нажимайте на карту для добавления точек периметра."
                        } else {
                            "Проведите пальцем по карте, чтобы обвести контур объекта."
                        } + if (draftZone.size in 1..2) " Нужно минимум 3 точки." else "",
                        color = if (draftZone.size in 1..2) UrbexColors.Amber else UrbexColors.TextSecondary,
                        fontSize = 11.sp
                    )

                    val canSaveZone = draftZone.isEmpty() || draftZone.size >= 3
                    val saveZoneAction = {
                        if (canSaveZone) {
                            scope.launch {
                                objectRepository.upsertObject(
                                    zoneEditingObject.copy(
                                        zone = draftZone,
                                        zoneColor = normalizeZoneColor(draftZoneColor),
                                        zoneStyle = draftZoneStyle,
                                        zoneHatched = draftZoneHatched,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                onClearZoneEdit()
                            }
                        }
                    }

                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                HudActionPill(
                                    label = "Назад",
                                    icon = Icons.AutoMirrored.Filled.Undo,
                                    accentColor = UrbexColors.TextPrimary,
                                    onClick = {
                                        if (draftZone.isNotEmpty()) {
                                            draftZone = draftZone.dropLast(1)
                                        }
                                    }
                                )
                                HudActionPill(
                                    label = "Очистить",
                                    icon = Icons.Default.Delete,
                                    accentColor = Color(0xFFF1ACA7),
                                    onClick = { draftZone = emptyList() }
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .height(32.dp)
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
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (canSaveZone) UrbexColors.Green else UrbexColors.Elevated,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .urbexBounceClick(0.95f) { saveZoneAction() }
                                        .padding(horizontal = 12.dp),
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
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                            if (draftZone.isNotEmpty()) {
                                                draftZone = draftZone.dropLast(1)
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = UrbexColors.TextPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Назад", color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                        .urbexBounceClick(0.95f) { draftZone = emptyList() },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF1ACA7), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Очистить", color = Color(0xFFF1ACA7), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
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
                                        .urbexBounceClick(0.95f) { onClearZoneEdit() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Отмена", color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (canSaveZone) UrbexColors.Green else UrbexColors.Elevated,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .urbexBounceClick(0.95f) { saveZoneAction() },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (canSaveZone) UrbexColors.Background else UrbexColors.TextDim,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Сохранить",
                                        color = if (canSaveZone) UrbexColors.Background else UrbexColors.TextDim,
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
    onRequestZoneEdit: () -> Unit,
    onOpenObject: () -> Unit
) {
    val distM = GeoUtils.haversineDistanceMeters(currentFix.lat, currentFix.lon, activeObj.lat, activeObj.lon)
    val azDeg = GeoUtils.bearingDegrees(currentFix.lat, currentFix.lon, activeObj.lat, activeObj.lon)
    val openBtnInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = if (compactLandscape) 215.dp else 440.dp)
            .background(UrbexColors.Surface.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(if (compactLandscape) 11.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 6.dp else 8.dp)
    ) {
        if (compactLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                val cardPhotos = activeObj.allPhotos()
                val coverPhoto = cardPhotos.firstOrNull()
                if (coverPhoto != null) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(UrbexColors.Background)
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = coverPhoto,
                            contentDescription = activeObj.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                        if (cardPhotos.size > 1) {
                            Text(
                                text = "${cardPhotos.size}",
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
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${activeObj.status.glyph} ${activeObj.status.label}",
                        color = activeObj.status.color(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeObj.title,
                        color = UrbexColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = GeoUtils.formatDistance(distM),
                            color = UrbexColors.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "$azDeg°",
                            color = UrbexColors.Amber,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                    if (activeObj.zone.size >= 3) {
                        val a = GeoUtils.formatArea(GeoUtils.computePolygonAreaM2(activeObj.zone))
                        Text(
                            text = "Зона: $a",
                            color = UrbexColors.GreenBright,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Скрыть", tint = UrbexColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${activeObj.status.glyph} ${activeObj.status.label} · ${activeObj.category.label}",
                        color = activeObj.status.color(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = activeObj.title,
                        color = UrbexColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Скрыть", tint = UrbexColors.TextSecondary)
                }
            }

            val cardPhotos = activeObj.allPhotos()
            val coverPhoto = cardPhotos.firstOrNull()
            if (coverPhoto != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(UrbexColors.Background)
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = coverPhoto,
                        contentDescription = activeObj.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    if (cardPhotos.size > 1) {
                        Text(
                            text = "${cardPhotos.size} фото",
                            color = UrbexColors.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(Color(0xCC0D110D), RoundedCornerShape(6.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Дистанция: ${GeoUtils.formatDistance(distM)}",
                    color = UrbexColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Text(
                    text = "Азимут: $azDeg°",
                    color = UrbexColors.Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }

            if (activeObj.securityLevel != SecurityLevel.NONE) {
                Text(
                    text = "Режим: ${activeObj.securityLevel.label}",
                    color = UrbexColors.Amber,
                    fontSize = 11.sp
                )
            }
            if (activeObj.zone.size >= 3) {
                val a = GeoUtils.formatArea(GeoUtils.computePolygonAreaM2(activeObj.zone))
                val p = GeoUtils.formatDistance(GeoUtils.computePolygonPerimeterMeters(activeObj.zone))
                Text(
                    text = "Зона: $a · P=$p",
                    color = UrbexColors.GreenBright,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            Text(
                text = activeObj.description,
                color = UrbexColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        val actionHeight = if (compactLandscape) 34.dp else 38.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canEdit) {
                OutlinedButton(
                    onClick = onRequestZoneEdit,
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, UrbexColors.Green),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(actionHeight)
                ) {
                    Text("Зона", color = UrbexColors.GreenBright, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }
            Button(
                onClick = onOpenObject,
                shape = RoundedCornerShape(9.dp),
                interactionSource = openBtnInteraction,
                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(actionHeight)
                    .urbexScaleOnPress(openBtnInteraction, 0.97f)
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

private fun createObjectBadgeBitmap(
    context: Context,
    obj: UrbexObject,
    isSelected: Boolean
): Bitmap {
    val density = context.resources.displayMetrics.density
    val shortTitle = if (obj.title.length > 18) obj.title.take(17) + "…" else obj.title
    val label = "${obj.status.glyph} $shortTitle"

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(230, 235, 232)
        textSize = 11.5f * density
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val textWidth = textPaint.measureText(label)
    val padH = 9f * density
    val w = (textWidth + padH * 2f).roundToInt().coerceAtLeast((40f * density).roundToInt())
    val h = (26f * density).roundToInt()

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val rect = RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
    val radius = 6f * density

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (isSelected) {
            android.graphics.Color.argb(245, 26, 32, 30)
        } else {
            android.graphics.Color.argb(232, 19, 24, 22)
        }
    }
    canvas.drawRoundRect(rect, radius, radius, fillPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (if (isSelected) 2.0f else 1.2f) * density
        color = if (isSelected) {
            android.graphics.Color.rgb(91, 157, 114)
        } else {
            android.graphics.Color.rgb(37, 46, 42)
        }
    }
    canvas.drawRoundRect(rect, radius, radius, strokePaint)

    val glyphStr = obj.status.glyph
    val glyphPaint = Paint(textPaint).apply {
        color = obj.status.androidColorInt()
    }
    val baseline = (h / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(glyphStr, padH, baseline, glyphPaint)

    val glyphWidth = glyphPaint.measureText("$glyphStr ")
    canvas.drawText(shortTitle, padH + glyphWidth, baseline, textPaint)
    return bitmap
}

private fun ensureHatchPatternImage(mapStyle: Style, hex: String): String {
    val normalized = normalizeZoneColor(hex) ?: "#5B9D72"
    val imageId = "urbex-hatch-${normalized.removePrefix("#")}"
    if (mapStyle.getImage(imageId) == null) {
        val size = 24
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseColor = android.graphics.Color.parseColor(normalized)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(
                195,
                android.graphics.Color.red(baseColor),
                android.graphics.Color.green(baseColor),
                android.graphics.Color.blue(baseColor)
            )
            strokeWidth = 3.2f
            style = Paint.Style.STROKE
        }
        for (offset in -size..(size * 2) step 12) {
            canvas.drawLine(offset.toFloat(), size.toFloat(), (offset + size).toFloat(), 0f, paint)
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
    val visValue = if (visible) Property.VISIBLE else Property.NONE
    val features = objects.mapNotNull { obj ->
        if (obj.id == zoneEditingObjectId || obj.zone.size < 3) return@mapNotNull null
        val zoneHex = obj.effectiveZoneColor()
        val hatchId = if (obj.zoneHatched) ensureHatchPatternImage(style, zoneHex) else ""
        val ring = (obj.zone + obj.zone.first()).map { Point.fromLngLat(it.lon, it.lat) }
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
            addStringProperty("objectId", obj.id)
            addStringProperty("zoneHex", zoneHex)
            addStringProperty("zoneStyle", obj.zoneStyle.name)
            addStringProperty("hatched", if (obj.zoneHatched) "1" else "0")
            addStringProperty("hatchId", hatchId)
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
                .withFilter(Expression.neq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.OUTLINE.name)))
        )
        style.addLayer(
            FillLayer(OBJECT_ZONES_HATCH_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    fillPattern(Expression.get("hatchId")),
                    fillOpacity(0.55f),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("hatched"), Expression.literal("1")))
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
            LineLayer(OBJECT_ZONES_LINE_DASHED_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor(Expression.get("zoneHex")),
                    lineWidth(2.1f),
                    lineDasharray(arrayOf(4f, 3f)),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.DASHED.name)))
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_DOTTED_LAYER_ID, OBJECT_ZONES_SOURCE_ID)
                .withProperties(
                    lineColor(Expression.get("zoneHex")),
                    lineWidth(2.2f),
                    lineDasharray(arrayOf(1.2f, 2.4f)),
                    visibility(visValue)
                )
                .withFilter(Expression.eq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.DOTTED.name)))
        )
    } else {
        source.setGeoJson(collection)
        style.getLayerAs<FillLayer>(OBJECT_ZONES_FILL_LAYER_ID)?.setProperties(visibility(visValue))
        style.getLayerAs<FillLayer>(OBJECT_ZONES_HATCH_LAYER_ID)?.setProperties(visibility(visValue))
        style.getLayerAs<LineLayer>(OBJECT_ZONES_LINE_SOLID_LAYER_ID)?.setProperties(visibility(visValue))
        style.getLayerAs<LineLayer>(OBJECT_ZONES_LINE_DASHED_LAYER_ID)?.setProperties(visibility(visValue))
        style.getLayerAs<LineLayer>(OBJECT_ZONES_LINE_DOTTED_LAYER_ID)?.setProperties(visibility(visValue))
    }
}

private fun updateDraftZoneOverlay(
    style: Style,
    draftZone: List<GeoPoint>,
    zoneColorHex: String = "#5B9D72",
    zoneStyle: ZoneStyle = ZoneStyle.SOLID,
    zoneHatched: Boolean = false
) {
    val activeHex = normalizeZoneColor(zoneColorHex) ?: "#5B9D72"
    val hatchImageId = ensureHatchPatternImage(style, activeHex)
    val features = mutableListOf<Feature>()
    if (draftZone.size >= 3) {
        val ring = (draftZone + draftZone.first()).map { Point.fromLngLat(it.lon, it.lat) }
        features.add(
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
                addStringProperty("kind", "poly")
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

    val fillVis = if (zoneStyle != ZoneStyle.OUTLINE) Property.VISIBLE else Property.NONE
    val hatchVis = if (zoneHatched) Property.VISIBLE else Property.NONE
    val solidVis = if (zoneStyle == ZoneStyle.SOLID || zoneStyle == ZoneStyle.OUTLINE) Property.VISIBLE else Property.NONE
    val dashedVis = if (zoneStyle == ZoneStyle.DASHED) Property.VISIBLE else Property.NONE
    val dottedVis = if (zoneStyle == ZoneStyle.DOTTED) Property.VISIBLE else Property.NONE

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
                fillPattern(hatchImageId),
                fillOpacity(0.6f),
                visibility(hatchVis)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("poly")))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_SOLID_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(activeHex),
                lineWidth(2.6f),
                visibility(solidVis)
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
                lineDasharray(arrayOf(1.2f, 2.4f)),
                visibility(dottedVis)
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
        style.getLayerAs<FillLayer>(ZONE_DRAFT_HATCH_LAYER_ID)?.setProperties(fillPattern(hatchImageId), visibility(hatchVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_SOLID_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(solidVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_DASHED_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(dashedVis))
        style.getLayerAs<LineLayer>(ZONE_DRAFT_LINE_DOTTED_LAYER_ID)?.setProperties(lineColor(activeHex), visibility(dottedVis))
    }
}

private fun updateObjectMarkersOverlay(
    context: Context,
    style: Style,
    objects: List<UrbexObject>,
    selectedObjectId: String?,
    showLabels: Boolean
) {
    val visValue = if (showLabels) Property.VISIBLE else Property.NONE
    val features = objects.map { obj ->
        val isSelected = obj.id == selectedObjectId
        val iconId = "urbex-pin-${obj.id}-${if (isSelected) 1 else 0}"
        val bmp = createObjectBadgeBitmap(context, obj, isSelected)
        style.addImage(iconId, bmp)

        Feature.fromGeometry(Point.fromLngLat(obj.lon, obj.lat)).apply {
            addStringProperty("objectId", obj.id)
            addStringProperty("iconId", iconId)
            addStringProperty("statusHex", obj.status.hexColor())
        }
    }
    val collection = FeatureCollection.fromFeatures(features)
    val source = style.getSourceAs<GeoJsonSource>(OBJECTS_SOURCE_ID)
    if (source == null) {
        style.addSource(GeoJsonSource(OBJECTS_SOURCE_ID, collection))
        style.addLayer(
            CircleLayer(OBJECTS_DOT_LAYER_ID, OBJECTS_SOURCE_ID).withProperties(
                circleRadius(5.5f),
                circleColor(Expression.get("statusHex")),
                circleStrokeColor("#E6EBE8"),
                circleStrokeWidth(1.5f)
            )
        )
        style.addLayer(
            SymbolLayer(OBJECTS_BADGE_LAYER_ID, OBJECTS_SOURCE_ID).withProperties(
                iconImage(Expression.get("iconId")),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                iconOffset(arrayOf(0f, -8f)),
                visibility(visValue)
            )
        )
    } else {
        source.setGeoJson(collection)
        style.getLayerAs<SymbolLayer>(OBJECTS_BADGE_LAYER_ID)?.setProperties(visibility(visValue))
    }
}

private fun updateSectorsOverlay(
    style: Style,
    sectors: List<OfflineMapSector>,
    visible: Boolean
) {
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
    visible: Boolean
) {
    val visValue = if (visible) Property.VISIBLE else Property.NONE
    val step = when {
        zoom >= 15.0 -> 0.005
        zoom >= 12.0 -> 0.02
        zoom >= 9.0 -> 0.1
        else -> 0.5
    }
    val snapLat = kotlin.math.floor(centerLat / step) * step
    val snapLon = kotlin.math.floor(centerLon / step) * step
    val minLat = snapLat - step * 6
    val maxLat = snapLat + step * 6
    val minLon = snapLon - step * 6
    val maxLon = snapLon + step * 6

    val features = mutableListOf<Feature>()
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
    val source = style.getSourceAs<GeoJsonSource>(GRID_SOURCE_ID)
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
        style.getLayerAs<LineLayer>(GRID_LINE_LAYER_ID)?.setProperties(visibility(visValue))
    }
}

private fun updateGpsAccuracyOverlay(
    style: Style,
    lat: Double,
    lon: Double,
    accuracyM: Int?
) {
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

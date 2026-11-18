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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.platform.LocalContext
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
import com.urbext.core.ui.ObjectEditorDialog
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.color
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.GeoPoint
import com.urbext.domain.model.GpsFix
import com.urbext.domain.model.MapLayerMode
import com.urbext.domain.model.OfflineMapSector
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.PowerProfile
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.SecurityLevel
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.ProfileRepository
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
private const val OBJECT_ZONES_LINE_LAYER_ID = "urbex-object-zones-line"
private const val ZONE_DRAFT_SOURCE_ID = "urbex-zone-draft-source"
private const val ZONE_DRAFT_FILL_LAYER_ID = "urbex-zone-draft-fill"
private const val ZONE_DRAFT_LINE_LAYER_ID = "urbex-zone-draft-line"
private const val ZONE_DRAFT_VERTEX_LAYER_ID = "urbex-zone-draft-vertices"

@Composable
fun MapScreen(
    objectRepository: ObjectRepository,
    settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository,
    onOpenObject: (String) -> Unit,
    zoneEditingObjectId: String? = null,
    onRequestZoneEdit: (String) -> Unit = {},
    onClearZoneEdit: () -> Unit = {},
    isActive: Boolean = true,
    bottomOverlayPadding: Dp = 92.dp
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val objects by objectRepository.observeObjects().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = AppSettings())
    val profile by profileRepository.observeProfile().collectAsStateWithLifecycle(
        initialValue = UserProfile(
            callsign = "Сокол-1",
            nodeId = "!a48f20c1",
            role = OperatorRole.SCOUT,
            emergencyContact = "",
            bio = "",
            updatedAt = 0L
        )
    )

    val locationClient = remember(context) { AdaptiveLocationClient(context) }
    var currentFix by remember { mutableStateOf(locationClient.getLastKnownFixOrDefault()) }
    var cursorLat by remember { mutableDoubleStateOf(currentFix.lat) }
    var cursorLon by remember { mutableDoubleStateOf(currentFix.lon) }
    var currentZoom by remember { mutableDoubleStateOf(14.0) }
    var isPollingGps by remember { mutableStateOf(false) }
    var gpsStatusNote by remember { mutableStateOf<String?>(null) }

    var showContours by remember { mutableStateOf(true) }
    var showSectors by remember { mutableStateOf(true) }
    var selectedObjectId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var sectorCachedToast by remember { mutableStateOf<String?>(null) }

    var draftZone by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var zoneDrawMode by remember { mutableStateOf("points") } // "points" | "freehand"

    val selectedObject = remember(objects, selectedObjectId) {
        selectedObjectId?.let { id -> objects.firstOrNull { it.id == id } }
    }
    val zoneEditingObject = remember(objects, zoneEditingObjectId) {
        zoneEditingObjectId?.let { id -> objects.firstOrNull { it.id == id } }
    }
    val latestObjects by rememberUpdatedState(objects)
    val latestZoneEditingId by rememberUpdatedState(zoneEditingObjectId)
    val latestZoneDrawMode by rememberUpdatedState(zoneDrawMode)
    val latestShowZones by rememberUpdatedState(settings.showObjectZones)

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
            isPollingGps = true
            locationClient.observeLocationUpdates(settings.powerProfile).collect { fix ->
                currentFix = fix
                gpsStatusNote = null
            }
        } else {
            isPollingGps = false
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
                }

                map.addOnMapClickListener { latLng ->
                    if (latestZoneEditingId != null) {
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

    LaunchedEffect(zoneEditingObjectId) {
        val target = zoneEditingObjectId?.let { id -> objects.firstOrNull { it.id == id } }
        if (target != null) {
            selectedObjectId = null
            draftZone = target.zone
            mapInstance?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lon), 16.0)
            )
        } else {
            draftZone = emptyList()
        }
    }

    LaunchedEffect(currentFix, settings.autoCenterGps, zoneEditingObjectId) {
        if (settings.autoCenterGps && zoneEditingObjectId == null) {
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
                updateSectorsOverlay(style, settings.offlineSectors, showSectors)
                updateObjectZonesOverlay(style, objects, selectedObjectId, zoneEditingObjectId, settings.showObjectZones)
                updateGpsAccuracyOverlay(style, currentFix.lat, currentFix.lon, currentFix.accuracyM)
                updateDraftZoneOverlay(style, draftZone)
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

    LaunchedEffect(draftZone) {
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                updateDraftZoneOverlay(style, draftZone)
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

        if (zoneEditingObject != null && zoneDrawMode == "freehand") {
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

        // Subtle Center Camera Crosshair
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

        // Top-Left Floating Telemetry HUD + Quick Actions
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp, end = 68.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(UrbexColors.Surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = GeoUtils.formatCoordinates(cursorLat, cursorLon, settings.coordinateFormat),
                        color = UrbexColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text("·", color = UrbexColors.TextDim, fontSize = 12.sp)
                    Text(
                        text = "$scaleBarMeters м",
                        color = UrbexColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier.size(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
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
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(signalColor)
                        )
                    }
                    val accText = currentFix.accuracyM?.let { " ±${it} м" } ?: ""
                    Text(
                        text = "GPS: ${signalLevel.label}$accText · ${settings.powerProfile.label}",
                        color = signalColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (gpsStatusNote != null) {
                    Text(
                        text = gpsStatusNote.orEmpty(),
                        color = UrbexColors.Amber,
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }
            }

            // Quick GPS & Layer Pills
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudActionPill(
                    label = if (isPollingGps) "Опрос..." else "Запросить GPS",
                    accentColor = UrbexColors.TextPrimary,
                    onClick = {
                        if (!hasLocationPermission) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            currentFix = locationClient.getLastKnownFixOrDefault(currentFix.lat, currentFix.lon)
                            gpsStatusNote = null
                        }
                    }
                )
                HudActionPill(
                    label = "Фикс к курсору",
                    icon = Icons.Default.Navigation,
                    accentColor = UrbexColors.Amber,
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
                    }
                )
                HudActionPill(
                    label = settings.mapLayer.label,
                    icon = Icons.Default.Public,
                    accentColor = if (settings.mapLayer == MapLayerMode.SATELLITE) UrbexColors.GreenBright else UrbexColors.TextSecondary,
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
            }
        }

        // Right Edge Vertical Map Controls (+ / - / Center GPS / Contours / Sectors)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MapIconControl(
                icon = Icons.Default.Add,
                contentDescription = "Приблизить карту",
                tint = UrbexColors.TextPrimary,
                onClick = {
                    mapInstance?.animateCamera(CameraUpdateFactory.zoomIn())
                }
            )
            MapIconControl(
                icon = Icons.Default.Remove,
                contentDescription = "Отдалить карту",
                tint = UrbexColors.TextPrimary,
                onClick = {
                    mapInstance?.animateCamera(CameraUpdateFactory.zoomOut())
                }
            )
            MapIconControl(
                icon = Icons.Default.MyLocation,
                contentDescription = "Центрировать по GPS",
                tint = UrbexColors.GreenBright,
                onClick = {
                    if (!hasLocationPermission) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        currentFix = locationClient.getLastKnownFixOrDefault(currentFix.lat, currentFix.lon)
                        mapInstance?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(currentFix.lat, currentFix.lon), 15.0)
                        )
                    }
                }
            )
            MapIconControl(
                icon = Icons.Default.Layers,
                contentDescription = "Переключить векторные изолинии",
                tint = if (showContours) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                highlighted = showContours,
                onClick = { showContours = !showContours }
            )
            MapIconControl(
                icon = Icons.Default.Radar,
                contentDescription = "Переключить зоны офлайн-секторов",
                tint = if (showSectors) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                highlighted = showSectors,
                onClick = { showSectors = !showSectors }
            )
        }

        // Bottom Floating Overlay: Toast + Selected Card + Quick Actions + Object Chips
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = bottomOverlayPadding),
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

            if (zoneEditingObject != null) {
                // Interactive Zone Drawing HUD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .background(UrbexColors.Surface.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
                        .border(1.dp, UrbexColors.Green, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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

                    Text(
                        text = if (zoneDrawMode == "points") {
                            "Нажимайте на карту для добавления точек периметра."
                        } else {
                            "Проведите пальцем по карте, чтобы обвести контур объекта."
                        } + if (draftZone.size in 1..2) " Нужно минимум 3 точки." else "",
                        color = if (draftZone.size in 1..2) UrbexColors.Amber else UrbexColors.TextSecondary,
                        fontSize = 11.sp
                    )

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

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Отмена",
                                color = UrbexColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.95f) { onClearZoneEdit() }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                            val canSaveZone = draftZone.isEmpty() || draftZone.size >= 3
                            Row(
                                modifier = Modifier
                                    .background(
                                        if (canSaveZone) UrbexColors.Green else UrbexColors.Elevated,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .urbexBounceClick(0.95f) {
                                        if (!canSaveZone) return@urbexBounceClick
                                        scope.launch {
                                            objectRepository.upsertObject(
                                                zoneEditingObject.copy(
                                                    zone = draftZone,
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            )
                                            onClearZoneEdit()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                AnimatedContent(
                    targetState = selectedObject,
                    contentKey = { it?.id },
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.96f, animationSpec = tween(200)) + slideInVertically(tween(200)) { it / 4 })
                            .togetherWith(
                                fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150)) + slideOutVertically(tween(150)) { it / 4 }
                            )
                    },
                    label = "MapSelectedObjectCard"
                ) { activeObj ->
                    if (activeObj != null) {
                        val distM = GeoUtils.haversineDistanceMeters(currentFix.lat, currentFix.lon, activeObj.lat, activeObj.lon)
                        val azDeg = GeoUtils.bearingDegrees(currentFix.lat, currentFix.lon, activeObj.lat, activeObj.lon)
                        val openBtnInteraction = remember { MutableInteractionSource() }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .background(UrbexColors.Surface.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                    onClick = { selectedObjectId = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Скрыть", tint = UrbexColors.TextSecondary)
                                }
                            }

                            if (activeObj.photoUri != null) {
                                AsyncImage(
                                    model = activeObj.photoUri,
                                    contentDescription = activeObj.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(UrbexColors.Background)
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                )
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onRequestZoneEdit(activeObj.id) },
                                    border = BorderStroke(1.dp, UrbexColors.Green)
                                ) {
                                    Text("Зона", color = UrbexColors.GreenBright, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { onOpenObject(activeObj.id) },
                                    interactionSource = openBtnInteraction,
                                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                                    modifier = Modifier
                                        .weight(1f)
                                        .urbexScaleOnPress(openBtnInteraction, 0.97f)
                                ) {
                                    Text("Открыть на всю площадь", color = UrbexColors.Background, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Bottom Action Bar: Отметить точку + Кэшировать сектор
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .background(UrbexColors.Green, RoundedCornerShape(10.dp))
                        .urbexBounceClick(0.95f) { showCreateDialog = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLocationAlt,
                        contentDescription = null,
                        tint = UrbexColors.Background,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Отметить точку",
                        color = UrbexColors.Background,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .background(UrbexColors.Surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                        .urbexBounceClick(0.95f) {
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
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = UrbexColors.Amber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Кэшировать сектор",
                        color = UrbexColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(objects, key = { it.id }) { obj ->
                    val isSelected = selectedObjectId == obj.id
                    val dist = GeoUtils.haversineDistanceMeters(currentFix.lat, currentFix.lon, obj.lat, obj.lon)
                    val chipBg by animateColorAsState(
                        targetValue = (if (isSelected) UrbexColors.Elevated else UrbexColors.Surface).copy(alpha = 0.92f),
                        animationSpec = tween(160),
                        label = "chipBg"
                    )
                    val chipBorder by animateColorAsState(
                        targetValue = if (isSelected) UrbexColors.Green else UrbexColors.Border,
                        animationSpec = tween(160),
                        label = "chipBorder"
                    )
                    Column(
                        modifier = Modifier
                            .animateItem()
                            .background(chipBg, RoundedCornerShape(10.dp))
                            .border(1.dp, chipBorder, RoundedCornerShape(10.dp))
                            .urbexBounceClick(0.95f) {
                                selectedObjectId = obj.id
                                mapInstance?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(obj.lat, obj.lon), 15.0)
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${obj.status.glyph} ${obj.status.label}",
                            color = obj.status.color(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = obj.title,
                            color = UrbexColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${GeoUtils.formatDistance(dist)} · ${GeoUtils.bearingDegrees(currentFix.lat, currentFix.lon, obj.lat, obj.lon)}°",
                            color = UrbexColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
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

    if (showCreateDialog) {
        ObjectEditorDialog(
            initialLat = cursorLat,
            initialLon = cursorLon,
            initialElevationM = currentFix.altitudeM ?: 140,
            defaultZoneRadiusM = settings.defaultZoneRadiusM,
            authorNodeId = profile.nodeId,
            editingObject = null,
            onDismiss = { showCreateDialog = false },
            onSave = { newObj ->
                scope.launch {
                    objectRepository.upsertObject(newObj)
                    selectedObjectId = newObj.id
                    showCreateDialog = false
                }
            },
            onOpenZoneEditor = { newObj ->
                scope.launch {
                    objectRepository.upsertObject(newObj)
                    selectedObjectId = newObj.id
                    showCreateDialog = false
                    onRequestZoneEdit(newObj.id)
                }
            }
        )
    }
}

@Composable
private fun HudActionPill(
    label: String,
    accentColor: Color,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
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
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            color = UrbexColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun MapIconControl(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    highlighted: Boolean = false,
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
            .size(42.dp)
            .urbexScaleOnPress(interaction, 0.91f)
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
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
        val ring = (obj.zone + obj.zone.first()).map { Point.fromLngLat(it.lon, it.lat) }
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
            addStringProperty("objectId", obj.id)
            addStringProperty("statusHex", obj.status.hexColor())
        }
    }
    val collection = FeatureCollection.fromFeatures(features)
    val source = style.getSourceAs<GeoJsonSource>(OBJECT_ZONES_SOURCE_ID)
    if (source == null) {
        style.addSource(GeoJsonSource(OBJECT_ZONES_SOURCE_ID, collection))
        style.addLayer(
            FillLayer(OBJECT_ZONES_FILL_LAYER_ID, OBJECT_ZONES_SOURCE_ID).withProperties(
                fillColor(Expression.get("statusHex")),
                fillOpacity(0.18f),
                visibility(visValue)
            )
        )
        style.addLayer(
            LineLayer(OBJECT_ZONES_LINE_LAYER_ID, OBJECT_ZONES_SOURCE_ID).withProperties(
                lineColor(Expression.get("statusHex")),
                lineWidth(2.0f),
                visibility(visValue)
            )
        )
    } else {
        source.setGeoJson(collection)
        style.getLayerAs<FillLayer>(OBJECT_ZONES_FILL_LAYER_ID)?.setProperties(visibility(visValue))
        style.getLayerAs<LineLayer>(OBJECT_ZONES_LINE_LAYER_ID)?.setProperties(visibility(visValue))
    }
}

private fun updateDraftZoneOverlay(
    style: Style,
    draftZone: List<GeoPoint>
) {
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
                addStringProperty("vertexHex", if (idx == 0) "#DF9E43" else "#6DB386")
            }
        )
    }

    val collection = FeatureCollection.fromFeatures(features)
    val source = style.getSourceAs<GeoJsonSource>(ZONE_DRAFT_SOURCE_ID)
    if (source == null) {
        style.addSource(GeoJsonSource(ZONE_DRAFT_SOURCE_ID, collection))
        style.addLayer(
            FillLayer(ZONE_DRAFT_FILL_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                fillColor("#5B9D72"),
                fillOpacity(0.26f)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("poly")))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor("#6DB386"),
                lineWidth(2.6f)
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

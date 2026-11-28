package com.urbext.feature.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
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
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbext.core.geo.GeoUtils
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.color
import com.urbext.core.ui.drawObjectIconOnCanvas
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexGlow
import com.urbext.core.ui.vector
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.GeoPoint
import com.urbext.domain.model.GpsFix
import com.urbext.domain.model.ObjectIcon
import com.urbext.domain.model.OfflineMapSector
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.ZONE_COLOR_PRESETS
import com.urbext.domain.model.ZonePattern
import com.urbext.domain.model.ZoneStyle
import com.urbext.domain.model.defaultIcon
import com.urbext.domain.model.defaultZoneHex
import com.urbext.domain.model.effectiveIcon
import com.urbext.domain.model.effectiveZoneColor
import com.urbext.domain.model.effectiveZonePattern
import com.urbext.domain.model.normalizeZoneColor
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
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
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val GRID_SOURCE_ID = "urbex-grid-source"
internal const val GRID_LINE_LAYER_ID = "urbex-grid-line"
internal const val CONTOURS_SOURCE_ID = "urbex-contours-source"
internal const val CONTOURS_LINE_LAYER_ID = "urbex-contours-line"
internal const val SECTORS_SOURCE_ID = "urbex-sectors-source"
internal const val SECTORS_LINE_LAYER_ID = "urbex-sectors-line"
internal const val GPS_ACCURACY_SOURCE_ID = "urbex-gps-accuracy-source"
internal const val GPS_ACCURACY_FILL_LAYER_ID = "urbex-gps-accuracy-fill"
internal const val GPS_ACCURACY_LINE_LAYER_ID = "urbex-gps-accuracy-line"
internal const val GPS_CENTER_SOURCE_ID = "urbex-gps-center-source"
internal const val GPS_CENTER_LAYER_ID = "urbex-gps-center-dot"
internal const val OBJECTS_SOURCE_ID = "urbex-objects-source"
internal const val OBJECTS_DOT_LAYER_ID = "urbex-objects-dots"
internal const val OBJECTS_BADGE_LAYER_ID = "urbex-objects-badges"
internal const val OBJECT_ZONES_SOURCE_ID = "urbex-object-zones-source"
internal const val OBJECT_ZONES_FILL_LAYER_ID = "urbex-object-zones-fill"
internal const val OBJECT_ZONES_HATCH_LAYER_ID = "urbex-object-zones-hatch"
internal const val OBJECT_ZONES_LINE_SOLID_LAYER_ID = "urbex-object-zones-line-solid"
internal const val OBJECT_ZONES_LINE_BOLD_LAYER_ID = "urbex-object-zones-line-bold"
internal const val OBJECT_ZONES_LINE_GLOW_HALO_LAYER_ID = "urbex-object-zones-line-glow-halo"
internal const val OBJECT_ZONES_LINE_GLOW_CORE_LAYER_ID = "urbex-object-zones-line-glow-core"
internal const val OBJECT_ZONES_LINE_DOUBLE_OUTER_LAYER_ID = "urbex-object-zones-line-double-outer"
internal const val OBJECT_ZONES_LINE_DOUBLE_INNER_LAYER_ID = "urbex-object-zones-line-double-inner"
internal const val OBJECT_ZONES_LINE_DASHED_LAYER_ID = "urbex-object-zones-line-dashed"
internal const val OBJECT_ZONES_LINE_DOTTED_LAYER_ID = "urbex-object-zones-line-dotted"
internal const val OBJECT_ZONES_LINE_DASH_DOT_LAYER_ID = "urbex-object-zones-line-dash-dot"
internal const val OBJECT_ZONES_LINE_LONG_DASH_LAYER_ID = "urbex-object-zones-line-long-dash"
internal const val ZONE_DRAFT_SOURCE_ID = "urbex-zone-draft-source"
internal const val ZONE_DRAFT_FILL_LAYER_ID = "urbex-zone-draft-fill"
internal const val ZONE_DRAFT_HATCH_LAYER_ID = "urbex-zone-draft-hatch"
internal const val ZONE_EMPTY_PATTERN_ID = "urbex-pat-empty"
internal const val ZONE_DRAFT_LINE_SOLID_LAYER_ID = "urbex-zone-draft-line-solid"
internal const val ZONE_DRAFT_LINE_BOLD_LAYER_ID = "urbex-zone-draft-line-bold"
internal const val ZONE_DRAFT_LINE_GLOW_HALO_LAYER_ID = "urbex-zone-draft-line-glow-halo"
internal const val ZONE_DRAFT_LINE_GLOW_CORE_LAYER_ID = "urbex-zone-draft-line-glow-core"
internal const val ZONE_DRAFT_LINE_DOUBLE_OUTER_LAYER_ID = "urbex-zone-draft-line-double-outer"
internal const val ZONE_DRAFT_LINE_DOUBLE_INNER_LAYER_ID = "urbex-zone-draft-line-double-inner"
internal const val ZONE_DRAFT_LINE_DASHED_LAYER_ID = "urbex-zone-draft-line-dashed"
internal const val ZONE_DRAFT_LINE_DOTTED_LAYER_ID = "urbex-zone-draft-line-dotted"
internal const val ZONE_DRAFT_LINE_DASH_DOT_LAYER_ID = "urbex-zone-draft-line-dash-dot"
internal const val ZONE_DRAFT_LINE_LONG_DASH_LAYER_ID = "urbex-zone-draft-line-long-dash"
internal const val ZONE_DRAFT_VERTEX_LAYER_ID = "urbex-zone-draft-vertices"
internal const val POINT_MOVE_SOURCE_ID = "urbex-point-move-source"
internal const val POINT_MOVE_ZONE_FILL_LAYER_ID = "urbex-point-move-zone-fill"
internal const val POINT_MOVE_ZONE_LINE_LAYER_ID = "urbex-point-move-zone-line"
internal const val POINT_MOVE_TETHER_LAYER_ID = "urbex-point-move-tether"
internal const val POINT_MOVE_ORIGIN_LAYER_ID = "urbex-point-move-origin"
internal const val POINT_MOVE_TARGET_HALO_LAYER_ID = "urbex-point-move-target-halo"
internal const val POINT_MOVE_TARGET_LAYER_ID = "urbex-point-move-target"
internal const val POINT_MOVE_TARGET_CENTER_LAYER_ID = "urbex-point-move-target-center"

internal class GridOverlayCache {
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

internal class ZonePatternImageCache {
    private val uploadedImageIds = HashSet<String>()
    private val prewarmedHexes = HashSet<String>()
    private val uploadedMarkerIds = HashSet<String>()
    var basePaletteWarmed: Boolean = false

    fun hasImage(id: String): Boolean = uploadedImageIds.contains(id)
    fun markImage(id: String) {
        uploadedImageIds.add(id)
    }

    fun hasHexPrewarmed(hex: String): Boolean = prewarmedHexes.contains(hex)
    fun markHexPrewarmed(hex: String) {
        prewarmedHexes.add(hex)
    }

    fun hasMarkerImage(id: String): Boolean = uploadedMarkerIds.contains(id)
    fun markMarkerImage(id: String) {
        uploadedMarkerIds.add(id)
    }
    fun markerImagesNotIn(activeIds: Set<String>): List<String> =
        uploadedMarkerIds.filter { it !in activeIds }
    fun removeMarkerImages(ids: Collection<String>) {
        uploadedMarkerIds.removeAll(ids.toSet())
    }

    fun reset() {
        uploadedImageIds.clear()
        prewarmedHexes.clear()
        uploadedMarkerIds.clear()
        basePaletteWarmed = false
    }
}

private class BooleanVisibilityState(var value: Boolean? = null)

@Immutable
private data class SavedZoneRenderSpec(
    val objectId: String,
    val zone: List<GeoPoint>,
    val zoneHex: String,
    val zoneStyle: ZoneStyle,
    val zonePattern: ZonePattern
)

@Immutable
private data class MarkerRenderSpec(
    val objectId: String,
    val lat: Double,
    val lon: Double,
    val title: String,
    val status: ReliabilityStatus,
    val effectiveIcon: ObjectIcon,
    val isSelected: Boolean
)

/**
 * Isolated memoized MapLibre layer controller for environment overlays (contours, grid, sectors).
 * Reads cursor/zoom state locally so camera pan/zoom never recomposes [MapScreen].
 */
@Composable
internal fun MapEnvironmentLayers(
    mapView: MapView,
    styleReadyVersion: Int,
    offlineSectors: List<OfflineMapSector>,
    showSectors: Boolean,
    showContours: Boolean,
    showMapGrid: Boolean,
    lowMemoryMode: Boolean,
    cursorLatState: State<Double>,
    cursorLonState: State<Double>,
    currentZoomState: State<Double>,
    gridCache: GridOverlayCache
) {
    LaunchedEffect(mapView, styleReadyVersion, showContours, lowMemoryMode) {
        if (styleReadyVersion <= 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateContoursOverlay(style, showContours && !lowMemoryMode)
            }
        }
    }

    val cursorLat = cursorLatState.value
    val cursorLon = cursorLonState.value
    val currentZoom = currentZoomState.value

    LaunchedEffect(mapView, styleReadyVersion, showMapGrid, lowMemoryMode, cursorLat, cursorLon, currentZoom) {
        if (styleReadyVersion <= 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateGridOverlay(style, cursorLat, cursorLon, currentZoom, showMapGrid && !lowMemoryMode, gridCache)
            }
        }
    }

    LaunchedEffect(mapView, styleReadyVersion, offlineSectors, showSectors) {
        if (styleReadyVersion <= 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateSectorsOverlay(style, offlineSectors, showSectors)
            }
        }
    }
}

/**
 * Isolated memoized MapLibre layer controller for persisted object zones.
 * Deliberately excludes object selection state so selecting an object never rebuilds zone GeoJSON.
 */
@Composable
internal fun MapSavedZonesLayer(
    mapView: MapView,
    styleReadyVersion: Int,
    objects: List<UrbexObject>,
    hiddenZoneObjectId: String?,
    visible: Boolean,
    patternCache: ZonePatternImageCache
) {
    val zoneSpecs = remember(objects, hiddenZoneObjectId) {
        objects.mapNotNull { obj ->
            if (obj.id == hiddenZoneObjectId || obj.zone.size < 3) return@mapNotNull null
            SavedZoneRenderSpec(
                objectId = obj.id,
                zone = obj.zone,
                zoneHex = obj.effectiveZoneColor(),
                zoneStyle = obj.zoneStyle,
                zonePattern = obj.effectiveZonePattern()
            )
        }
    }
    val visibilityTracker = remember(styleReadyVersion) { BooleanVisibilityState() }

    LaunchedEffect(mapView, styleReadyVersion, zoneSpecs, visible) {
        if (styleReadyVersion <= 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                if (!patternCache.basePaletteWarmed) {
                    ReliabilityStatus.entries.forEach { st ->
                        ensureAllZonePatternImages(style, st.defaultZoneHex(), patternCache)
                    }
                    ZONE_COLOR_PRESETS.forEach { (presetHex, _) ->
                        ensureAllZonePatternImages(style, presetHex, patternCache)
                    }
                    patternCache.basePaletteWarmed = true
                }
                updateObjectZonesOverlay(
                    style = style,
                    zoneSpecs = zoneSpecs,
                    visible = visible,
                    patternCache = patternCache,
                    visibilityTracker = visibilityTracker
                )
            }
        }
    }
}

/**
 * Isolated memoized MapLibre layer controller for the GPS accuracy halo and live fix dot.
 */
@Composable
internal fun MapGpsLayer(
    mapView: MapView,
    styleReadyVersion: Int,
    fix: GpsFix
) {
    LaunchedEffect(mapView, styleReadyVersion, fix) {
        if (styleReadyVersion <= 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateGpsAccuracyOverlay(style, fix.lat, fix.lon, fix.accuracyM)
            }
        }
    }
}

/**
 * Isolated memoized MapLibre layer controller for the active zone editor draft.
 * Uses data-driven GeoJSON properties + style expressions so updates only swap GeoJSON once with zero layer property churn.
 */
@Composable
internal fun MapDraftZoneLayer(
    mapView: MapView,
    styleReadyVersion: Int,
    draftZone: List<GeoPoint>,
    zoneColorHex: String,
    zoneStyle: ZoneStyle,
    zonePattern: ZonePattern,
    patternCache: ZonePatternImageCache
) {
    val activeHex = remember(zoneColorHex) { normalizeZoneColor(zoneColorHex) ?: "#5B9D72" }

    LaunchedEffect(mapView, styleReadyVersion, draftZone, activeHex, zoneStyle, zonePattern) {
        if (styleReadyVersion <= 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateDraftZoneOverlay(
                    style = style,
                    draftZone = draftZone,
                    activeHex = activeHex,
                    zoneStyle = zoneStyle,
                    zonePattern = zonePattern,
                    patternCache = patternCache
                )
            }
        }
    }
}

/**
 * Isolated memoized MapLibre layer controller for the point-move preview tether & shifted zone.
 */
@Composable
internal fun MapPointMoveLayer(
    mapView: MapView,
    styleReadyVersion: Int,
    request: MapPointMoveRequest?,
    draftPoint: GeoPoint?,
    shiftZoneWithPoint: Boolean
) {
    LaunchedEffect(mapView, styleReadyVersion, request, draftPoint, shiftZoneWithPoint) {
        if (styleReadyVersion <= 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updatePointMoveOverlay(style, request, draftPoint, shiftZoneWithPoint)
            }
        }
    }
}

/**
 * Isolated memoized MapLibre layer controller for object markers and zoom-dependent badges.
 */
@Composable
internal fun MapObjectMarkersLayer(
    context: Context,
    mapView: MapView,
    styleReadyVersion: Int,
    objects: List<UrbexObject>,
    selectedObjectId: String?,
    hiddenObjectId: String?,
    showLabels: Boolean,
    patternCache: ZonePatternImageCache
) {
    val markerSpecs = remember(objects, selectedObjectId, hiddenObjectId) {
        objects.mapNotNull { obj ->
            if (obj.id == hiddenObjectId) return@mapNotNull null
            MarkerRenderSpec(
                objectId = obj.id,
                lat = obj.lat,
                lon = obj.lon,
                title = obj.title,
                status = obj.status,
                effectiveIcon = obj.effectiveIcon(),
                isSelected = obj.id == selectedObjectId
            )
        }
    }
    val labelVisibilityTracker = remember(styleReadyVersion) { BooleanVisibilityState() }

    LaunchedEffect(mapView, styleReadyVersion, markerSpecs, showLabels) {
        if (styleReadyVersion <= 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.let { style ->
                updateObjectMarkersOverlay(
                    context = context,
                    style = style,
                    markerSpecs = markerSpecs,
                    showLabels = showLabels,
                    patternCache = patternCache,
                    labelVisibilityTracker = labelVisibilityTracker
                )
            }
        }
    }
}

/**
 * Isolated draggable target pin overlay for Point-Move mode so cameraTick only recomposes the pin.
 */
@Composable
internal fun MapPointMovePinOverlay(
    activeMoveReq: MapPointMoveRequest,
    draftPoint: GeoPoint,
    cameraTickState: State<Int>,
    mapInstance: MapLibreMap?,
    onMoveDraftPoint: (GeoPoint) -> Unit
) {
    val cameraTick = cameraTickState.value
    val screenPt = remember(draftPoint, cameraTick, mapInstance) {
        mapInstance?.projection?.toScreenLocation(LatLng(draftPoint.lat, draftPoint.lon))
    }
    if (screenPt != null && screenPt.x.isFinite() && screenPt.y.isFinite()) {
        val activeIcon = activeMoveReq.icon ?: activeMoveReq.category.defaultIcon()
        val statusColor = activeMoveReq.status.color()
        val density = LocalDensity.current
        val pinHalfPx = with(density) { 24.dp.toPx() }
        val labelGapPx = with(density) { 4.dp.toPx() }
        val latestDraftPoint by rememberUpdatedState(draftPoint)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (screenPt.x - pinHalfPx).roundToInt(),
                        y = (screenPt.y - pinHalfPx).roundToInt()
                    )
                }
                .size(48.dp)
                .pointerInput(activeMoveReq.token, mapInstance) {
                    var dragX = 0f
                    var dragY = 0f
                    detectDragGestures(
                        onDragStart = {
                            val m = mapInstance ?: return@detectDragGestures
                            val currentScreen = m.projection.toScreenLocation(
                                LatLng(latestDraftPoint.lat, latestDraftPoint.lon)
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
                            onMoveDraftPoint(next)
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

/**
 * Isolated top-left telemetry HUD pill & mini-compass so 60Hz camera moves/zooms never recompose [MapScreen].
 */
@Composable
internal fun BoxScope.MapTopTelemetryHud(
    visible: Boolean,
    isLandscape: Boolean,
    isActive: Boolean,
    settings: AppSettings,
    cursorLatState: State<Double>,
    cursorLonState: State<Double>,
    currentZoomState: State<Double>,
    cameraBearingState: State<Int>,
    deviceAzimuthState: State<Float?>,
    currentFix: GpsFix,
    targetSignalColor: Color,
    signalLabel: String,
    gpsStatusNote: String?,
    nearbyHazard: Pair<UrbexObject, Double>?,
    onResetNorth: () -> Unit,
    onSelectHazard: (String) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
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
        val cursorLat = cursorLatState.value
        val cursorLon = cursorLonState.value
        val currentZoom = currentZoomState.value
        val cameraBearing = cameraBearingState.value
        val metersPerPixel = remember(currentZoom, cursorLat) {
            val cosLat = cos(Math.toRadians(cursorLat)).coerceAtLeast(0.2)
            (156543.03392 * cosLat) / 2.0.pow(currentZoom.coerceIn(2.0, 20.0))
        }
        val scaleBarMeters = (metersPerPixel * 96.0).roundToInt().coerceAtLeast(5)
        val accText = currentFix.accuracyM?.let { "±${it}м" } ?: signalLabel

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
                        text = GeoUtils.formatDisplayCoordinates(
                            cursorLat,
                            cursorLon,
                            settings.coordinateFormat,
                            settings.maskCoordinatesOnScreen
                        ),
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
                        text = gpsStatusNote,
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
                    onResetNorth = onResetNorth
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
                            .urbexBounceClick(0.96f) { onSelectHazard(hazardObj.id) }
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
    spec: MarkerRenderSpec
): Bitmap {
    val density = context.resources.displayMetrics.density
    val shortTitle = if (spec.title.length > 18) spec.title.take(17) + "…" else spec.title
    val isSelected = spec.isSelected

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
    val rect = android.graphics.RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
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
        icon = spec.effectiveIcon,
        cx = padH + iconSize * 0.5f,
        cy = h * 0.5f,
        sizePx = iconSize,
        colorInt = spec.status.androidColorInt()
    )

    val baseline = (h / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(shortTitle, padH + iconSize + iconGap, baseline, textPaint)
    return bitmap
}

private fun createObjectDotBitmap(
    context: Context,
    spec: MarkerRenderSpec
): Bitmap {
    val density = context.resources.displayMetrics.density
    val isSelected = spec.isSelected
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
        color = spec.status.androidColorInt()
    }
    canvas.drawCircle(cx, cy, r, ringPaint)

    drawObjectIconOnCanvas(
        canvas = canvas,
        icon = spec.effectiveIcon,
        cx = cx,
        cy = cy,
        sizePx = sizePx * 0.50f,
        colorInt = spec.status.androidColorInt()
    )
    return bitmap
}

private fun ensureEmptyZonePatternImage(
    mapStyle: Style,
    cache: ZonePatternImageCache
): String {
    if (!cache.hasImage(ZONE_EMPTY_PATTERN_ID)) {
        if (mapStyle.getImage(ZONE_EMPTY_PATTERN_ID) == null) {
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            mapStyle.addImage(ZONE_EMPTY_PATTERN_ID, bitmap)
        }
        cache.markImage(ZONE_EMPTY_PATTERN_ID)
    }
    return ZONE_EMPTY_PATTERN_ID
}

private fun ensureAllZonePatternImages(
    mapStyle: Style,
    hex: String,
    cache: ZonePatternImageCache
) {
    val normalized = normalizeZoneColor(hex) ?: "#5B9D72"
    if (cache.hasHexPrewarmed(normalized)) return
    ZonePattern.entries.forEach { pattern ->
        if (pattern != ZonePattern.NONE) {
            ensureZonePatternImage(mapStyle, normalized, pattern, cache)
        }
    }
    cache.markHexPrewarmed(normalized)
}

private fun ensureZonePatternImage(
    mapStyle: Style,
    hex: String,
    pattern: ZonePattern,
    cache: ZonePatternImageCache
): String {
    if (pattern == ZonePattern.NONE) return ""
    val normalized = normalizeZoneColor(hex) ?: "#5B9D72"
    val imageId = "urbex-pat-${pattern.name}-${normalized.removePrefix("#")}"
    if (cache.hasImage(imageId)) return imageId
    if (mapStyle.getImage(imageId) == null) {
        // Power-of-two 32x32 texture ensures GL_REPEAT fillPattern renders cleanly across all Android GPUs
        val size = 32
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseColor = runCatching { android.graphics.Color.parseColor(normalized) }
            .getOrDefault(android.graphics.Color.rgb(91, 157, 114))
        val drawColor = android.graphics.Color.argb(
            210,
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
                strokePaint.strokeWidth = 3.2f
                for (offset in -size..(size * 2) step 8) {
                    canvas.drawLine(offset.toFloat(), size.toFloat(), (offset + size).toFloat(), 0f, strokePaint)
                }
            }
            ZonePattern.CROSS_HATCH -> {
                strokePaint.strokeWidth = 2.6f
                for (offset in -size..(size * 2) step 8) {
                    canvas.drawLine(offset.toFloat(), size.toFloat(), (offset + size).toFloat(), 0f, strokePaint)
                    canvas.drawLine(offset.toFloat(), 0f, (offset + size).toFloat(), size.toFloat(), strokePaint)
                }
            }
            ZonePattern.GRID -> {
                strokePaint.strokeWidth = 2.2f
                canvas.drawLine(8f, 0f, 8f, size.toFloat(), strokePaint)
                canvas.drawLine(24f, 0f, 24f, size.toFloat(), strokePaint)
                canvas.drawLine(0f, 8f, size.toFloat(), 8f, strokePaint)
                canvas.drawLine(0f, 24f, size.toFloat(), 24f, strokePaint)
            }
            ZonePattern.DOTS -> {
                canvas.drawCircle(8f, 8f, 3.0f, fillPaint)
                canvas.drawCircle(24f, 8f, 3.0f, fillPaint)
                canvas.drawCircle(16f, 24f, 3.0f, fillPaint)
            }
            ZonePattern.VERTICAL -> {
                strokePaint.strokeWidth = 3.2f
                canvas.drawLine(8f, 0f, 8f, size.toFloat(), strokePaint)
                canvas.drawLine(24f, 0f, 24f, size.toFloat(), strokePaint)
            }
            ZonePattern.HORIZONTAL -> {
                strokePaint.strokeWidth = 3.2f
                canvas.drawLine(0f, 8f, size.toFloat(), 8f, strokePaint)
                canvas.drawLine(0f, 24f, size.toFloat(), 24f, strokePaint)
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
    cache.markImage(imageId)
    return imageId
}

private fun updateObjectZonesOverlay(
    style: Style,
    zoneSpecs: List<SavedZoneRenderSpec>,
    visible: Boolean,
    patternCache: ZonePatternImageCache,
    visibilityTracker: BooleanVisibilityState
) {
    if (!style.isFullyLoaded) return
    val emptyPatternId = ensureEmptyZonePatternImage(style, patternCache)
    val visValue = if (visible) Property.VISIBLE else Property.NONE
    val features = zoneSpecs.map { spec ->
        val patternId = ensureZonePatternImage(style, spec.zoneHex, spec.zonePattern, patternCache)
            .ifEmpty { emptyPatternId }
        val ring = (spec.zone + spec.zone.first()).map { Point.fromLngLat(it.lon, it.lat) }
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
            addStringProperty("objectId", spec.objectId)
            addStringProperty("zoneHex", spec.zoneHex)
            addStringProperty("zoneStyle", spec.zoneStyle.name)
            addStringProperty("hasPattern", if (spec.zonePattern != ZonePattern.NONE) "1" else "0")
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
        visibilityTracker.value = visible
    } else {
        source.setGeoJson(collection)
        if (visibilityTracker.value != visible) {
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
            visibilityTracker.value = visible
        }
    }
}

private fun draftLineFilter(vararg styles: ZoneStyle): Expression {
    val kindExpr = Expression.eq(Expression.get("kind"), Expression.literal("line"))
    val styleExpr = if (styles.size == 1) {
        Expression.eq(Expression.get("zoneStyle"), Expression.literal(styles[0].name))
    } else {
        Expression.any(
            *styles.map { st ->
                Expression.eq(Expression.get("zoneStyle"), Expression.literal(st.name))
            }.toTypedArray()
        )
    }
    return Expression.all(kindExpr, styleExpr)
}

private fun updateDraftZoneOverlay(
    style: Style,
    draftZone: List<GeoPoint>,
    activeHex: String,
    zoneStyle: ZoneStyle,
    zonePattern: ZonePattern,
    patternCache: ZonePatternImageCache
) {
    if (!style.isFullyLoaded) return
    val emptyPatternId = ensureEmptyZonePatternImage(style, patternCache)
    ensureAllZonePatternImages(style, activeHex, patternCache)
    val patternImageId = ensureZonePatternImage(style, activeHex, zonePattern, patternCache)
    val hasPattern = zonePattern != ZonePattern.NONE && patternImageId.isNotEmpty()
    val safePatternId = patternImageId.ifEmpty { emptyPatternId }
    val features = mutableListOf<Feature>()
    if (draftZone.size >= 3) {
        val ring = (draftZone + draftZone.first()).map { Point.fromLngLat(it.lon, it.lat) }
        features.add(
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
                addStringProperty("kind", "poly")
                addStringProperty("zoneHex", activeHex)
                addStringProperty("zoneStyle", zoneStyle.name)
                addStringProperty("hasPattern", if (hasPattern) "1" else "0")
                addStringProperty("patternId", safePatternId)
            }
        )
        features.add(
            Feature.fromGeometry(LineString.fromLngLats(ring)).apply {
                addStringProperty("kind", "line")
                addStringProperty("zoneHex", activeHex)
                addStringProperty("zoneStyle", zoneStyle.name)
            }
        )
    } else if (draftZone.size == 2) {
        val pts = draftZone.map { Point.fromLngLat(it.lon, it.lat) }
        features.add(
            Feature.fromGeometry(LineString.fromLngLats(pts)).apply {
                addStringProperty("kind", "line")
                addStringProperty("zoneHex", activeHex)
                addStringProperty("zoneStyle", zoneStyle.name)
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

    val collection = FeatureCollection.fromFeatures(features)
    val source = style.getSourceAs<GeoJsonSource>(ZONE_DRAFT_SOURCE_ID)
    if (source == null) {
        style.addSource(GeoJsonSource(ZONE_DRAFT_SOURCE_ID, collection))
        style.addLayer(
            FillLayer(ZONE_DRAFT_FILL_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                fillColor(Expression.get("zoneHex")),
                fillOpacity(0.24f),
                visibility(Property.VISIBLE)
            ).withFilter(
                Expression.all(
                    Expression.eq(Expression.get("kind"), Expression.literal("poly")),
                    Expression.neq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.OUTLINE.name)),
                    Expression.neq(Expression.get("zoneStyle"), Expression.literal(ZoneStyle.DASHED_OUTLINE.name))
                )
            )
        )
        style.addLayer(
            FillLayer(ZONE_DRAFT_HATCH_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                fillPattern(Expression.get("patternId")),
                fillOpacity(0.62f),
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
                lineColor(Expression.get("zoneHex")),
                lineWidth(2.6f),
                visibility(Property.VISIBLE)
            ).withFilter(draftLineFilter(ZoneStyle.SOLID, ZoneStyle.OUTLINE))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_BOLD_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(Expression.get("zoneHex")),
                lineWidth(4.4f),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(Property.VISIBLE)
            ).withFilter(draftLineFilter(ZoneStyle.BOLD))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_GLOW_HALO_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(Expression.get("zoneHex")),
                lineWidth(8.0f),
                lineOpacity(0.32f),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(Property.VISIBLE)
            ).withFilter(draftLineFilter(ZoneStyle.GLOW))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_GLOW_CORE_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(Expression.get("zoneHex")),
                lineWidth(2.8f),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(Property.VISIBLE)
            ).withFilter(draftLineFilter(ZoneStyle.GLOW))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_DOUBLE_OUTER_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(Expression.get("zoneHex")),
                lineWidth(5.2f),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(Property.VISIBLE)
            ).withFilter(draftLineFilter(ZoneStyle.DOUBLE_LINE))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_DOUBLE_INNER_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor("#0C0F0E"),
                lineWidth(2.0f),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(Property.VISIBLE)
            ).withFilter(draftLineFilter(ZoneStyle.DOUBLE_LINE))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_DASHED_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(Expression.get("zoneHex")),
                lineWidth(2.6f),
                lineDasharray(arrayOf(4f, 3f)),
                visibility(Property.VISIBLE)
            ).withFilter(draftLineFilter(ZoneStyle.DASHED, ZoneStyle.DASHED_OUTLINE))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_DOTTED_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(Expression.get("zoneHex")),
                lineWidth(2.6f),
                lineCap(Property.LINE_CAP_ROUND),
                lineDasharray(arrayOf(1.2f, 2.4f)),
                visibility(Property.VISIBLE)
            ).withFilter(draftLineFilter(ZoneStyle.DOTTED))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_DASH_DOT_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(Expression.get("zoneHex")),
                lineWidth(2.6f),
                lineCap(Property.LINE_CAP_ROUND),
                lineDasharray(arrayOf(5.5f, 2.2f, 1.2f, 2.2f)),
                visibility(Property.VISIBLE)
            ).withFilter(draftLineFilter(ZoneStyle.DASH_DOT))
        )
        style.addLayer(
            LineLayer(ZONE_DRAFT_LINE_LONG_DASH_LAYER_ID, ZONE_DRAFT_SOURCE_ID).withProperties(
                lineColor(Expression.get("zoneHex")),
                lineWidth(2.6f),
                lineDasharray(arrayOf(8.5f, 3.2f)),
                visibility(Property.VISIBLE)
            ).withFilter(draftLineFilter(ZoneStyle.LONG_DASH))
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
        // Atomic GeoJSON swap only: all fill, hatch, border styles, and colors are driven by feature properties.
        source.setGeoJson(collection)
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
    markerSpecs: List<MarkerRenderSpec>,
    showLabels: Boolean,
    patternCache: ZonePatternImageCache,
    labelVisibilityTracker: BooleanVisibilityState
) {
    if (!style.isFullyLoaded) return
    val badgeVis = if (showLabels) Property.VISIBLE else Property.NONE
    val dotVis = if (!showLabels) Property.VISIBLE else Property.NONE
    val activeMarkerIds = HashSet<String>(markerSpecs.size * 2)
    val features = markerSpecs.map { spec ->
        val selFlag = if (spec.isSelected) 1 else 0
        val iconId = "urbex-pin-${spec.objectId}-${spec.effectiveIcon.name}-${spec.status.name}-$selFlag-${spec.title.hashCode()}"
        val dotIconId = "urbex-dot-${spec.objectId}-${spec.effectiveIcon.name}-${spec.status.name}-$selFlag"
        activeMarkerIds.add(iconId)
        activeMarkerIds.add(dotIconId)
        if (!patternCache.hasMarkerImage(iconId)) {
            if (style.getImage(iconId) == null) {
                style.addImage(iconId, createObjectBadgeBitmap(context, spec))
            }
            patternCache.markMarkerImage(iconId)
        }
        if (!patternCache.hasMarkerImage(dotIconId)) {
            if (style.getImage(dotIconId) == null) {
                style.addImage(dotIconId, createObjectDotBitmap(context, spec))
            }
            patternCache.markMarkerImage(dotIconId)
        }

        Feature.fromGeometry(Point.fromLngLat(spec.lon, spec.lat)).apply {
            addStringProperty("objectId", spec.objectId)
            addStringProperty("iconId", iconId)
            addStringProperty("dotIconId", dotIconId)
            addStringProperty("statusHex", spec.status.hexColor())
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
        labelVisibilityTracker.value = showLabels
    } else {
        source.setGeoJson(collection)
        if (labelVisibilityTracker.value != showLabels) {
            style.getLayerAs<CircleLayer>("urbex-objects-anchor-dots")?.setProperties(visibility(badgeVis))
            style.getLayerAs<SymbolLayer>(OBJECTS_DOT_LAYER_ID)?.setProperties(visibility(dotVis))
            style.getLayerAs<SymbolLayer>(OBJECTS_BADGE_LAYER_ID)?.setProperties(visibility(badgeVis))
            labelVisibilityTracker.value = showLabels
        }
    }
    val staleMarkerIds = patternCache.markerImagesNotIn(activeMarkerIds)
    if (staleMarkerIds.isNotEmpty()) {
        staleMarkerIds.forEach { staleId -> style.removeImage(staleId) }
        patternCache.removeMarkerImages(staleMarkerIds)
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

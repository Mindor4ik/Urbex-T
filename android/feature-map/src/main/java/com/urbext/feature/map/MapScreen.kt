package com.urbext.feature.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.geo.GeoUtils
import com.urbext.core.location.AdaptiveLocationClient
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.color
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.MapLayerMode
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.PowerProfile
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.ProfileRepository
import com.urbext.domain.repository.SettingsRepository
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

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

@Composable
fun MapScreen(
    objectRepository: ObjectRepository,
    settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository,
    onOpenObject: (String) -> Unit
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
            role = com.urbext.domain.model.OperatorRole.SCOUT,
            emergencyContact = "",
            bio = "",
            updatedAt = 0L
        )
    )

    val locationClient = remember(context) { AdaptiveLocationClient(context) }
    var currentFix by remember { mutableStateOf(locationClient.getLastKnownFixOrDefault()) }
    var selectedObject by remember { mutableStateOf<UrbexObject?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

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
    }

    LaunchedEffect(hasLocationPermission, settings.powerProfile) {
        if (hasLocationPermission && settings.powerProfile != PowerProfile.MANUAL) {
            locationClient.observeLocationUpdates(settings.powerProfile).collect { fix ->
                currentFix = fix
            }
        }
    }

    val styleJson = if (settings.mapLayer == MapLayerMode.SATELLITE) SATELLITE_STYLE_JSON else OFFLINE_DARK_STYLE_JSON

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            getMapAsync { map ->
                map.setStyle(Style.Builder().fromJson(styleJson))
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(currentFix.lat, currentFix.lon))
                    .zoom(14.0)
                    .build()
            }
        }
    }

    LaunchedEffect(styleJson) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(styleJson))
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

    // Full-area satellite MapLibre viewport with floating HUD controls
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UrbexColors.Background)
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Top-Left Telemetry Pill
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(UrbexColors.Surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = GeoUtils.formatCoordinates(currentFix.lat, currentFix.lon, settings.coordinateFormat),
                color = UrbexColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${settings.mapLayer.label} · ${settings.powerProfile.label}",
                color = UrbexColors.Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }

        // Floating Action Controls (Top Right)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
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
                },
                modifier = Modifier
                    .background(UrbexColors.Surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Переключить слой карты",
                    tint = UrbexColors.TextPrimary
                )
            }

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
                        currentFix = locationClient.getLastKnownFixOrDefault(currentFix.lat, currentFix.lon)
                        mapView.getMapAsync { map ->
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(currentFix.lat, currentFix.lon))
                                .zoom(15.0)
                                .build()
                        }
                    }
                },
                modifier = Modifier
                    .background(UrbexColors.Surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Центрировать по GPS",
                    tint = UrbexColors.GreenBright
                )
            }

            IconButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .background(UrbexColors.Green, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.AddLocationAlt,
                    contentDescription = "Отметить точку",
                    tint = UrbexColors.Background
                )
            }
        }

        // Bottom Floating Object Chips + Animated Selected Card
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = selectedObject != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                selectedObject?.let { activeObj ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .background(UrbexColors.Surface.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${activeObj.status.glyph} ${activeObj.status.label}",
                                color = activeObj.status.color(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(onClick = { selectedObject = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Скрыть", tint = UrbexColors.TextSecondary)
                            }
                        }
                        Text(
                            text = activeObj.title,
                            color = UrbexColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = activeObj.description,
                            color = UrbexColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = { onOpenObject(activeObj.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Открыть на всю площадь", color = UrbexColors.Background, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(objects, key = { it.id }) { obj ->
                    val isSelected = selectedObject?.id == obj.id
                    val dist = GeoUtils.haversineDistanceMeters(currentFix.lat, currentFix.lon, obj.lat, obj.lon)
                    Column(
                        modifier = Modifier
                            .background(
                                (if (isSelected) UrbexColors.Elevated else UrbexColors.Surface).copy(alpha = 0.92f),
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) UrbexColors.Green else UrbexColors.Border,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                selectedObject = obj
                                mapView.getMapAsync { map ->
                                    map.cameraPosition = CameraPosition.Builder()
                                        .target(LatLng(obj.lat, obj.lon))
                                        .zoom(15.0)
                                        .build()
                                }
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

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = UrbexColors.Surface,
            title = { Text("Новая точка объекта", color = UrbexColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = GeoUtils.formatCoordinates(currentFix.lat, currentFix.lon, settings.coordinateFormat),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = UrbexColors.Amber
                    )
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = UrbexColors.Danger, fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Название") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Описание") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                    onClick = {
                        if (title.trim().length < 2 || desc.trim().length < 3) {
                            errorMsg = "Заполните название и описание."
                            return@Button
                        }
                        val now = System.currentTimeMillis()
                        val newObj = UrbexObject(
                            id = "obj-${now.toString(36)}",
                            title = title.trim(),
                            category = ObjectCategory.INDUSTRIAL,
                            status = ReliabilityStatus.UNVERIFIED,
                            lat = currentFix.lat,
                            lon = currentFix.lon,
                            elevationM = currentFix.altitudeM ?: 140,
                            description = desc.trim(),
                            accessNotes = "Отмечено в полевом режиме.",
                            hazards = emptyList(),
                            createdAt = now,
                            updatedAt = now,
                            authorNodeId = profile.nodeId
                        )
                        scope.launch {
                            objectRepository.upsertObject(newObj)
                            selectedObject = newObj
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Сохранить", color = UrbexColors.Background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Отмена", color = UrbexColors.TextSecondary)
                }
            }
        )
    }
}

package com.urbext.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.urbext.core.radio.RadioPlanUtils
import com.urbext.data.seed.SeedData
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.CloudSyncConfig
import com.urbext.domain.model.CoordinateFormat
import com.urbext.domain.model.MapLayerMode
import com.urbext.domain.model.ObjectsViewMode
import com.urbext.domain.model.OfflineMapSector
import com.urbext.domain.model.PdaOrientation
import com.urbext.domain.model.PowerProfile
import com.urbext.domain.model.RadioChannelPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.urbexDataStore: DataStore<Preferences> by preferencesDataStore(name = "urbex_settings")

class SettingsDataStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val POWER_PROFILE = stringPreferencesKey("power_profile")
        val GPS_INTERVAL_MS = longPreferencesKey("gps_interval_ms")
        val COORD_FORMAT = stringPreferencesKey("coord_format")
        val IMAGE_CACHE_MB = intPreferencesKey("image_cache_mb")
        val PAGE_SIZE = intPreferencesKey("page_size")
        val MESH_ENABLED = booleanPreferencesKey("mesh_enabled")
        val RADIO_CHANNELS_TAB_ENABLED = booleanPreferencesKey("radio_channels_tab_enabled")
        val MAP_LAYER = stringPreferencesKey("map_layer")
        val LOW_MEM_MODE = booleanPreferencesKey("low_mem_mode")
        val SHOW_OBJECT_ZONES = booleanPreferencesKey("show_object_zones")
        val SHOW_OBJECT_LABELS = booleanPreferencesKey("show_object_labels")
        val SHOW_MAP_GRID = booleanPreferencesKey("show_map_grid")
        val SHOW_COMPASS_HUD = booleanPreferencesKey("show_compass_hud")
        val DEFAULT_OBJECTS_VIEW = stringPreferencesKey("default_objects_view")
        val AUTO_CENTER_GPS = booleanPreferencesKey("auto_center_gps")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val DEFAULT_ZONE_RADIUS_M = intPreferencesKey("default_zone_radius_m")
        val HAZARD_PROXIMITY_ALERT_M = intPreferencesKey("hazard_proximity_alert_m")
        val CONFIRM_BEFORE_DELETE = booleanPreferencesKey("confirm_before_delete")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val MASK_COORDS_ON_SCREEN = booleanPreferencesKey("mask_coords_on_screen")
        val EDITOR_ENABLED = booleanPreferencesKey("editor_enabled")
        val PDA_MODE_ENABLED = booleanPreferencesKey("pda_mode_enabled")
        val PDA_ORIENTATION = stringPreferencesKey("pda_orientation")
        val RADIO_CHANNELS_JSON = stringPreferencesKey("radio_channels_json")
        val CLOUD_SYNC_JSON = stringPreferencesKey("cloud_sync_json")
        val SECTORS_JSON = stringPreferencesKey("offline_sectors_json")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    fun observeSettings(): Flow<AppSettings> = context.urbexDataStore.data.map { prefs ->
        val defaultSectors = SeedData.defaultOfflineSectors()
        val sectors = prefs[Keys.SECTORS_JSON]?.let { raw ->
            runCatching { json.decodeFromString<List<OfflineMapSector>>(raw) }.getOrNull()
        } ?: defaultSectors

        val radioChannels = prefs[Keys.RADIO_CHANNELS_JSON]?.let { raw ->
            runCatching { json.decodeFromString<List<RadioChannelPreset>>(raw) }.getOrNull()
        } ?: RadioPlanUtils.createDefaultRadioChannels()

        val cloudSync = prefs[Keys.CLOUD_SYNC_JSON]?.let { raw ->
            runCatching { json.decodeFromString<CloudSyncConfig>(raw) }.getOrNull()
        } ?: CloudSyncConfig()

        AppSettings(
            powerProfile = prefs[Keys.POWER_PROFILE]?.let {
                runCatching { PowerProfile.valueOf(it) }.getOrNull()
            } ?: PowerProfile.BALANCED,
            gpsIntervalMs = prefs[Keys.GPS_INTERVAL_MS] ?: 10_000L,
            coordinateFormat = prefs[Keys.COORD_FORMAT]?.let {
                runCatching { CoordinateFormat.valueOf(it) }.getOrNull()
            } ?: CoordinateFormat.DD,
            imageCacheLimitMb = prefs[Keys.IMAGE_CACHE_MB] ?: 64,
            pageSize = prefs[Keys.PAGE_SIZE] ?: 12,
            meshEnabled = prefs[Keys.MESH_ENABLED] ?: false,
            radioChannelsTabEnabled = prefs[Keys.RADIO_CHANNELS_TAB_ENABLED] ?: true,
            mapLayer = prefs[Keys.MAP_LAYER]?.let {
                runCatching { MapLayerMode.valueOf(it) }.getOrNull()
            } ?: MapLayerMode.SATELLITE,
            lowMemoryMode = prefs[Keys.LOW_MEM_MODE] ?: false,
            showObjectZones = prefs[Keys.SHOW_OBJECT_ZONES] ?: true,
            showObjectLabels = prefs[Keys.SHOW_OBJECT_LABELS] ?: true,
            showMapGrid = prefs[Keys.SHOW_MAP_GRID] ?: true,
            showCompassHud = prefs[Keys.SHOW_COMPASS_HUD] ?: true,
            defaultObjectsView = prefs[Keys.DEFAULT_OBJECTS_VIEW]?.let {
                runCatching { ObjectsViewMode.valueOf(it) }.getOrNull()
            } ?: ObjectsViewMode.CARDS,
            autoCenterGps = prefs[Keys.AUTO_CENTER_GPS] ?: false,
            keepScreenAwake = prefs[Keys.KEEP_SCREEN_AWAKE] ?: false,
            defaultZoneRadiusM = prefs[Keys.DEFAULT_ZONE_RADIUS_M] ?: 80,
            hazardProximityAlertM = prefs[Keys.HAZARD_PROXIMITY_ALERT_M] ?: 150,
            confirmBeforeDelete = prefs[Keys.CONFIRM_BEFORE_DELETE] ?: true,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            maskCoordinatesOnScreen = prefs[Keys.MASK_COORDS_ON_SCREEN] ?: false,
            editorEnabled = prefs[Keys.EDITOR_ENABLED] ?: false,
            pdaModeEnabled = prefs[Keys.PDA_MODE_ENABLED] ?: false,
            pdaOrientation = prefs[Keys.PDA_ORIENTATION]?.let {
                runCatching { PdaOrientation.valueOf(it) }.getOrNull()
            } ?: PdaOrientation.PORTRAIT,
            radioChannels = radioChannels,
            cloudSync = cloudSync,
            offlineSectors = sectors,
            updatedAt = prefs[Keys.UPDATED_AT] ?: 0L
        )
    }

    suspend fun getOnce(): AppSettings = observeSettings().first()

    suspend fun saveSettings(settings: AppSettings) {
        context.urbexDataStore.edit { prefs ->
            prefs[Keys.POWER_PROFILE] = settings.powerProfile.name
            prefs[Keys.GPS_INTERVAL_MS] = settings.gpsIntervalMs
            prefs[Keys.COORD_FORMAT] = settings.coordinateFormat.name
            prefs[Keys.IMAGE_CACHE_MB] = settings.imageCacheLimitMb
            prefs[Keys.PAGE_SIZE] = settings.pageSize
            prefs[Keys.MESH_ENABLED] = settings.meshEnabled
            prefs[Keys.RADIO_CHANNELS_TAB_ENABLED] = settings.radioChannelsTabEnabled
            prefs[Keys.MAP_LAYER] = settings.mapLayer.name
            prefs[Keys.LOW_MEM_MODE] = settings.lowMemoryMode
            prefs[Keys.SHOW_OBJECT_ZONES] = settings.showObjectZones
            prefs[Keys.SHOW_OBJECT_LABELS] = settings.showObjectLabels
            prefs[Keys.SHOW_MAP_GRID] = settings.showMapGrid
            prefs[Keys.SHOW_COMPASS_HUD] = settings.showCompassHud
            prefs[Keys.DEFAULT_OBJECTS_VIEW] = settings.defaultObjectsView.name
            prefs[Keys.AUTO_CENTER_GPS] = settings.autoCenterGps
            prefs[Keys.KEEP_SCREEN_AWAKE] = settings.keepScreenAwake
            prefs[Keys.DEFAULT_ZONE_RADIUS_M] = settings.defaultZoneRadiusM
            prefs[Keys.HAZARD_PROXIMITY_ALERT_M] = settings.hazardProximityAlertM
            prefs[Keys.CONFIRM_BEFORE_DELETE] = settings.confirmBeforeDelete
            prefs[Keys.HAPTIC_FEEDBACK] = settings.hapticFeedback
            prefs[Keys.MASK_COORDS_ON_SCREEN] = settings.maskCoordinatesOnScreen
            prefs[Keys.EDITOR_ENABLED] = settings.editorEnabled
            prefs[Keys.PDA_MODE_ENABLED] = settings.pdaModeEnabled
            prefs[Keys.PDA_ORIENTATION] = settings.pdaOrientation.name
            prefs[Keys.RADIO_CHANNELS_JSON] = json.encodeToString(settings.radioChannels)
            prefs[Keys.CLOUD_SYNC_JSON] = json.encodeToString(settings.cloudSync)
            prefs[Keys.SECTORS_JSON] = json.encodeToString(settings.offlineSectors)
            prefs[Keys.UPDATED_AT] = settings.updatedAt
        }
    }
}

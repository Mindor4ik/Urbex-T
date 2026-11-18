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
import com.urbext.data.seed.SeedData
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.CoordinateFormat
import com.urbext.domain.model.MapLayerMode
import com.urbext.domain.model.ObjectsViewMode
import com.urbext.domain.model.OfflineMapSector
import com.urbext.domain.model.PdaOrientation
import com.urbext.domain.model.PowerProfile
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
        val MAP_LAYER = stringPreferencesKey("map_layer")
        val LOW_MEM_MODE = booleanPreferencesKey("low_mem_mode")
        val SHOW_OBJECT_ZONES = booleanPreferencesKey("show_object_zones")
        val SHOW_OBJECT_LABELS = booleanPreferencesKey("show_object_labels")
        val DEFAULT_OBJECTS_VIEW = stringPreferencesKey("default_objects_view")
        val AUTO_CENTER_GPS = booleanPreferencesKey("auto_center_gps")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val DEFAULT_ZONE_RADIUS_M = intPreferencesKey("default_zone_radius_m")
        val PDA_MODE_ENABLED = booleanPreferencesKey("pda_mode_enabled")
        val PDA_ORIENTATION = stringPreferencesKey("pda_orientation")
        val SECTORS_JSON = stringPreferencesKey("offline_sectors_json")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    fun observeSettings(): Flow<AppSettings> = context.urbexDataStore.data.map { prefs ->
        val defaultSectors = SeedData.defaultOfflineSectors()
        val sectors = prefs[Keys.SECTORS_JSON]?.let { raw ->
            runCatching { json.decodeFromString<List<OfflineMapSector>>(raw) }.getOrNull()
        } ?: defaultSectors

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
            mapLayer = prefs[Keys.MAP_LAYER]?.let {
                runCatching { MapLayerMode.valueOf(it) }.getOrNull()
            } ?: MapLayerMode.SATELLITE,
            lowMemoryMode = prefs[Keys.LOW_MEM_MODE] ?: false,
            showObjectZones = prefs[Keys.SHOW_OBJECT_ZONES] ?: true,
            showObjectLabels = prefs[Keys.SHOW_OBJECT_LABELS] ?: true,
            defaultObjectsView = prefs[Keys.DEFAULT_OBJECTS_VIEW]?.let {
                runCatching { ObjectsViewMode.valueOf(it) }.getOrNull()
            } ?: ObjectsViewMode.CARDS,
            autoCenterGps = prefs[Keys.AUTO_CENTER_GPS] ?: false,
            keepScreenAwake = prefs[Keys.KEEP_SCREEN_AWAKE] ?: false,
            defaultZoneRadiusM = prefs[Keys.DEFAULT_ZONE_RADIUS_M] ?: 80,
            pdaModeEnabled = prefs[Keys.PDA_MODE_ENABLED] ?: false,
            pdaOrientation = prefs[Keys.PDA_ORIENTATION]?.let {
                runCatching { PdaOrientation.valueOf(it) }.getOrNull()
            } ?: PdaOrientation.PORTRAIT,
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
            prefs[Keys.MAP_LAYER] = settings.mapLayer.name
            prefs[Keys.LOW_MEM_MODE] = settings.lowMemoryMode
            prefs[Keys.SHOW_OBJECT_ZONES] = settings.showObjectZones
            prefs[Keys.SHOW_OBJECT_LABELS] = settings.showObjectLabels
            prefs[Keys.DEFAULT_OBJECTS_VIEW] = settings.defaultObjectsView.name
            prefs[Keys.AUTO_CENTER_GPS] = settings.autoCenterGps
            prefs[Keys.KEEP_SCREEN_AWAKE] = settings.keepScreenAwake
            prefs[Keys.DEFAULT_ZONE_RADIUS_M] = settings.defaultZoneRadiusM
            prefs[Keys.PDA_MODE_ENABLED] = settings.pdaModeEnabled
            prefs[Keys.PDA_ORIENTATION] = settings.pdaOrientation.name
            prefs[Keys.SECTORS_JSON] = json.encodeToString(settings.offlineSectors)
            prefs[Keys.UPDATED_AT] = settings.updatedAt
        }
    }
}

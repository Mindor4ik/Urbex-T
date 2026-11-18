package com.urbext.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class ReliabilityStatus(val label: String, val glyph: String) {
    VERIFIED("Подтверждён", "●"),
    UNVERIFIED("Требует проверки", "▲"),
    CONTESTED("Спорный", "◆"),
    HAZARDOUS("Опасен", "✖")
}

@Serializable
enum class ObjectCategory(val label: String) {
    INDUSTRIAL("Промзона"),
    BUNKER("Спецобъект / Бункер"),
    CIVILIAN("Гражданский объект"),
    INFRASTRUCTURE("Инфраструктура"),
    UNDERGROUND("Подземка / Коллектор")
}

@Serializable
enum class PowerProfile(val label: String, val intervalMs: Long, val summary: String) {
    ECO("Эко (30 сек)", 30_000L, "Минимальный опрос GPS, пауза в фоне"),
    BALANCED("Баланс (10 сек)", 10_000L, "Оптимально для пешего маршрута"),
    PRECISION("Точный (2 сек)", 2_000L, "Частое обновление при съёмке координат"),
    MANUAL("Ручной запрос", 0L, "GPS включается только по нажатию")
}

@Serializable
enum class CoordinateFormat(val label: String) {
    DD("Десятичные градусы (DD)"),
    DMS("Градусы-минуты-секунды (DMS)"),
    UTM("Сетка UTM (метры)")
}

@Serializable
enum class OperatorRole(val label: String) {
    SCOUT("Разведчик"),
    CARTOGRAPHER("Картограф"),
    COMMS("Связист Mesh"),
    ARCHIVIST("Архивариус")
}

@Serializable
enum class ArchiveMediaType(val label: String) {
    PHOTO("Фото"),
    VIDEO("Видео"),
    AUDIO("Аудио"),
    NOTE("Заметка"),
    DOCUMENT("Документ")
}

@Serializable
enum class MeshPacketKind {
    TEXT,
    POSITION,
    SOS
}

@Serializable
enum class BaseSection(val label: String) {
    SAFETY("Безопасность"),
    NAVIGATION("Навигация"),
    COMMS("Связь"),
    ARCHITECTURE("Форматы и PDA"),
    FIELD_NOTE("Личная заметка")
}

enum class ConflictStrategy {
    SKIP_EXISTING,
    KEEP_NEWER,
    OVERWRITE
}

@Serializable
enum class ObjectsViewMode(val label: String) {
    LIST("Список"),
    COMPACT("Компакт"),
    CARDS("Карточки")
}

@Serializable
enum class MapLayerMode(val label: String) {
    SATELLITE("Спутник"),
    OFFLINE_VECTOR("Вектор")
}

@Serializable
enum class PdaOrientation(val label: String) {
    PORTRAIT("Портретная"),
    LANDSCAPE("Горизонтальная"),
    AUTO("Автоповорот")
}

@Serializable
enum class SecurityLevel(val label: String) {
    NONE("Нет охраны"),
    LOW("Слабая / обход"),
    MEDIUM("Средняя / ЧОП"),
    HIGH("Строгий режим")
}

@Serializable
enum class EntryDifficulty(val label: String) {
    EASY("Свободный"),
    MODERATE("Средняя"),
    HARD("Сложная"),
    TECHNICAL("Спецснаряжение")
}

@Immutable
@Serializable
data class GeoPoint(
    val lat: Double,
    val lon: Double
)

@Immutable
@Serializable
data class ObjectParam(
    val key: String,
    val value: String
)

@Immutable
@Serializable
data class GpsFix(
    val lat: Double,
    val lon: Double,
    val altitudeM: Int? = null,
    val accuracyM: Int? = null,
    val timestamp: Long,
    val source: String = "cached"
)

@Immutable
@Serializable
data class UrbexObject(
    val id: String,
    val title: String,
    val category: ObjectCategory,
    val status: ReliabilityStatus,
    val lat: Double,
    val lon: Double,
    val elevationM: Int,
    val description: String,
    val accessNotes: String,
    val hazards: List<String>,
    val photoUri: String? = null,
    val securityLevel: SecurityLevel = SecurityLevel.NONE,
    val entryDifficulty: EntryDifficulty = EntryDifficulty.MODERATE,
    val floorsOrDepth: String? = null,
    val customParams: List<ObjectParam> = emptyList(),
    val zone: List<GeoPoint> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val authorNodeId: String
)

@Immutable
@Serializable
data class MeshChannel(
    val id: String,
    val name: String,
    val hopLimit: Int,
    val isEmergency: Boolean = false
)

@Immutable
@Serializable
data class MeshNode(
    val nodeId: String,
    val callsign: String,
    val role: OperatorRole,
    val snrDb: Double,
    val rssiDbm: Int,
    val hopsAway: Int,
    val batteryPct: Int
)

@Immutable
@Serializable
data class MeshMessage(
    val id: String,
    val channelId: String,
    val senderNodeId: String,
    val senderCallsign: String,
    val kind: MeshPacketKind,
    val body: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val timestamp: Long,
    val hopLimit: Int,
    val acknowledged: Boolean,
    val frameHex: String
)

@Immutable
@Serializable
data class BaseArticle(
    val id: String,
    val title: String,
    val section: BaseSection,
    val summary: String,
    val content: String,
    val updatedAt: Long,
    val pinned: Boolean
)

@Immutable
@Serializable
data class ArchiveItem(
    val id: String,
    val title: String,
    val type: ArchiveMediaType,
    val objectId: String? = null,
    val mimeType: String,
    val sizeBytes: Long,
    val contentUri: String? = null,
    val textContent: String? = null,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Immutable
@Serializable
data class UserProfile(
    val id: String = "local",
    val callsign: String,
    val nodeId: String,
    val role: OperatorRole,
    val emergencyContact: String,
    val bio: String,
    val updatedAt: Long
)

@Immutable
@Serializable
data class OfflineMapSector(
    val id: String,
    val name: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusKm: Double,
    val zoomMin: Int,
    val zoomMax: Int,
    val sizeMb: Double,
    val cachedAt: Long
)

@Immutable
@Serializable
data class AppSettings(
    val powerProfile: PowerProfile = PowerProfile.BALANCED,
    val gpsIntervalMs: Long = 10_000L,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DD,
    val imageCacheLimitMb: Int = 64,
    val pageSize: Int = 12,
    val meshEnabled: Boolean = false,
    val mapLayer: MapLayerMode = MapLayerMode.SATELLITE,
    val lowMemoryMode: Boolean = false,
    val showObjectZones: Boolean = true,
    val showObjectLabels: Boolean = true,
    val defaultObjectsView: ObjectsViewMode = ObjectsViewMode.CARDS,
    val autoCenterGps: Boolean = false,
    val keepScreenAwake: Boolean = false,
    val defaultZoneRadiusM: Int = 80,
    val pdaModeEnabled: Boolean = false,
    val pdaOrientation: PdaOrientation = PdaOrientation.PORTRAIT,
    val offlineSectors: List<OfflineMapSector> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class UrbexDbCounts(
    val objects: Int,
    val meshMessages: Int,
    val baseArticles: Int,
    val archiveItems: Int
)

@Serializable
data class UrbexDbManifest(
    val format: String = "urbex-t-db",
    val version: String = "1.0.0",
    val exportedAt: Long,
    val deviceNodeId: String,
    val checksum: String,
    val counts: UrbexDbCounts
)

@Serializable
data class UrbexDbPayload(
    val objects: List<UrbexObject>,
    val meshMessages: List<MeshMessage>,
    val baseArticles: List<BaseArticle>,
    val archiveItems: List<ArchiveItem>,
    val profile: UserProfile,
    val settings: AppSettings
)

@Serializable
data class UrbexDbBundle(
    val manifest: UrbexDbManifest,
    val payload: UrbexDbPayload
)

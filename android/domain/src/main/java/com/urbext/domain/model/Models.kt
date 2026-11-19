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

@Serializable
enum class ZoneStyle(val label: String) {
    SOLID("Сплошная"),
    DASHED("Пунктир"),
    DOTTED("Точки"),
    OUTLINE("Контур")
}

val ZONE_COLOR_PRESETS: List<Pair<String, String>> = listOf(
    "#5B9D72" to "Зелёный",
    "#DF9E43" to "Янтарный",
    "#DC6860" to "Красный",
    "#38BDF8" to "Голубой",
    "#A78BFA" to "Фиолетовый",
    "#E6EBE8" to "Белый"
)

private val HEX_COLOR_REGEX = Regex("^#[0-9a-fA-F]{6}$")

fun normalizeZoneColor(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val prefixed = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    return if (HEX_COLOR_REGEX.matches(prefixed)) prefixed.uppercase() else null
}

fun normalizeZoneStyle(raw: String?): ZoneStyle {
    return when (raw?.trim()?.uppercase()) {
        "DASHED" -> ZoneStyle.DASHED
        "DOTTED" -> ZoneStyle.DOTTED
        "OUTLINE" -> ZoneStyle.OUTLINE
        else -> ZoneStyle.SOLID
    }
}

fun ReliabilityStatus.defaultZoneHex(): String = when (this) {
    ReliabilityStatus.VERIFIED -> "#5B9D72"
    ReliabilityStatus.UNVERIFIED -> "#D39239"
    ReliabilityStatus.CONTESTED -> "#B9A46B"
    ReliabilityStatus.HAZARDOUS -> "#C7564E"
}

fun UrbexObject.effectiveZoneColor(): String =
    normalizeZoneColor(zoneColor) ?: status.defaultZoneHex()

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
    val photos: List<String> = emptyList(),
    val securityLevel: SecurityLevel = SecurityLevel.NONE,
    val entryDifficulty: EntryDifficulty = EntryDifficulty.MODERATE,
    val floorsOrDepth: String? = null,
    val customParams: List<ObjectParam> = emptyList(),
    val zone: List<GeoPoint> = emptyList(),
    val zoneColor: String? = null,
    val zoneStyle: ZoneStyle = ZoneStyle.SOLID,
    val zoneHatched: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val authorNodeId: String
)

fun UrbexObject.allPhotos(): List<String> =
    (listOfNotNull(photoUri) + photos)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

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
    val editorEnabled: Boolean = false,
    val pdaModeEnabled: Boolean = false,
    val pdaOrientation: PdaOrientation = PdaOrientation.PORTRAIT,
    val offlineSectors: List<OfflineMapSector> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class UrbexDbCounts(
    val objects: Int = 0,
    val meshMessages: Int = 0,
    val baseArticles: Int = 0,
    val archiveItems: Int = 0
)

@Serializable
data class UrbexDbManifest(
    val format: String = "urbex-t-db",
    val version: String = "1.0.0",
    val exportedAt: Long = 0L,
    val deviceNodeId: String = "!manual",
    val checksum: String = "manual",
    val counts: UrbexDbCounts = UrbexDbCounts()
)

@Serializable
data class UrbexDbPayload(
    val objects: List<UrbexObject> = emptyList(),
    val meshMessages: List<MeshMessage> = emptyList(),
    val baseArticles: List<BaseArticle> = emptyList(),
    val archiveItems: List<ArchiveItem> = emptyList(),
    val profile: UserProfile = UserProfile(
        callsign = "Сокол-1",
        nodeId = "!a48f20c1",
        role = OperatorRole.SCOUT,
        emergencyContact = "",
        bio = "",
        updatedAt = 0L
    ),
    val settings: AppSettings = AppSettings()
)

@Serializable
data class UrbexDbBundle(
    val manifest: UrbexDbManifest,
    val payload: UrbexDbPayload
)

@Immutable
@Serializable
data class DatabaseWorkspaceMeta(
    val id: String,
    val name: String,
    val regionLabel: String,
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Immutable
@Serializable
data class WorkspaceCounts(
    val objects: Int = 0,
    val archiveItems: Int = 0,
    val baseArticles: Int = 0,
    val meshMessages: Int = 0
)

@Immutable
data class DatabaseWorkspaceSummary(
    val meta: DatabaseWorkspaceMeta,
    val counts: WorkspaceCounts
)

@Immutable
data class DbItemSource(
    val dbId: String,
    val dbName: String
)

@Immutable
data class WorkspaceState(
    val activeDbId: String = "db-moscow-main",
    val mountedDbIds: List<String> = listOf("db-moscow-main"),
    val workspaces: List<DatabaseWorkspaceSummary> = emptyList()
)


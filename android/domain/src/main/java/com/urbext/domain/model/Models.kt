package com.urbext.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
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
    UNDERGROUND("Подземка / Коллектор"),
    MILITARY("Военный объект / ВЧ"),
    ENERGY("Энергетика / ТЭЦ"),
    MEDICAL("Медицина / Госпиталь"),
    TRANSPORT("Транспорт / Депо / ЖД"),
    HYDRO("Гидротехника / Дамба"),
    MINING("Шахта / Карьер"),
    RUINS("Заброшка / Недострой")
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

@Serializable
enum class ConflictStrategy {
    @SerialName("skip_existing")
    SKIP_EXISTING,
    @SerialName("keep_newer")
    KEEP_NEWER,
    @SerialName("overwrite")
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

@Serializable
enum class ObjectIcon(val label: String) {
    FACTORY("Завод / цех"),
    BUNKER("Бункер / ЗС ГО"),
    BUILDING("Здание / корпус"),
    TOWER("Вышка / мачта"),
    TUNNEL("Тоннель / метро"),
    WAREHOUSE("Ангар / склад"),
    POWER("Подстанция / ЛЭП"),
    BOILER("Котельная / ТЭЦ"),
    WATER("Водоканал / коллектор"),
    FUEL("Склад ГСМ / цистерны"),
    RADAR("РЛС / радиоузел"),
    RADIATION("Радиация / спецзона"),
    HAZARD("Аварийный объект"),
    SKULL("Особая опасность"),
    OBSERVATION("Наблюдательный пункт"),
    GATE("КПП / периметр"),
    CRANE("Недострой / кран"),
    HOSPITAL("Госпиталь / санчасть"),
    AIRFIELD("Аэродром / ВПП"),
    PORT("Причал / док"),
    SHAFT("Шахта / карьер"),
    WORKSHOP("Депо / мастерская"),
    CAMERA("Пост наблюдения"),
    PIN("Опорная точка"),
    MILITARY("Военный объект / ВЧ"),
    SILO("ШПУ / ракетная шахта"),
    LAB("НИИ / лаборатория"),
    BIOHAZARD("Биоопасность / химзона"),
    ANTENNA("Ретранслятор / радио"),
    TRAIN("Ж/Д узел / тупик"),
    BRIDGE("Мост / эстакада"),
    DAM("Плотина / ГЭС / шлюз"),
    MONUMENT("Усадьба / памятник / ДК"),
    SCHOOL("Школа / корпус / ВУЗ"),
    HOTEL("Санаторий / пансионат"),
    RUINS("Руины / развалины"),
    SHELTER("Убежище / бомбоубежище"),
    FIRE("Пожарная часть / МЧС"),
    COMPASS("Ориентир / триангуляция"),
    STASH("Тайник / схрон / кэш"),
    SUBSTATION("Щитовая / ТП / РУ"),
    COOLING("Градирня / ТЭЦ-труба"),
    CISTERN("Резервуар / РВС"),
    ELEVATOR("Элеватор / зернобашня"),
    HANGAR("Авиаангар / капонир"),
    CHECKPOINT("Блокпост / шлагбаум"),
    BARRACKS("Казарма / штаб ВЧ"),
    RANGE("Полигон / стрельбище"),
    SEARCHLIGHT("Прожектор / ПВО-пост"),
    MUNITION("Арсенал / склад БК"),
    METRO("Станция / вестибюль метро"),
    VENT("Вентшахта / ВШ"),
    DRAINAGE("Штольня / дренаж"),
    CATACOMB("Катакомбы / выработки"),
    VAULT("Спецблок / хранилище"),
    DEPOT("Ж/Д депо / веерное"),
    BRIDGE_RAIL("Ж/Д мост / виадук"),
    PIER("Дебаркадер / пирс"),
    LIGHTHOUSE("Маяк / створный знак"),
    QUARRY("Карьер / разрез"),
    MINE_CART("Рудник / вагонетка"),
    OIL_RIG("Нефтекачка / скважина"),
    PIPELINE("Трубопровод / эстакада"),
    MORGUE("Морг / прозекторская"),
    SANATORIUM("Профилакторий / пионерлагерь"),
    CHURCH("Храм / колокольня"),
    CASTLE("Усадьба / замок / башня"),
    THEATER("ДК / кинотеатр / клуб"),
    STADIUM("Стадион / спортзал"),
    MALL("ТЦ / универмаг / рынок"),
    GENERATOR("ДГУ / резервный дизель"),
    EVAC("Точка эвакуации / выход")
}

@Immutable
data class ObjectSubtypePreset(
    val label: String,
    val icon: ObjectIcon
)

fun ObjectCategory.subtypePresets(): List<ObjectSubtypePreset> = when (this) {
    ObjectCategory.INDUSTRIAL -> listOf(
        ObjectSubtypePreset("Цех / промкорпус", ObjectIcon.FACTORY),
        ObjectSubtypePreset("Элеватор / силос", ObjectIcon.ELEVATOR),
        ObjectSubtypePreset("Склад / ангар", ObjectIcon.WAREHOUSE),
        ObjectSubtypePreset("Резервуар ГСМ", ObjectIcon.FUEL),
        ObjectSubtypePreset("НИИ / лаборатория", ObjectIcon.LAB),
        ObjectSubtypePreset("Ремзона / мастерская", ObjectIcon.WORKSHOP)
    )
    ObjectCategory.BUNKER -> listOf(
        ObjectSubtypePreset("Бомбоубежище ЗС ГО", ObjectIcon.SHELTER),
        ObjectSubtypePreset("Командный пункт (ЗКП)", ObjectIcon.BUNKER),
        ObjectSubtypePreset("Спецхранилище", ObjectIcon.VAULT),
        ObjectSubtypePreset("ДГУ / дизельная", ObjectIcon.GENERATOR),
        ObjectSubtypePreset("Точка эвакуации", ObjectIcon.EVAC)
    )
    ObjectCategory.CIVILIAN -> listOf(
        ObjectSubtypePreset("ДК / кинотеатр", ObjectIcon.THEATER),
        ObjectSubtypePreset("Школа / ВУЗ / НИИ", ObjectIcon.SCHOOL),
        ObjectSubtypePreset("Санаторий / пионерлагерь", ObjectIcon.SANATORIUM),
        ObjectSubtypePreset("Храм / колокольня", ObjectIcon.CHURCH),
        ObjectSubtypePreset("Усадьба / памятник", ObjectIcon.CASTLE),
        ObjectSubtypePreset("ТЦ / универмаг", ObjectIcon.MALL),
        ObjectSubtypePreset("Стадион / спорткомплекс", ObjectIcon.STADIUM)
    )
    ObjectCategory.INFRASTRUCTURE -> listOf(
        ObjectSubtypePreset("Радиовышка / мачта", ObjectIcon.TOWER),
        ObjectSubtypePreset("Ретранслятор", ObjectIcon.ANTENNA),
        ObjectSubtypePreset("КПП / периметр", ObjectIcon.CHECKPOINT),
        ObjectSubtypePreset("Трубопроводная эстакада", ObjectIcon.PIPELINE),
        ObjectSubtypePreset("Пожарная часть", ObjectIcon.FIRE)
    )
    ObjectCategory.UNDERGROUND -> listOf(
        ObjectSubtypePreset("Тоннель / спецход", ObjectIcon.TUNNEL),
        ObjectSubtypePreset("Вентшахта (ВШ)", ObjectIcon.VENT),
        ObjectSubtypePreset("Дренаж / ливневой коллектор", ObjectIcon.DRAINAGE),
        ObjectSubtypePreset("Катакомбы / каменоломня", ObjectIcon.CATACOMB),
        ObjectSubtypePreset("Вестибюль / метрострой", ObjectIcon.METRO)
    )
    ObjectCategory.MILITARY -> listOf(
        ObjectSubtypePreset("Воинская часть / штаб", ObjectIcon.BARRACKS),
        ObjectSubtypePreset("ШПУ / ракетная база", ObjectIcon.SILO),
        ObjectSubtypePreset("РЛС / узел связи", ObjectIcon.RADAR),
        ObjectSubtypePreset("Полигон / стрельбище", ObjectIcon.RANGE),
        ObjectSubtypePreset("Склад БК / арсенал", ObjectIcon.MUNITION),
        ObjectSubtypePreset("Позиция ПВО / капонир", ObjectIcon.SEARCHLIGHT)
    )
    ObjectCategory.ENERGY -> listOf(
        ObjectSubtypePreset("Подстанция / ОРУ / ТП", ObjectIcon.SUBSTATION),
        ObjectSubtypePreset("ТЭЦ / котельная", ObjectIcon.BOILER),
        ObjectSubtypePreset("Градирня / дымовая труба", ObjectIcon.COOLING),
        ObjectSubtypePreset("ЛЭП / высоковольтная опора", ObjectIcon.POWER),
        ObjectSubtypePreset("ДГУ / резервный генератор", ObjectIcon.GENERATOR)
    )
    ObjectCategory.MEDICAL -> listOf(
        ObjectSubtypePreset("Госпиталь / больница", ObjectIcon.HOSPITAL),
        ObjectSubtypePreset("Морг / патанатомия", ObjectIcon.MORGUE),
        ObjectSubtypePreset("Биолаборатория", ObjectIcon.BIOHAZARD),
        ObjectSubtypePreset("Профилакторий / здравница", ObjectIcon.HOTEL)
    )
    ObjectCategory.TRANSPORT -> listOf(
        ObjectSubtypePreset("Ж/Д депо / веерное", ObjectIcon.DEPOT),
        ObjectSubtypePreset("Ж/Д мост / виадук", ObjectIcon.BRIDGE_RAIL),
        ObjectSubtypePreset("Аэродром / ВПП / ангар", ObjectIcon.HANGAR),
        ObjectSubtypePreset("Причал / дебаркадер", ObjectIcon.PIER),
        ObjectSubtypePreset("Эстакада / путепровод", ObjectIcon.BRIDGE)
    )
    ObjectCategory.HYDRO -> listOf(
        ObjectSubtypePreset("Плотина / ГЭС / шлюз", ObjectIcon.DAM),
        ObjectSubtypePreset("Водозабор / насосная", ObjectIcon.WATER),
        ObjectSubtypePreset("Резервуар / водонапорка", ObjectIcon.CISTERN),
        ObjectSubtypePreset("Маяк / гидропост", ObjectIcon.LIGHTHOUSE)
    )
    ObjectCategory.MINING -> listOf(
        ObjectSubtypePreset("Шахтный ствол / копёр", ObjectIcon.SHAFT),
        ObjectSubtypePreset("Открытый карьер / разрез", ObjectIcon.QUARRY),
        ObjectSubtypePreset("Рудник / штольня", ObjectIcon.MINE_CART),
        ObjectSubtypePreset("Нефтяная вышка / скважина", ObjectIcon.OIL_RIG)
    )
    ObjectCategory.RUINS -> listOf(
        ObjectSubtypePreset("Недострой / башенный кран", ObjectIcon.CRANE),
        ObjectSubtypePreset("Руины / развалины", ObjectIcon.RUINS),
        ObjectSubtypePreset("Исторический монумент", ObjectIcon.MONUMENT),
        ObjectSubtypePreset("Тайник / схрон", ObjectIcon.STASH),
        ObjectSubtypePreset("Опасный / аварийный сектор", ObjectIcon.HAZARD)
    )
}

fun ObjectCategory.defaultIcon(): ObjectIcon = when (this) {
    ObjectCategory.INDUSTRIAL -> ObjectIcon.FACTORY
    ObjectCategory.BUNKER -> ObjectIcon.BUNKER
    ObjectCategory.CIVILIAN -> ObjectIcon.BUILDING
    ObjectCategory.INFRASTRUCTURE -> ObjectIcon.TOWER
    ObjectCategory.UNDERGROUND -> ObjectIcon.TUNNEL
    ObjectCategory.MILITARY -> ObjectIcon.MILITARY
    ObjectCategory.ENERGY -> ObjectIcon.POWER
    ObjectCategory.MEDICAL -> ObjectIcon.HOSPITAL
    ObjectCategory.TRANSPORT -> ObjectIcon.TRAIN
    ObjectCategory.HYDRO -> ObjectIcon.DAM
    ObjectCategory.MINING -> ObjectIcon.SHAFT
    ObjectCategory.RUINS -> ObjectIcon.RUINS
}

fun normalizeObjectCategory(raw: String?): ObjectCategory {
    val clean = raw?.trim()?.uppercase()?.replace('-', '_') ?: return ObjectCategory.INDUSTRIAL
    if (clean.isEmpty()) return ObjectCategory.INDUSTRIAL
    return ObjectCategory.entries.firstOrNull { it.name == clean } ?: ObjectCategory.INDUSTRIAL
}

fun normalizeObjectIcon(raw: String?): ObjectIcon? {
    val clean = raw?.trim()?.uppercase()?.replace('-', '_') ?: return null
    if (clean.isEmpty()) return null
    return ObjectIcon.entries.firstOrNull { it.name == clean }
}

fun normalizeObjectSubtype(raw: String?): String? {
    val clean = raw?.trim().orEmpty()
    return clean.ifEmpty { null }
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
    val authorNodeId: String,
    val icon: ObjectIcon? = null,
    val subtype: String? = null
)

fun UrbexObject.effectiveIcon(): ObjectIcon = icon ?: category.defaultIcon()

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

@Serializable
enum class SettingsCategory(val label: String, val subtitle: String) {
    DATABASES("Базы и облако", "Регионы, автосинк"),
    MAP_GPS("Карта и GPS", "Питание, сетка, HUD"),
    RADIO_MESH("Радио и Mesh", "Частоты, CTCSS, шифр"),
    PDA_UI("PDA и экран", "Редактор, кэш, защита")
}

@Serializable
enum class RadioModulation(val label: String, val shortLabel: String) {
    NFM("NFM (Узкая ЧМ)", "NFM"),
    FM("FM (Широкая ЧМ)", "FM"),
    PMR446("PMR446", "PMR"),
    LPD("LPD433", "LPD"),
    DMR("DMR (Цифра)", "DMR"),
    LORA("LoRa / Mesh", "LoRa"),
    AM("AM (Авиа/Си-Би)", "AM")
}

@Serializable
enum class ToneSquelchMode(val label: String) {
    NONE("Нет (CSQ)"),
    CTCSS("CTCSS (субтон Hz)"),
    DCS("DCS (цифровой N)"),
    DCS_INV("DCS-I (инверсный)")
}

@Serializable
enum class RadioPowerLevel(val label: String) {
    LOW("LOW (0.5 Вт)"),
    MID("MID (2 Вт)"),
    HIGH("HIGH (5+ Вт)")
}

@Serializable
enum class RadioEncryptionType(val label: String) {
    NONE("Открытый (нет)"),
    SCRAMBLER("Скремблер"),
    ARC4("ARC4 / DMR BP"),
    AES128("AES-128"),
    AES256("AES-256")
}

@Immutable
@Serializable
data class RadioChannelPreset(
    val id: String,
    val name: String,
    val rxFreqMhz: String,
    val txFreqMhz: String,
    val mode: RadioModulation = RadioModulation.NFM,
    val bandwidthKhz: String = "12.5",
    val toneMode: ToneSquelchMode = ToneSquelchMode.NONE,
    val rxTone: String = "NONE",
    val txTone: String = "NONE",
    val squelchLevel: Int = 3,
    val powerLevel: RadioPowerLevel = RadioPowerLevel.LOW,
    val encryptionType: RadioEncryptionType = RadioEncryptionType.NONE,
    val encryptionKey: String = "",
    val txInhibit: Boolean = false,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class CloudSyncProvider(
    val label: String,
    val costLabel: String,
    val summary: String,
    val endpointPlaceholder: String,
    val setupHint: String
) {
    @SerialName("none")
    NONE(
        label = "Локально (выкл.)",
        costLabel = "Офлайн",
        summary = "Данные хранятся только на устройстве.",
        endpointPlaceholder = "",
        setupHint = "Выберите бесплатный провайдер ниже для автоматической синхронизации."
    ),
    @SerialName("github_gist")
    GITHUB_GIST(
        label = "GitHub Secret Gist",
        costLabel = "Бесплатно · без лимита",
        summary = "Приватный Gist в вашем аккаунте GitHub по бесплатному токену (scope: gist). Работает прямо в Android APK и Web.",
        endpointPlaceholder = "Автосоздание или ID существующего Gist",
        setupHint = "Создайте бесплатный Personal Access Token (Classic -> галочка gist). Если Gist ID пустой, он создастся при первой синхронизации."
    ),
    @SerialName("webdav")
    WEBDAV(
        label = "WebDAV (Яндекс.Диск / Nextcloud)",
        costLabel = "Бесплатно · свой диск",
        summary = "Яндекс.Диск (https://webdav.yandex.ru/UrbexT), Nextcloud или ownCloud по паролю приложения.",
        endpointPlaceholder = "https://webdav.yandex.ru/UrbexT",
        setupHint = "На Android работает напрямую без ограничений CORS. Используйте пароль приложения для Яндекс.Диска или Nextcloud."
    ),
    @SerialName("local_folder")
    LOCAL_FOLDER(
        label = "Папка диска (Drive / Syncthing)",
        costLabel = "Бесплатно · офлайн-мост",
        summary = "Чтение и запись .urbexdb в выбранную системную папку (Google Drive, Яндекс.Диск, Syncthing, FolderSync).",
        endpointPlaceholder = "Выбирается системным диалогом папки",
        setupHint = "Полностью автономно без токенов: синхронизацию выбранной папки с облаком выполняет Syncthing, FolderSync или клиент диска."
    ),
    @SerialName("custom_http")
    CUSTOM_HTTP(
        label = "Свой HTTP / Worker REST",
        costLabel = "Бесплатно · свой URL",
        summary = "Любой бесплатный endpoint (Cloudflare Workers, ntfy/KV, свой сервер), принимающий GET и PUT текста.",
        endpointPlaceholder = "https://your-worker.workers.dev/urbex-sync",
        setupHint = "Приложение скачивает снимок через GET, объединяет записи локально и загружает объединённую базу через PUT."
    )
}

@Serializable
enum class CloudSyncStatus {
    @SerialName("idle")
    IDLE,
    @SerialName("syncing")
    SYNCING,
    @SerialName("success")
    SUCCESS,
    @SerialName("error")
    ERROR
}

@Immutable
@Serializable
data class CloudSyncConfig(
    val enabled: Boolean = false,
    val provider: CloudSyncProvider = CloudSyncProvider.NONE,
    val autoSyncOnChange: Boolean = true,
    val autoSyncIntervalMin: Int = 30,
    val wifiOnly: Boolean = true,
    val encryptBeforeUpload: Boolean = true,
    val encryptionPassphrase: String = "",
    val conflictStrategy: ConflictStrategy = ConflictStrategy.KEEP_NEWER,
    val remoteEndpoint: String = "",
    val authToken: String = "",
    val username: String = "",
    val gistId: String = "",
    val remoteFileName: String = "urbex-t-sync.urbexdb",
    val lastSyncAt: Long? = null,
    val lastSyncStatus: CloudSyncStatus = CloudSyncStatus.IDLE,
    val lastSyncMessage: String? = null
)

@Serializable
data class EncryptedSyncEnvelope(
    val format: String = "urbex-e2ee-v1",
    val kdf: String = "PBKDF2-SHA256",
    val iterations: Int = 100_000,
    val saltB64: String,
    val ivB64: String,
    val cipherB64: String,
    val updatedAt: Long
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
    val radioChannelsTabEnabled: Boolean = true,
    val mapLayer: MapLayerMode = MapLayerMode.SATELLITE,
    val lowMemoryMode: Boolean = false,
    val showObjectZones: Boolean = true,
    val showObjectLabels: Boolean = true,
    val showMapGrid: Boolean = true,
    val showCompassHud: Boolean = true,
    val defaultObjectsView: ObjectsViewMode = ObjectsViewMode.CARDS,
    val autoCenterGps: Boolean = false,
    val keepScreenAwake: Boolean = false,
    val defaultZoneRadiusM: Int = 80,
    val hazardProximityAlertM: Int = 150,
    val confirmBeforeDelete: Boolean = true,
    val hapticFeedback: Boolean = true,
    val maskCoordinatesOnScreen: Boolean = false,
    val editorEnabled: Boolean = false,
    val pdaModeEnabled: Boolean = false,
    val pdaOrientation: PdaOrientation = PdaOrientation.PORTRAIT,
    val radioChannels: List<RadioChannelPreset> = emptyList(),
    val cloudSync: CloudSyncConfig = CloudSyncConfig(),
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


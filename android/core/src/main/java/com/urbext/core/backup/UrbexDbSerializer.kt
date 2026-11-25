package com.urbext.core.backup

import com.urbext.domain.model.UrbexDbBundle
import com.urbext.domain.model.UrbexDbCounts
import com.urbext.domain.model.UrbexDbManifest
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.model.ZonePattern
import com.urbext.domain.model.normalizeObjectCategory
import com.urbext.domain.model.normalizeObjectIcon
import com.urbext.domain.model.normalizeObjectSubtype
import com.urbext.domain.model.normalizeZoneColor
import com.urbext.domain.model.normalizeZonePattern
import com.urbext.domain.model.normalizeZoneStyle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Locale

data class SchemaFieldDoc(
    val name: String,
    val type: String,
    val desc: String
)

data class SchemaSectionDoc(
    val section: String,
    val fields: List<SchemaFieldDoc>
)

sealed interface ParseUrbexDbResult {
    data class Valid(
        val bundle: UrbexDbBundle,
        val warnings: List<String> = emptyList()
    ) : ParseUrbexDbResult

    data class Invalid(val error: String) : ParseUrbexDbResult
}

object UrbexDbSerializer {
    const val FORMAT_ID = "urbex-t-db"
    const val SCHEMA_VERSION = "1.0.0"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val canonicalJson = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val ENUM_UPPER_KEYS = setOf(
        "category",
        "icon",
        "status",
        "securityLevel",
        "entryDifficulty",
        "section",
        "type",
        "kind",
        "role",
        "powerProfile",
        "coordinateFormat",
        "mapLayer",
        "defaultObjectsView",
        "pdaOrientation",
        "zoneStyle",
        "zonePattern",
        "mode",
        "toneMode",
        "powerLevel",
        "encryptionType"
    )

    private val ENUM_LOWER_KEYS = setOf(
        "provider",
        "conflictStrategy",
        "lastSyncStatus"
    )

    /**
     * Deterministic FNV-1a + DJB2 dual 32-bit checksum matching the Web PDA format.
     */
    fun computeChecksum(payload: UrbexDbPayload): String {
        val canonical = canonicalJson.encodeToString(payload)
        var h1 = 0x811c9dc5.toInt()
        var h2 = 5381
        for (ch in canonical) {
            val code = ch.code
            h1 = h1 xor code
            h1 *= 0x01000193
            h2 = (h2 * 33) xor code
        }
        val part1 = (h1.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0')
        val part2 = (h2.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0')
        return "uxdb-$part1$part2"
    }

    fun serialize(
        payload: UrbexDbPayload,
        deviceNodeId: String,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val safePayload = CloudSyncCrypto.sanitizePayloadForSync(payload)
        val bundle = UrbexDbBundle(
            manifest = UrbexDbManifest(
                format = FORMAT_ID,
                version = SCHEMA_VERSION,
                exportedAt = exportedAt,
                deviceNodeId = deviceNodeId,
                checksum = computeChecksum(safePayload),
                counts = UrbexDbCounts(
                    objects = safePayload.objects.size,
                    meshMessages = safePayload.meshMessages.size,
                    baseArticles = safePayload.baseArticles.size,
                    archiveItems = safePayload.archiveItems.size
                )
            ),
            payload = safePayload
        )
        return json.encodeToString(bundle)
    }

    /**
     * Removes // line comments and block comments outside JSON string literals,
     * allowing hand-authored .urbexdb templates with inline documentation to parse cleanly.
     */
    fun stripJsonComments(raw: String): String {
        val input = if (raw.startsWith("\uFEFF")) raw.substring(1) else raw
        val out = StringBuilder(input.length)
        var inString = false
        var escaped = false
        var i = 0

        while (i < input.length) {
            val ch = input[i]
            val next = if (i + 1 < input.length) input[i + 1] else '\u0000'

            if (inString) {
                out.append(ch)
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                i++
                continue
            }

            if (ch == '"') {
                inString = true
                out.append(ch)
                i++
                continue
            }

            if (ch == '/' && next == '/') {
                i += 2
                while (i < input.length && input[i] != '\n' && input[i] != '\r') {
                    i++
                }
                continue
            }

            if (ch == '/' && next == '*') {
                i += 2
                while (i < input.length) {
                    if (input[i] == '*' && i + 1 < input.length && input[i + 1] == '/') {
                        i += 2
                        break
                    }
                    if (input[i] == '\n') out.append('\n')
                    i++
                }
                continue
            }

            out.append(ch)
            i++
        }

        return out.toString()
    }

    fun parseAndVerify(raw: String): ParseUrbexDbResult {
        val cleaned = stripJsonComments(raw)
        val root = try {
            json.parseToJsonElement(cleaned).jsonObject
        } catch (_: Exception) {
            return ParseUrbexDbResult.Invalid("Файл повреждён или не является корректным JSON-контейнером .urbexdb.")
        }

        val manifestObj = root["manifest"] as? JsonObject
        val payloadObj = root["payload"] as? JsonObject
        if (manifestObj == null || payloadObj == null) {
            return ParseUrbexDbResult.Invalid("Некорректная структура контейнера .urbexdb (отсутствует manifest или payload).")
        }

        val format = manifestObj["format"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (format != FORMAT_ID) {
            return ParseUrbexDbResult.Invalid("Неизвестный формат контейнера: $format.")
        }

        val version = manifestObj["version"]?.jsonPrimitive?.contentOrNull ?: "1.0.0"
        val major = version.substringBefore('.')
        if (major != "1") {
            return ParseUrbexDbResult.Invalid("Неподдерживаемая версия формата: $version.")
        }

        val rawChecksum = manifestObj["checksum"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val isManualChecksum = rawChecksum.isEmpty() || rawChecksum == "manual" || rawChecksum == "auto"

        if (!isManualChecksum) {
            val strictBundle = runCatching { json.decodeFromString<UrbexDbBundle>(cleaned) }.getOrNull()
            if (strictBundle != null) {
                val expected = computeChecksum(strictBundle.payload)
                if (strictBundle.manifest.checksum != expected) {
                    return ParseUrbexDbResult.Invalid(
                        "Нарушена контрольная сумма целостности (ожидалось $expected, получено ${strictBundle.manifest.checksum})."
                    )
                }
                return ParseUrbexDbResult.Valid(strictBundle)
            }
        }

        // Normalize manual or Web-formatted .urbexdb JSON into Android enum & field names
        val warnings = mutableListOf<String>()
        if (isManualChecksum) {
            warnings.add("Импорт ручного шаблона .urbexdb (контрольная сумма пересчитана автоматически).")
        } else {
            warnings.add("Файл импортирован с нормализацией полей формата .urbexdb v1.")
        }

        val now = System.currentTimeMillis()
        val normalizedPayloadJson = normalizePayloadJson(payloadObj, now)
        val normalizedRoot = buildJsonObject {
            put("manifest", manifestObj)
            put("payload", normalizedPayloadJson)
        }

        val decodedBundle = try {
            json.decodeFromJsonElement(UrbexDbBundle.serializer(), normalizedRoot)
        } catch (err: Exception) {
            return ParseUrbexDbResult.Invalid(
                "Ошибка чтения полей .urbexdb: ${err.message ?: "проверьте обязательные поля объектов"}."
            )
        }

        val recomputedChecksum = computeChecksum(decodedBundle.payload)
        val fixedManifest = decodedBundle.manifest.copy(
            checksum = recomputedChecksum,
            counts = UrbexDbCounts(
                objects = decodedBundle.payload.objects.size,
                meshMessages = decodedBundle.payload.meshMessages.size,
                baseArticles = decodedBundle.payload.baseArticles.size,
                archiveItems = decodedBundle.payload.archiveItems.size
            )
        )
        return ParseUrbexDbResult.Valid(
            bundle = decodedBundle.copy(manifest = fixedManifest),
            warnings = warnings
        )
    }

    private fun normalizePayloadJson(payload: JsonObject, now: Long): JsonObject {
        val rawObjects = payload["objects"]?.jsonArray ?: JsonArray(emptyList())
        val normObjects = JsonArray(
            rawObjects.mapIndexedNotNull { idx, elem ->
                val obj = elem as? JsonObject ?: return@mapIndexedNotNull null
                normalizeObjectJson(obj, idx, now)
            }
        )

        val rawArchive = payload["archiveItems"] as? JsonArray ?: JsonArray(emptyList())
        val normArchive = JsonArray(
            rawArchive.mapIndexedNotNull { idx, elem ->
                val item = elem as? JsonObject ?: return@mapIndexedNotNull null
                normalizeArchiveJson(item, idx, now)
            }
        )

        val rawArticles = payload["baseArticles"] as? JsonArray ?: JsonArray(emptyList())
        val normArticles = JsonArray(
            rawArticles.mapNotNull { elem ->
                val art = elem as? JsonObject ?: return@mapNotNull null
                uppercaseEnumsInObject(art)
            }
        )

        val rawMesh = payload["meshMessages"] as? JsonArray ?: JsonArray(emptyList())
        val normMesh = JsonArray(
            rawMesh.mapNotNull { elem ->
                val msg = elem as? JsonObject ?: return@mapNotNull null
                uppercaseEnumsInObject(msg)
            }
        )

        return buildJsonObject {
            put("objects", normObjects)
            put("meshMessages", normMesh)
            put("baseArticles", normArticles)
            put("archiveItems", normArchive)
            (payload["profile"] as? JsonObject)?.let { put("profile", uppercaseEnumsInObject(it)) }
            (payload["settings"] as? JsonObject)?.let { put("settings", uppercaseEnumsInObject(it)) }
        }
    }

    private fun normalizeObjectJson(obj: JsonObject, idx: Int, now: Long): JsonObject {
        val normalized = uppercaseEnumsInObject(obj).toMutableMap()
        if (!normalized.containsKey("id")) {
            normalized["id"] = JsonPrimitive("obj-manual-${now.toString(36)}-$idx")
        }
        val rawCategory = (normalized["category"] as? JsonPrimitive)?.contentOrNull
        normalized["category"] = JsonPrimitive(normalizeObjectCategory(rawCategory).name)
        if (!normalized.containsKey("status")) {
            normalized["status"] = JsonPrimitive("UNVERIFIED")
        }
        if (!normalized.containsKey("elevationM")) {
            normalized["elevationM"] = JsonPrimitive(0)
        }
        if (!normalized.containsKey("description")) {
            normalized["description"] = JsonPrimitive("Импортированный объект.")
        }
        if (!normalized.containsKey("accessNotes")) {
            normalized["accessNotes"] = JsonPrimitive("Подходы уточняются на местности.")
        }
        if (!normalized.containsKey("hazards")) {
            normalized["hazards"] = JsonArray(emptyList())
        }
        if (!normalized.containsKey("createdAt")) {
            normalized["createdAt"] = JsonPrimitive(now)
        }
        if (!normalized.containsKey("updatedAt")) {
            normalized["updatedAt"] = JsonPrimitive(now)
        }
        if (!normalized.containsKey("authorNodeId")) {
            normalized["authorNodeId"] = JsonPrimitive("!manual00")
        }
        val collectedPhotos = linkedSetOf<String>()
        val coverCandidate = (normalized["photoUri"] as? JsonPrimitive)?.contentOrNull
            ?: (normalized["photoUrl"] as? JsonPrimitive)?.contentOrNull
        if (!coverCandidate.isNullOrBlank()) {
            collectedPhotos.add(coverCandidate.trim())
        }
        listOf("photos", "photoUrls", "photoUris").forEach { key ->
            val arr = normalized[key] as? JsonArray
            arr?.forEach { elem ->
                val text = (elem as? JsonPrimitive)?.contentOrNull?.trim()
                if (!text.isNullOrEmpty()) {
                    collectedPhotos.add(text)
                }
            }
        }
        val orderedPhotos = collectedPhotos.toList()
        if (orderedPhotos.isNotEmpty()) {
            normalized["photoUri"] = JsonPrimitive(orderedPhotos.first())
            normalized["photos"] = JsonArray(orderedPhotos.map { JsonPrimitive(it) })
        } else {
            normalized.remove("photoUri")
            normalized["photos"] = JsonArray(emptyList())
        }
        val rawZoneColor = (normalized["zoneColor"] as? JsonPrimitive)?.contentOrNull
        val cleanZoneColor = normalizeZoneColor(rawZoneColor)
        if (cleanZoneColor != null) {
            normalized["zoneColor"] = JsonPrimitive(cleanZoneColor)
        } else {
            normalized.remove("zoneColor")
        }
        val rawIcon = (normalized["icon"] as? JsonPrimitive)?.contentOrNull
        val cleanIcon = normalizeObjectIcon(rawIcon)
        if (cleanIcon != null) {
            normalized["icon"] = JsonPrimitive(cleanIcon.name)
        } else {
            normalized.remove("icon")
        }
        val rawSubtype = (normalized["subtype"] as? JsonPrimitive)?.contentOrNull
        val cleanSubtype = normalizeObjectSubtype(rawSubtype)
        if (cleanSubtype != null) {
            normalized["subtype"] = JsonPrimitive(cleanSubtype)
        } else {
            normalized.remove("subtype")
        }
        val rawZoneStyle = (normalized["zoneStyle"] as? JsonPrimitive)?.contentOrNull
        normalized["zoneStyle"] = JsonPrimitive(normalizeZoneStyle(rawZoneStyle).name)
        val rawZonePattern = (normalized["zonePattern"] as? JsonPrimitive)?.contentOrNull
        val rawHatched = (normalized["zoneHatched"] as? JsonPrimitive)?.booleanOrNull ?: false
        val cleanPattern = normalizeZonePattern(rawZonePattern, rawHatched)
        normalized["zonePattern"] = JsonPrimitive(cleanPattern.name)
        normalized["zoneHatched"] = JsonPrimitive(cleanPattern != ZonePattern.NONE)
        return JsonObject(normalized)
    }

    private fun normalizeArchiveJson(item: JsonObject, idx: Int, now: Long): JsonObject {
        val normalized = uppercaseEnumsInObject(item).toMutableMap()
        if (!normalized.containsKey("id")) {
            normalized["id"] = JsonPrimitive("arch-manual-${now.toString(36)}-$idx")
        }
        if (!normalized.containsKey("type")) {
            normalized["type"] = JsonPrimitive("NOTE")
        }
        if (!normalized.containsKey("mimeType")) {
            normalized["mimeType"] = JsonPrimitive("text/plain")
        }
        if (!normalized.containsKey("sizeBytes")) {
            normalized["sizeBytes"] = JsonPrimitive(0L)
        }
        if (!normalized.containsKey("notes")) {
            normalized["notes"] = JsonPrimitive("")
        }
        if (!normalized.containsKey("createdAt")) {
            normalized["createdAt"] = JsonPrimitive(now)
        }
        if (!normalized.containsKey("updatedAt")) {
            normalized["updatedAt"] = JsonPrimitive(now)
        }
        val contentUri = normalized["contentUri"] ?: normalized["dataUrl"]
        if (contentUri != null) {
            normalized["contentUri"] = contentUri
        }
        return JsonObject(normalized)
    }

    private fun uppercaseEnumsInObject(obj: JsonObject): JsonObject {
        val map = LinkedHashMap<String, JsonElement>(obj.size)
        for ((k, v) in obj) {
            map[k] = when {
                k in ENUM_UPPER_KEYS && v is JsonPrimitive && v.isString ->
                    JsonPrimitive(v.content.uppercase(Locale.US))
                k in ENUM_LOWER_KEYS && v is JsonPrimitive && v.isString ->
                    JsonPrimitive(v.content.lowercase(Locale.US))
                v is JsonObject -> uppercaseEnumsInObject(v)
                v is JsonArray -> normalizeEnumsInArray(v)
                else -> v
            }
        }
        return JsonObject(map)
    }

    private fun normalizeEnumsInArray(arr: JsonArray): JsonArray {
        return JsonArray(
            arr.map { elem ->
                when (elem) {
                    is JsonObject -> uppercaseEnumsInObject(elem)
                    is JsonArray -> normalizeEnumsInArray(elem)
                    else -> elem
                }
            }
        )
    }

    fun buildTemplateJson(withHints: Boolean = false): String {
        if (!withHints) {
            return """
{
  "manifest": {
    "format": "urbex-t-db",
    "version": "1.0.0",
    "exportedAt": 1795000000000,
    "deviceNodeId": "!a48f20c1",
    "checksum": "manual",
    "counts": {
      "objects": 1,
      "meshMessages": 0,
      "baseArticles": 1,
      "archiveItems": 0
    }
  },
  "payload": {
    "objects": [
      {
        "id": "obj-tyumen-01",
        "title": "ТЭЦ-1 · Заброшенный мазутный корпус",
        "category": "industrial",
        "subtype": "ТЭЦ / котельная",
        "icon": "boiler",
        "status": "verified",
        "lat": 57.15224,
        "lon": 65.52723,
        "elevationM": 76,
        "description": "Кирпичный производственный корпус, сохранились мостовые краны и техническая галерея.",
        "accessNotes": "Подход со стороны старой ж/д ветки, забор частично отсутствует.",
        "hazards": [
          "Открытые люки",
          "Ржавые перекрытия"
        ],
        "photoUrl": "https://example.com/photo-1.jpg",
        "photos": [
          "https://example.com/photo-1.jpg",
          "https://example.com/photo-2.jpg"
        ],
        "securityLevel": "low",
        "entryDifficulty": "moderate",
        "floorsOrDepth": "3 этажа + подвал -4 м",
        "customParams": [
          { "key": "Год постройки", "value": "1962" },
          { "key": "O2", "value": "20.8%" }
        ],
        "zone": [
          { "lat": 57.1525, "lon": 65.5268 },
          { "lat": 57.1525, "lon": 65.5277 },
          { "lat": 57.1519, "lon": 65.5277 },
          { "lat": 57.1519, "lon": 65.5268 }
        ],
        "zoneColor": "#38BDF8",
        "zoneStyle": "dashed",
        "zonePattern": "hatch",
        "zoneHatched": true,
        "createdAt": 1795000000000,
        "updatedAt": 1795000000000,
        "authorNodeId": "!a48f20c1"
      }
    ],
    "meshMessages": [],
    "baseArticles": [
      {
        "id": "art-manual-01",
        "title": "Секторная сводка по региону",
        "section": "safety",
        "summary": "Краткая памятка по объекту и точкам эвакуации.",
        "content": "Проверять перекрытия, фиксировать аварийные выходы на карте.",
        "updatedAt": 1795000000000,
        "pinned": true
      }
    ],
    "archiveItems": []
  }
}
            """.trimIndent()
        }

        return """
/* ============================================================================
 * ПРАВИЛА СОЗДАНИЯ БАЗЫ ДАННЫХ URBEX-T (.urbexdb v1.0.0)
 * ============================================================================
 * 1. Кодировка файла: UTF-8, расширение: .urbexdb (поддерживается в Web и Android).
 * 2. Синтаксис: стандартный JSON + строчные // и блочные комментарии (удаляются при импорте).
 *    НЕ оставляйте лишних запятых после последнего поля перед "}" или "]".
 * 3. Контрольная сумма: для ручных файлов и генераторов ставьте "checksum": "manual".
 *    При импорте приложение само пересчитает хэш FNV-1a+DJB2 и счётчики counts.
 * 4. Обязательные поля объекта в payload.objects[]:
 *    - "title" (строка) — отображаемое название точки;
 *    - "lat", "lon" (числа WGS-84) — широта [-90..90] и долгота [-180..180].
 *    Остальные поля имеют безопасные значения по умолчанию (id сгенерируется сам).
 * 5. Фотографии: можно указать обложку "photoUrl" / "photoUri" И/ИЛИ массив "photos": [...].
 *    Допускаются HTTPS-ссылки, локальные URI или Data URI (data:image/jpeg;base64,...).
 * 6. Зона на карте ("zone"): массив >= 3 точек {"lat": ..., "lon": ...} по контуру.
 *    Можно задать цвет ("zoneColor": "#RRGGBB"), стиль ("zoneStyle") и штриховку ("zoneHatched").
 * 7. Время ("exportedAt", "createdAt", "updatedAt"): Unix timestamp в миллисекундах (ms).
 * 8. Дополнительные таблицы: "baseArticles", "archiveItems", "meshMessages", "profile",
 *    "settings" необязательны — при отсутствии подставятся безопасные значения по умолчанию.
 * ============================================================================ */
{
  // Метаданные контейнера .urbexdb — обязательный заголовок файла
  "manifest": {
    "format": "urbex-t-db",          // Обязательный идентификатор формата (не менять)
    "version": "1.0.0",              // Версия схемы (поддерживаются все 1.x)
    "exportedAt": 1795000000000,     // Время сборки файла (Unix ms)
    "deviceNodeId": "!a48f20c1",     // Meshtastic HEX ID автора/терминала (формат !<8 hex>)
    "checksum": "manual",            // "manual" отключает строгую сверку и включает авто-хэш при импорте
    "counts": {                      // Сводные счётчики таблиц (при импорте уточняются автоматически)
      "objects": 1,
      "meshMessages": 0,
      "baseArticles": 1,
      "archiveItems": 0
    }
  },

  // Основные таблицы базы данных
  "payload": {
    // Реестр объектов на карте и в каталоге
    "objects": [
      {
        "id": "obj-tyumen-01",       // Уникальный ID объекта (используется при слиянии без дублей)
        "title": "ТЭЦ-1 · Заброшенный мазутный корпус", // Название объекта (ОБЯЗАТЕЛЬНО)
        "category": "industrial",    // Категория: industrial | bunker | civilian | infrastructure | underground | military | energy | medical | transport | hydro | mining | ruins
        "subtype": "ТЭЦ / котельная", // Подтип сооружения (опционально, произвольная строка или пресет категории)
        "icon": "boiler",            // Иконка объекта (72 варианта): factory | bunker | building | tower | tunnel | warehouse | power | boiler | water | fuel | radar | radiation | hazard | skull | observation | gate | crane | hospital | airfield | port | shaft | workshop | camera | pin | military | silo | lab | biohazard | antenna | train | bridge | dam | monument | school | hotel | ruins | shelter | fire | compass | stash | substation | cooling | cistern | elevator | hangar | checkpoint | barracks | range | searchlight | munition | metro | vent | drainage | catacomb | vault | depot | bridge_rail | pier | lighthouse | quarry | mine_cart | oil_rig | pipeline | morgue | sanatorium | church | castle | theater | stadium | mall | generator | evac
        "status": "verified",        // Статус: verified | unverified | contested | hazardous
        "lat": 57.15224,             // Широта WGS-84 (ОБЯЗАТЕЛЬНО, число)
        "lon": 65.52723,             // Долгота WGS-84 (ОБЯЗАТЕЛЬНО, число)
        "elevationM": 76,            // Высота над уровнем моря в метрах (по умолчанию 0)
        "description": "Кирпичный производственный корпус, сохранились мостовые краны и техническая галерея.", // Подробное описание состояния
        "accessNotes": "Подход со стороны старой ж/д ветки, забор частично отсутствует.", // Маршрут подхода и точки входа
        "hazards": [                 // Список опасностей и рисков (массив строк)
          "Открытые люки",
          "Ржавые перекрытия"
        ],
        "photoUrl": "https://example.com/photo-1.jpg", // Главное фото / обложка (совместимо с Android "photoUri")
        "photos": [                  // Галерея фото объекта (первое фото становится обложкой, остальные в карусели)
          "https://example.com/photo-1.jpg",
          "https://example.com/photo-2.jpg"
        ],
        "securityLevel": "low",      // Охрана: none | low | medium | high
        "entryDifficulty": "moderate", // Доступ: easy | moderate | hard | technical
        "floorsOrDepth": "3 этажа + подвал -4 м", // Этажность или глубина (опционально)
        "customParams": [            // Произвольные полевые замеры и характеристики {key, value}
          { "key": "Год постройки", "value": "1962" },
          { "key": "O2", "value": "20.8%" }
        ],
        "zone": [                    // Полигон зоны объекта на карте (минимум 3 вершины)
          { "lat": 57.1525, "lon": 65.5268 },
          { "lat": 57.1525, "lon": 65.5277 },
          { "lat": 57.1519, "lon": 65.5277 },
          { "lat": 57.1519, "lon": 65.5268 }
        ],
        "zoneColor": "#38BDF8",      // Цвет зоны в HEX (#RRGGBB). Удалите поле или null — цвет возьмётся по статусу
        "zoneStyle": "dashed",       // Стиль границы: solid | dashed | dotted | dash_dot | long_dash | double_line | bold | glow | outline | dashed_outline
        "zonePattern": "hatch",      // Узор заливки: none | hatch | cross_hatch | grid | dots | vertical | horizontal | hazard_stripes
        "zoneHatched": true,         // Совместимость со старыми версиями (true если zonePattern != "none")
        "createdAt": 1795000000000,  // Дата создания записи (Unix ms)
        "updatedAt": 1795000000000,  // Дата последнего изменения (Unix ms, влияет на стратегию слияния keep_newer)
        "authorNodeId": "!a48f20c1"  // ID узла, добавившего объект
      }
    ],

    // Журнал сообщений Meshtastic (для ручных баз обычно оставляют [])
    "meshMessages": [],

    // База знаний сектора (инструкции, сводки, памятки)
    "baseArticles": [
      {
        "id": "art-manual-01",       // Уникальный ID статьи
        "title": "Секторная сводка по региону", // Заголовок статьи
        "section": "safety",         // Раздел: safety | navigation | comms | architecture | field_note
        "summary": "Краткая памятка по объекту и точкам эвакуации.", // Короткий анонс для списка
        "content": "Проверять перекрытия, фиксировать аварийные выходы на карте.", // Полный текст заметки
        "updatedAt": 1795000000000,  // Время обновления (Unix ms)
        "pinned": true               // Закрепить статью вверху списка (true | false)
      }
    ],

    // Полевой архив (привязанные фото, заметки, документы: type = photo | video | audio | note | document)
    "archiveItems": []
  }
}
        """.trimIndent()
    }

    val SCHEMA_DOCS: List<SchemaSectionDoc> = listOf(
        SchemaSectionDoc(
            section = "manifest (заголовок файла .urbexdb)",
            fields = listOf(
                SchemaFieldDoc("format", "\"urbex-t-db\"", "Обязательный идентификатор формата."),
                SchemaFieldDoc("version", "\"1.0.0\"", "Мажорная версия схемы (поддерживается v1.x)."),
                SchemaFieldDoc("checksum", "\"uxdb-...\" | \"manual\"", "Хэш FNV-1a+DJB2 или \"manual\" при ручном создании файла."),
                SchemaFieldDoc("deviceNodeId", "string", "ID узла-источника, например \"!a48f20c1\"."),
                SchemaFieldDoc("exportedAt", "number (ms)", "Unix timestamp в миллисекундах.")
            )
        ),
        SchemaSectionDoc(
            section = "payload.objects[] (точки объектов)",
            fields = listOf(
                SchemaFieldDoc("id, title", "string", "Уникальный ID и название объекта (обязательно)."),
                SchemaFieldDoc("lat, lon, elevationM", "number", "Координаты WGS-84 (-90..90, -180..180) и высота в метрах."),
                SchemaFieldDoc("category", "enum", "industrial | bunker | civilian | infrastructure | underground | military | energy | medical | transport | hydro | mining | ruins"),
                SchemaFieldDoc("subtype", "string?", "Необязательный подтип сооружения (например: \"Бомбоубежище ЗС ГО\", \"Градирня\")."),
                SchemaFieldDoc("icon", "enum?", "Иконка маркера (72 вида): factory, bunker, building, tower, tunnel, warehouse, power, boiler, water, fuel, radar, radiation, hazard, skull, observation, gate, crane, hospital, airfield, port, shaft, workshop, camera, pin, military, silo, lab, biohazard, antenna, train, bridge, dam, monument, school, hotel, ruins, shelter, fire, compass, stash, substation, cooling, cistern, elevator, hangar, checkpoint, barracks, range, searchlight, munition, metro, vent, drainage, catacomb, vault, depot, bridge_rail, pier, lighthouse, quarry, mine_cart, oil_rig, pipeline, morgue, sanatorium, church, castle, theater, stadium, mall, generator, evac."),
                SchemaFieldDoc("status", "enum", "verified | unverified | contested | hazardous"),
                SchemaFieldDoc("securityLevel", "enum", "none | low | medium | high"),
                SchemaFieldDoc("entryDifficulty", "enum", "easy | moderate | hard | technical"),
                SchemaFieldDoc("floorsOrDepth", "string?", "Например: \"-14 м / 2 яруса\"."),
                SchemaFieldDoc("photoUri / photoUrl", "string?", "Главное фото (обложка): https://... или локальный URI/dataURL."),
                SchemaFieldDoc("photos / photoUrls", "string[]?", "Несколько фотографий объекта (обложка + доп. снимки)."),
                SchemaFieldDoc("hazards", "string[]", "Список опасностей и факторов риска."),
                SchemaFieldDoc("customParams", "{key, value}[]", "Доп. замеры (газы, радиация, год и т. д.)."),
                SchemaFieldDoc("zone", "{lat, lon}[]", "Полигон периметра объекта (≥3 точек)."),
                SchemaFieldDoc("zoneColor", "string?", "Ручной HEX-цвет зоны (#RRGGBB) или null (авто по статусу)."),
                SchemaFieldDoc("zoneStyle", "enum", "Стиль границы зоны: solid | dashed | dotted | dash_dot | long_dash | double_line | bold | glow | outline | dashed_outline."),
                SchemaFieldDoc("zonePattern", "enum?", "Узор заливки зоны: none | hatch | cross_hatch | grid | dots | vertical | horizontal | hazard_stripes."),
                SchemaFieldDoc("zoneHatched", "boolean?", "Совместимость: включает диагональную штриховку, если zonePattern не задан.")
            )
        ),
        SchemaSectionDoc(
            section = "Прочие таблицы payload",
            fields = listOf(
                SchemaFieldDoc("baseArticles[]", "BaseArticle[]", "Статьи базы знаний (section: safety | navigation | comms | architecture | field_note)."),
                SchemaFieldDoc("archiveItems[]", "ArchiveItem[]", "Фото, заметки и документы (type: photo | video | audio | note | document)."),
                SchemaFieldDoc("meshMessages[]", "MeshMessage[]", "Журнал пакетов Meshtastic (можно оставить [])."),
                SchemaFieldDoc("profile, settings", "object?", "Необязательны для ручных файлов — подставятся значения по умолчанию.")
            )
        )
    )
}

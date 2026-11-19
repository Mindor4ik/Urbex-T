package com.urbext.core.backup

import com.urbext.domain.model.UrbexDbBundle
import com.urbext.domain.model.UrbexDbCounts
import com.urbext.domain.model.UrbexDbManifest
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.model.normalizeZoneColor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    private val ENUM_KEYS = setOf(
        "category",
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
        "zoneStyle"
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
        val bundle = UrbexDbBundle(
            manifest = UrbexDbManifest(
                format = FORMAT_ID,
                version = SCHEMA_VERSION,
                exportedAt = exportedAt,
                deviceNodeId = deviceNodeId,
                checksum = computeChecksum(payload),
                counts = UrbexDbCounts(
                    objects = payload.objects.size,
                    meshMessages = payload.meshMessages.size,
                    baseArticles = payload.baseArticles.size,
                    archiveItems = payload.archiveItems.size
                )
            ),
            payload = payload
        )
        return json.encodeToString(bundle)
    }

    fun parseAndVerify(raw: String): ParseUrbexDbResult {
        val root = try {
            json.parseToJsonElement(raw).jsonObject
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
            val strictBundle = runCatching { json.decodeFromString<UrbexDbBundle>(raw) }.getOrNull()
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
        if (!normalized.containsKey("category")) {
            normalized["category"] = JsonPrimitive("INDUSTRIAL")
        }
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
        if (!normalized.containsKey("zoneStyle")) {
            normalized["zoneStyle"] = JsonPrimitive("SOLID")
        }
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
            if (k in ENUM_KEYS && v is JsonPrimitive && v.isString) {
                map[k] = JsonPrimitive(v.content.uppercase(Locale.US))
            } else {
                map[k] = v
            }
        }
        return JsonObject(map)
    }

    fun buildTemplateJson(): String = """
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
                SchemaFieldDoc("category", "enum", "industrial | bunker | civilian | infrastructure | underground"),
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
                SchemaFieldDoc("zoneStyle", "enum", "Стиль границы зоны: solid | dashed | dotted | outline."),
                SchemaFieldDoc("zoneHatched", "boolean?", "Включает диагональную штриховку внутри полигона (true | false).")
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

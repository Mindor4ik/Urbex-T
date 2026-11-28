package com.urbext.core.geo

import com.urbext.domain.model.UrbexObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

enum class MapSearchKind {
    COORD,
    OBJECT,
    CITY,
    PLACE
}

enum class MapSearchSource {
    LOCAL,
    ONLINE
}

data class MapSearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val kind: MapSearchKind,
    val source: MapSearchSource,
    val recommendedZoom: Double,
    val objectId: String? = null
)

private data class OfflineCityEntry(
    val id: String,
    val name: String,
    val region: String,
    val lat: Double,
    val lon: Double,
    val aliases: List<String>,
    val popularRank: Int? = null
)

object MapPlaceSearch {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val coordRegex = Regex(
        """^\s*([+-]?\d+(?:[.,]\d+)?)\s*°?\s*([NSСЮ]?)\s*[,;\s]+\s*([+-]?\d+(?:[.,]\d+)?)\s*°?\s*([EWВЗ]?)\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val cityAddressTypes = setOf(
        "city",
        "town",
        "village",
        "hamlet",
        "municipality",
        "state",
        "region",
        "administrative",
        "county"
    )

    private val offlineCities = listOf(
        OfflineCityEntry("moscow", "Москва", "Россия", 55.7558, 37.6173, listOf("moscow", "msk", "мск", "moskva"), 1),
        OfflineCityEntry("spb", "Санкт-Петербург", "Россия", 59.9343, 30.3351, listOf("spb", "спб", "питер", "ленинград", "saint petersburg", "st petersburg", "piter"), 2),
        OfflineCityEntry("kazan", "Казань", "Татарстан, Россия", 55.7961, 49.1064, listOf("kazan", "кзн"), 3),
        OfflineCityEntry("ekb", "Екатеринбург", "Свердловская обл., Россия", 56.8389, 60.6057, listOf("ekb", "екб", "yekaterinburg", "свердловск"), 4),
        OfflineCityEntry("nsk", "Новосибирск", "Новосибирская обл., Россия", 55.0084, 82.9357, listOf("nsk", "нск", "novosibirsk"), 5),
        OfflineCityEntry("nnov", "Нижний Новгород", "Нижегородская обл., Россия", 56.3269, 44.0059, listOf("nizhny novgorod", "nn", "нн", "горький")),
        OfflineCityEntry("samara", "Самара", "Самарская обл., Россия", 53.1959, 50.1002, listOf("samara", "куйбышев")),
        OfflineCityEntry("krasnodar", "Краснодар", "Краснодарский край, Россия", 45.0355, 38.9753, listOf("krasnodar", "крд")),
        OfflineCityEntry("sochi", "Сочи", "Краснодарский край, Россия", 43.5855, 39.7231, listOf("sochi")),
        OfflineCityEntry("rostov", "Ростов-на-Дону", "Ростовская обл., Россия", 47.2357, 39.7015, listOf("rostov", "рнд", "rostov-on-don")),
        OfflineCityEntry("ufa", "Уфа", "Башкортостан, Россия", 54.7388, 55.9721, listOf("ufa")),
        OfflineCityEntry("krasnoyarsk", "Красноярск", "Красноярский край, Россия", 56.0153, 92.8932, listOf("krasnoyarsk")),
        OfflineCityEntry("voronezh", "Воронеж", "Воронежская обл., Россия", 51.672, 39.1843, listOf("voronezh", "врн")),
        OfflineCityEntry("perm", "Пермь", "Пермский край, Россия", 58.0105, 56.2502, listOf("perm")),
        OfflineCityEntry("volgograd", "Волгоград", "Волгоградская обл., Россия", 48.708, 44.5133, listOf("volgograd", "сталинград")),
        OfflineCityEntry("chelyabinsk", "Челябинск", "Челябинская обл., Россия", 55.1644, 61.4368, listOf("chelyabinsk", "члб")),
        OfflineCityEntry("omsk", "Омск", "Омская обл., Россия", 54.9885, 73.3242, listOf("omsk")),
        OfflineCityEntry("tyumen", "Тюмень", "Тюменская обл., Россия", 57.1553, 65.5619, listOf("tyumen")),
        OfflineCityEntry("saratov", "Саратов", "Саратовская обл., Россия", 51.5336, 46.0343, listOf("saratov")),
        OfflineCityEntry("tolyatti", "Тольятти", "Самарская обл., Россия", 53.5078, 49.4204, listOf("tolyatti")),
        OfflineCityEntry("izhevsk", "Ижевск", "Удмуртия, Россия", 56.8526, 53.2069, listOf("izhevsk")),
        OfflineCityEntry("barnaul", "Барнаул", "Алтайский край, Россия", 53.3548, 83.7698, listOf("barnaul")),
        OfflineCityEntry("irkutsk", "Иркутск", "Иркутская обл., Россия", 52.2869, 104.305, listOf("irkutsk")),
        OfflineCityEntry("khabarovsk", "Хабаровск", "Хабаровский край, Россия", 48.4802, 135.0719, listOf("khabarovsk")),
        OfflineCityEntry("vladivostok", "Владивосток", "Приморский край, Россия", 43.1155, 131.8855, listOf("vladivostok", "вдк")),
        OfflineCityEntry("kaliningrad", "Калининград", "Калининградская обл., Россия", 54.7104, 20.4522, listOf("kaliningrad", "кёнигсберг")),
        OfflineCityEntry("murmansk", "Мурманск", "Мурманская обл., Россия", 68.9707, 33.075, listOf("murmansk")),
        OfflineCityEntry("arkhangelsk", "Архангельск", "Архангельская обл., Россия", 64.5399, 40.5158, listOf("arkhangelsk")),
        OfflineCityEntry("yaroslavl", "Ярославль", "Ярославская обл., Россия", 57.6261, 39.8845, listOf("yaroslavl")),
        OfflineCityEntry("tula", "Тула", "Тульская обл., Россия", 54.1931, 37.6173, listOf("tula")),
        OfflineCityEntry("tver", "Тверь", "Тверская обл., Россия", 56.8587, 35.9176, listOf("tver")),
        OfflineCityEntry("ryazan", "Рязань", "Рязанская обл., Россия", 54.6292, 39.7364, listOf("ryazan")),
        OfflineCityEntry("kaluga", "Калуга", "Калужская обл., Россия", 54.5138, 36.2612, listOf("kaluga")),
        OfflineCityEntry("vladimir", "Владимир", "Владимирская обл., Россия", 56.1291, 40.4066, listOf("vladimir")),
        OfflineCityEntry("smolensk", "Смоленск", "Смоленская обл., Россия", 54.7826, 32.0453, listOf("smolensk")),
        OfflineCityEntry("pskov", "Псков", "Псковская обл., Россия", 57.8193, 28.3324, listOf("pskov")),
        OfflineCityEntry("veliky-novgorod", "Великий Новгород", "Новгородская обл., Россия", 58.5228, 31.2698, listOf("veliky novgorod")),
        OfflineCityEntry("petrozavodsk", "Петрозаводск", "Карелия, Россия", 61.7873, 34.3543, listOf("petrozavodsk")),
        OfflineCityEntry("grozny", "Грозный", "Чечня, Россия", 43.3184, 45.6981, listOf("grozny")),
        OfflineCityEntry("makhachkala", "Махачкала", "Дагестан, Россия", 42.9831, 47.5047, listOf("makhachkala")),
        OfflineCityEntry("sevastopol", "Севастополь", "Крым", 44.6167, 33.5254, listOf("sevastopol")),
        OfflineCityEntry("simferopol", "Симферополь", "Крым", 44.9521, 34.1024, listOf("simferopol")),
        OfflineCityEntry("minsk", "Минск", "Беларусь", 53.9006, 27.559, listOf("minsk")),
        OfflineCityEntry("brest", "Брест", "Беларусь", 52.0976, 23.7341, listOf("brest")),
        OfflineCityEntry("gomel", "Гомель", "Беларусь", 52.4345, 30.9754, listOf("gomel")),
        OfflineCityEntry("almaty", "Алматы", "Казахстан", 43.2389, 76.8897, listOf("almaty", "алма-ата")),
        OfflineCityEntry("astana", "Астана", "Казахстан", 51.1605, 71.4704, listOf("astana", "нур-султан")),
        OfflineCityEntry("kyiv", "Киев", "Украина", 50.4501, 30.5234, listOf("kyiv", "kiev")),
        OfflineCityEntry("odesa", "Одесса", "Украина", 46.4825, 30.7233, listOf("odesa", "odessa")),
        OfflineCityEntry("lviv", "Львов", "Украина", 49.8397, 24.0297, listOf("lviv")),
        OfflineCityEntry("tbilisi", "Тбилиси", "Грузия", 41.7151, 44.8271, listOf("tbilisi")),
        OfflineCityEntry("yerevan", "Ереван", "Армения", 40.1872, 44.5152, listOf("yerevan")),
        OfflineCityEntry("baku", "Баку", "Азербайджан", 40.4093, 49.8671, listOf("baku")),
        OfflineCityEntry("tashkent", "Ташкент", "Узбекистан", 41.2995, 69.2401, listOf("tashkent")),
        OfflineCityEntry("bishkek", "Бишкек", "Кыргызстан", 42.8746, 74.5698, listOf("bishkek")),
        OfflineCityEntry("tallinn", "Таллин", "Эстония", 59.437, 24.7536, listOf("tallinn")),
        OfflineCityEntry("riga", "Рига", "Латвия", 56.9496, 24.1052, listOf("riga")),
        OfflineCityEntry("vilnius", "Вильнюс", "Литва", 54.6872, 25.2797, listOf("vilnius")),
        OfflineCityEntry("warsaw", "Варшава", "Польша", 52.2297, 21.0122, listOf("warsaw", "warszawa")),
        OfflineCityEntry("berlin", "Берлин", "Германия", 52.52, 13.405, listOf("berlin")),
        OfflineCityEntry("prague", "Прага", "Чехия", 50.0755, 14.4378, listOf("prague", "praha")),
        OfflineCityEntry("vienna", "Вена", "Австрия", 48.2082, 16.3738, listOf("vienna", "wien")),
        OfflineCityEntry("paris", "Париж", "Франция", 48.8566, 2.3522, listOf("paris")),
        OfflineCityEntry("london", "Лондон", "Великобритания", 51.5072, -0.1276, listOf("london")),
        OfflineCityEntry("istanbul", "Стамбул", "Турция", 41.0082, 28.9784, listOf("istanbul")),
        OfflineCityEntry("tokyo", "Токио", "Япония", 35.6762, 139.6503, listOf("tokyo")),
        OfflineCityEntry("new-york", "Нью-Йорк", "США", 40.7128, -74.006, listOf("new york", "nyc"))
    )

    private fun normalizeToken(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(Regex("\\s+"), " ")
    }

    fun parseCoordinates(query: String): Pair<Double, Double>? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val match = coordRegex.matchEntire(trimmed) ?: return null
        val rawLat = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val latHem = match.groupValues[2].uppercase(Locale.ROOT)
        val rawLon = match.groupValues[3].replace(',', '.').toDoubleOrNull() ?: return null
        val lonHem = match.groupValues[4].uppercase(Locale.ROOT)

        val lat = if (latHem == "S" || latHem == "Ю") -kotlin.math.abs(rawLat) else rawLat
        val lon = if (lonHem == "W" || lonHem == "З") -kotlin.math.abs(rawLon) else rawLon
        if (!GeoUtils.isValidCoordinate(lat, lon)) return null
        return lat to lon
    }

    fun searchOffline(
        query: String,
        objects: List<UrbexObject>,
        limit: Int = 8
    ): List<MapSearchResult> {
        val normalized = normalizeToken(query)
        if (normalized.isEmpty()) {
            return offlineCities
                .filter { it.popularRank != null }
                .sortedBy { it.popularRank }
                .take(limit.coerceAtMost(5))
                .map { city ->
                    MapSearchResult(
                        id = "city-${city.id}",
                        title = city.name,
                        subtitle = city.region,
                        lat = city.lat,
                        lon = city.lon,
                        kind = MapSearchKind.CITY,
                        source = MapSearchSource.LOCAL,
                        recommendedZoom = 11.8
                    )
                }
        }

        val results = mutableListOf<MapSearchResult>()
        val coord = parseCoordinates(query)
        if (coord != null) {
            results.add(
                MapSearchResult(
                    id = "coord-${coord.first}-${coord.second}",
                    title = String.format(Locale.US, "%.5f, %.5f", coord.first, coord.second),
                    subtitle = "Перейти к координатам",
                    lat = coord.first,
                    lon = coord.second,
                    kind = MapSearchKind.COORD,
                    source = MapSearchSource.LOCAL,
                    recommendedZoom = 14.5
                )
            )
        }

        val scoredObjects = objects.mapNotNull { obj ->
            val titleNorm = normalizeToken(obj.title)
            val subNorm = normalizeToken(obj.subtype)
            val catNorm = normalizeToken(obj.category.label)
            val score = when {
                titleNorm == normalized -> 120
                titleNorm.startsWith(normalized) -> 100
                titleNorm.contains(normalized) -> 80
                subNorm.contains(normalized) || catNorm.contains(normalized) -> 60
                else -> 0
            }
            if (score <= 0) null
            else score to MapSearchResult(
                id = "obj-${obj.id}",
                title = obj.title,
                subtitle = if (obj.subtype.isNotBlank()) "${obj.category.label} · ${obj.subtype}" else obj.category.label,
                lat = obj.lat,
                lon = obj.lon,
                kind = MapSearchKind.OBJECT,
                source = MapSearchSource.LOCAL,
                recommendedZoom = 15.8,
                objectId = obj.id
            )
        }.sortedByDescending { it.first }

        val scoredCities = offlineCities.mapNotNull { city ->
            val nameNorm = normalizeToken(city.name)
            val regionNorm = normalizeToken(city.region)
            val aliasesNorm = city.aliases.map { normalizeToken(it) }
            val baseScore = when {
                nameNorm == normalized || aliasesNorm.contains(normalized) -> 130
                nameNorm.startsWith(normalized) || aliasesNorm.any { it.startsWith(normalized) } -> 105
                nameNorm.contains(normalized) || aliasesNorm.any { it.contains(normalized) } -> 82
                regionNorm.contains(normalized) -> 50
                else -> 0
            }
            if (baseScore <= 0) null
            else (baseScore + (city.popularRank?.let { 6 - it } ?: 0)) to MapSearchResult(
                id = "city-${city.id}",
                title = city.name,
                subtitle = city.region,
                lat = city.lat,
                lon = city.lon,
                kind = MapSearchKind.CITY,
                source = MapSearchSource.LOCAL,
                recommendedZoom = 11.8
            )
        }.sortedByDescending { it.first }

        scoredObjects.take(4).forEach { results.add(it.second) }
        for ((_, cityResult) in scoredCities) {
            if (results.size >= limit) break
            results.add(cityResult)
        }
        return results.take(limit)
    }

    fun fetchOnlineNominatim(query: String): List<MapSearchResult> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return runCatching {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&accept-language=ru&limit=6&q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "UrbexT-Android/1.0")
            }
            try {
                if (conn.responseCode !in 200..299) return emptyList()
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseNominatimJson(body)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(emptyList())
    }

    fun parseNominatimJson(body: String): List<MapSearchResult> {
        val array: JsonArray = runCatching { jsonParser.parseToJsonElement(body).jsonArray }
            .getOrNull() ?: return emptyList()
        val results = mutableListOf<MapSearchResult>()
        for (element in array) {
            val obj: JsonObject = runCatching { element.jsonObject }.getOrNull() ?: continue
            val lat = obj["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: continue
            val lon = obj["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: continue
            if (!GeoUtils.isValidCoordinate(lat, lon)) continue

            val displayName = obj["display_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val explicitName = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val parts = displayName.split(',').map { it.trim() }.filter { it.isNotEmpty() }

            val title = explicitName.ifEmpty {
                parts.firstOrNull() ?: String.format(Locale.US, "%.4f, %.4f", lat, lon)
            }
            val titleNorm = normalizeToken(title)
            val subtitle = parts
                .filterIndexed { idx, part -> !(idx == 0 && normalizeToken(part) == titleNorm) }
                .take(3)
                .joinToString(", ")
                .ifEmpty { "OpenStreetMap" }

            val addrType = obj["addresstype"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val objType = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val isCity = cityAddressTypes.contains(addrType) || cityAddressTypes.contains(objType)
            val placeId = obj["place_id"]?.jsonPrimitive?.contentOrNull ?: "$lat-$lon"

            results.add(
                MapSearchResult(
                    id = "osm-$placeId",
                    title = title,
                    subtitle = subtitle,
                    lat = lat,
                    lon = lon,
                    kind = if (isCity) MapSearchKind.CITY else MapSearchKind.PLACE,
                    source = MapSearchSource.ONLINE,
                    recommendedZoom = if (isCity) 11.8 else 14.5
                )
            )
        }
        return results
    }

    fun mergeResults(
        local: List<MapSearchResult>,
        online: List<MapSearchResult>,
        limit: Int = 9
    ): List<MapSearchResult> {
        val merged = local.toMutableList()
        for (remote in online) {
            val remoteTitle = normalizeToken(remote.title)
            val duplicate = merged.any { existing ->
                normalizeToken(existing.title) == remoteTitle &&
                    GeoUtils.haversineDistanceMeters(existing.lat, existing.lon, remote.lat, remote.lon) < 15_000.0
            }
            if (!duplicate) {
                merged.add(remote)
            }
            if (merged.size >= limit) break
        }
        return merged.take(limit)
    }
}

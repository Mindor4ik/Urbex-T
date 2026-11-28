package com.urbext.core

import com.urbext.core.backup.CloudSyncCrypto
import com.urbext.core.backup.ParseUrbexDbResult
import com.urbext.core.backup.UrbexDbSerializer
import com.urbext.core.geo.GeoUtils
import com.urbext.core.geo.MapPlaceSearch
import com.urbext.core.geo.MapSearchKind
import com.urbext.core.geo.MapSearchSource
import com.urbext.core.mesh.MeshtasticCodec
import com.urbext.core.radio.RadioPlanUtils
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.CloudSyncConfig
import com.urbext.domain.model.CloudSyncProvider
import com.urbext.domain.model.CoordinateFormat
import com.urbext.domain.model.MeshPacketKind
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.ObjectIcon
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.PdaOrientation
import com.urbext.domain.model.RadioChannelPreset
import com.urbext.domain.model.RadioEncryptionType
import com.urbext.domain.model.RadioModulation
import com.urbext.domain.model.RadioPowerLevel
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.ToneSquelchMode
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile
import com.urbext.domain.model.ZonePattern
import com.urbext.domain.model.ZoneStyle
import com.urbext.domain.model.allPhotos
import com.urbext.domain.model.defaultIcon
import com.urbext.domain.model.normalizeObjectCategory
import com.urbext.domain.model.normalizeObjectIcon
import com.urbext.domain.model.normalizeObjectSubtype
import com.urbext.domain.model.normalizeZonePattern
import com.urbext.domain.model.normalizeZoneStyle
import com.urbext.domain.model.subtypePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {
    @Test
    fun geoValidationAndDistanceWork() {
        assertTrue(GeoUtils.isValidCoordinate(55.7512, 37.6184))
        assertFalse(GeoUtils.isValidCoordinate(95.0, 37.6184))
        val dist = GeoUtils.haversineDistanceMeters(55.7512, 37.6184, 55.7602, 37.6184)
        assertTrue(dist in 990.0..1010.0)
        assertTrue(GeoUtils.formatCoordinates(55.7512, 37.6184, CoordinateFormat.DD).contains("55.75120"))
        assertTrue(GeoUtils.formatCoordinates(55.7512, 37.6184, CoordinateFormat.UTM).contains("37U"))
        val masked = GeoUtils.formatDisplayCoordinates(55.7512, 37.6184, CoordinateFormat.DD, masked = true)
        assertEquals("••.•••••° · ••.•••••° [OPSEC]", masked)
    }

    @Test
    fun urbexDbRoundtripAndTamperDetectionWork() {
        val payload = UrbexDbPayload(
            objects = listOf(
                UrbexObject(
                    id = "obj-1",
                    title = "Градирня ТЭЦ-4",
                    category = ObjectCategory.INDUSTRIAL,
                    status = ReliabilityStatus.VERIFIED,
                    lat = 55.7512,
                    lon = 37.6184,
                    elevationM = 148,
                    description = "Бетонная башня",
                    accessNotes = "Восточный склон",
                    hazards = listOf("Арматура"),
                    zoneColor = "#38BDF8",
                    zoneStyle = ZoneStyle.DASHED,
                    zonePattern = ZonePattern.HATCH,
                    zoneHatched = true,
                    createdAt = 1000L,
                    updatedAt = 2000L,
                    authorNodeId = "!a48f20c1"
                )
            ),
            meshMessages = emptyList(),
            baseArticles = emptyList(),
            archiveItems = emptyList(),
            profile = UserProfile(
                callsign = "Сокол-1",
                nodeId = "!a48f20c1",
                role = OperatorRole.SCOUT,
                emergencyContact = "",
                bio = "",
                updatedAt = 1000L
            ),
            settings = AppSettings(
                editorEnabled = true,
                pdaModeEnabled = true,
                pdaOrientation = PdaOrientation.LANDSCAPE,
                radioChannels = listOf(
                    RadioChannelPreset(
                        id = "ch-sec",
                        name = "Секретный",
                        rxFreqMhz = "433.9250",
                        txFreqMhz = "433.9250",
                        encryptionType = RadioEncryptionType.AES256,
                        encryptionKey = "secret-radio-key-should-be-stripped"
                    )
                ),
                cloudSync = CloudSyncConfig(
                    enabled = true,
                    provider = CloudSyncProvider.GITHUB_GIST,
                    authToken = "ghp_super_secret_token",
                    username = "secret-user",
                    encryptionPassphrase = "my-e2ee-passphrase"
                ),
                updatedAt = 1000L
            )
        )

        val serialized = UrbexDbSerializer.serialize(payload, "!a48f20c1", 5000L)
        assertFalse(serialized.contains("ghp_super_secret_token"))
        assertFalse(serialized.contains("secret-user"))
        assertFalse(serialized.contains("my-e2ee-passphrase"))
        assertTrue(serialized.contains("secret-radio-key-should-be-stripped"))
        val valid = UrbexDbSerializer.parseAndVerify(serialized)
        assertTrue(valid is ParseUrbexDbResult.Valid)
        val validBundle = (valid as ParseUrbexDbResult.Valid).bundle
        val decodedObj = validBundle.payload.objects.first()
        assertEquals("#38BDF8", decodedObj.zoneColor)
        assertEquals(ZoneStyle.DASHED, decodedObj.zoneStyle)
        assertEquals(ZonePattern.HATCH, decodedObj.zonePattern)
        assertTrue(decodedObj.zoneHatched)
        val decoded = validBundle.payload.settings
        assertTrue(decoded.editorEnabled)
        assertTrue(decoded.pdaModeEnabled)
        assertEquals(PdaOrientation.LANDSCAPE, decoded.pdaOrientation)
        assertEquals("", decoded.cloudSync.authToken)
        assertEquals("", decoded.cloudSync.username)
        assertEquals("", decoded.cloudSync.encryptionPassphrase)
        assertEquals("secret-radio-key-should-be-stripped", decoded.radioChannels.first().encryptionKey)

        val tampered = serialized.replace("Градирня ТЭЦ-4", "Подменённый объект")
        val invalid = UrbexDbSerializer.parseAndVerify(tampered)
        assertTrue(invalid is ParseUrbexDbResult.Invalid)
    }

    @Test
    fun manualTemplateWithPhotoUrlAndLowercaseEnumsParses() {
        val template = UrbexDbSerializer.buildTemplateJson()
        val res = UrbexDbSerializer.parseAndVerify(template)
        assertTrue(res is ParseUrbexDbResult.Valid)
        val valid = res as ParseUrbexDbResult.Valid
        assertTrue(valid.warnings.isNotEmpty())
        assertEquals(1, valid.bundle.payload.objects.size)
        val obj = valid.bundle.payload.objects.first()
        assertEquals(ObjectCategory.INDUSTRIAL, obj.category)
        assertEquals(ReliabilityStatus.VERIFIED, obj.status)
        assertEquals("#38BDF8", obj.zoneColor)
        assertEquals(ZoneStyle.DASHED, obj.zoneStyle)
        assertTrue(obj.zoneHatched)
        assertEquals("https://example.com/photo-1.jpg", obj.photoUri)
        assertEquals(
            listOf("https://example.com/photo-1.jpg", "https://example.com/photo-2.jpg"),
            obj.allPhotos()
        )
        assertTrue(valid.bundle.manifest.checksum.startsWith("uxdb-"))
    }

    @Test
    fun commentedTemplateAndUrlsPreserveSafely() {
        val rawWithComments = """
            /* top block comment */
            {
              // line comment
              "url": "https://example.com/a//b/*keep*/",
              "quote": "hello \"// keep\""
            }
        """.trimIndent()
        val stripped = UrbexDbSerializer.stripJsonComments(rawWithComments)
        assertTrue(stripped.contains("https://example.com/a//b/*keep*/"))
        assertFalse(stripped.contains("top block comment"))
        assertFalse(stripped.contains("// line comment"))

        val hinted = UrbexDbSerializer.buildTemplateJson(withHints = true)
        assertTrue(hinted.contains("ПРАВИЛА СОЗДАНИЯ БАЗЫ ДАННЫХ"))
        val res = UrbexDbSerializer.parseAndVerify(hinted)
        assertTrue(res is ParseUrbexDbResult.Valid)
        val valid = res as ParseUrbexDbResult.Valid
        assertEquals(1, valid.bundle.payload.objects.size)
        assertEquals(1, valid.bundle.payload.baseArticles.size)
        assertEquals("https://example.com/photo-1.jpg", valid.bundle.payload.objects.first().photoUri)
    }

    @Test
    fun meshtasticSosFrameStartsWithExpectedPreamble() {
        assertEquals("!a1b20000", MeshtasticCodec.normalizeNodeId("a1b2"))
        val msg = MeshtasticCodec.buildMessage(
            channelId = "ch-sos",
            senderNodeId = "!a48f20c1",
            senderCallsign = "Сокол-1",
            kind = MeshPacketKind.SOS,
            body = "Помощь в секторе Б",
            hopLimit = 7,
            lat = 55.7512,
            lon = 37.6184,
            timestamp = 1700000000000L
        )
        assertTrue(msg.frameHex.startsWith("94c30307"))
    }

    @Test
    fun radioPlanUtilsFormattingKeygenAndChirpCsvWork() {
        assertEquals("446.00625", RadioPlanUtils.normalizeFrequencyMhz("446,00625"))
        val duplexPos = RadioPlanUtils.computeDuplexOffsetMhz("433.50000", "438.50000")
        assertEquals("+", duplexPos.duplex)
        assertEquals("5.00000", duplexPos.offsetMhz)

        val simplex = RadioPlanUtils.computeDuplexOffsetMhz("446.00625", "446.00625")
        assertEquals("", simplex.duplex)
        assertEquals("0.00000", simplex.offsetMhz)

        val aes256 = RadioPlanUtils.generateRadioCryptoKey(RadioEncryptionType.AES256)
        assertEquals(64, aes256.length)
        assertTrue(aes256.all { it in '0'..'9' || it in 'a'..'f' })

        val csv = RadioPlanUtils.buildChirpCsv(
            listOf(
                RadioChannelPreset(
                    id = "r1",
                    name = "PMR1",
                    rxFreqMhz = "446.00625",
                    txFreqMhz = "446.00625",
                    mode = RadioModulation.NFM,
                    bandwidthKhz = "12.5",
                    toneMode = ToneSquelchMode.CTCSS,
                    rxTone = "88.5",
                    txTone = "88.5",
                    powerLevel = RadioPowerLevel.LOW
                )
            )
        )
        assertTrue(csv.contains("Location,Name,Frequency,Duplex,Offset,Tone,rToneFreq,cToneFreq,DtcsCode,DtcsPolarity,Mode,TStep,Skip,Comment"))
        assertTrue(csv.contains("PMR1"))
        assertTrue(csv.contains("88.5"))
    }

    @Test
    fun cloudSyncE2eeEncryptDecryptRoundtripWorks() {
        val sampleJson = """{"urbex":"test","count":42,"label":"Сектор-7"}"""
        val encrypted = CloudSyncCrypto.encryptSyncContent(sampleJson, "correct-horse-battery-staple")
        assertTrue(encrypted.contains("urbex-e2ee-v1"))
        assertFalse(encrypted.contains("Сектор-7"))

        val decrypted = CloudSyncCrypto.decryptSyncContent(encrypted, "correct-horse-battery-staple")
        assertEquals(sampleJson, decrypted)

        var failed = false
        try {
            CloudSyncCrypto.decryptSyncContent(encrypted, "wrong-passphrase")
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun expandedObjectCategoriesAndIconsNormalizeAndRoundtrip() {
        assertEquals(12, ObjectCategory.entries.size)
        assertEquals(72, ObjectIcon.entries.size)
        for (cat in ObjectCategory.entries) {
            assertTrue(cat.label.isNotBlank())
            assertTrue(cat.defaultIcon().label.isNotBlank())
            val presets = cat.subtypePresets()
            assertTrue(presets.size >= 4)
            for (preset in presets) {
                assertTrue(preset.label.isNotBlank())
                assertTrue(preset.icon.label.isNotBlank())
            }
        }
        assertEquals(ObjectCategory.MILITARY, normalizeObjectCategory(" military "))
        assertEquals(ObjectCategory.INDUSTRIAL, normalizeObjectCategory("unknown_cat"))
        assertEquals(ObjectIcon.SILO, normalizeObjectIcon(" silo "))
        assertEquals(ObjectIcon.SUBSTATION, normalizeObjectIcon(" substation "))
        assertEquals(ObjectIcon.BRIDGE_RAIL, normalizeObjectIcon("bridge-rail"))
        assertEquals(null, normalizeObjectIcon("bad_icon"))
        assertEquals("Подстанция / ОРУ / ТП", normalizeObjectSubtype("  Подстанция / ОРУ / ТП  "))
        assertEquals(null, normalizeObjectSubtype("   "))

        val jsonInput = """
            {
              "manifest": { "format": "urbex-t-db", "version": "1.0.0", "checksum": "manual" },
              "payload": {
                "objects": [
                  { "id": "c1", "title": "ТП-110 кВ", "category": "energy", "subtype": "  Подстанция / ОРУ / ТП ", "icon": "substation", "lat": 55.75, "lon": 37.62 },
                  { "id": "c2", "title": "Неизвестный тип", "category": "unknown_type", "subtype": "   ", "icon": "invalid", "lat": 55.76, "lon": 37.63 }
                ]
              }
            }
        """.trimIndent()
        val res = UrbexDbSerializer.parseAndVerify(jsonInput)
        assertTrue(res is ParseUrbexDbResult.Valid)
        val objs = (res as ParseUrbexDbResult.Valid).bundle.payload.objects
        assertEquals(ObjectCategory.ENERGY, objs[0].category)
        assertEquals("Подстанция / ОРУ / ТП", objs[0].subtype)
        assertEquals(ObjectIcon.SUBSTATION, objs[0].icon)
        assertEquals(ObjectCategory.INDUSTRIAL, objs[1].category)
        assertEquals(null, objs[1].subtype)
        assertEquals(null, objs[1].icon)
    }

    @Test
    fun expandedZoneStylesAndPatternsNormalizeAndRoundtrip() {
        assertEquals(10, ZoneStyle.entries.size)
        assertEquals(9, ZonePattern.entries.size)
        ZoneStyle.entries.forEach { st ->
            assertTrue(st.label.isNotBlank())
            assertEquals(st, normalizeZoneStyle(st.name.lowercase()))
        }
        ZonePattern.entries.forEach { pat ->
            assertTrue(pat.label.isNotBlank())
            assertEquals(pat, normalizeZonePattern(pat.name.lowercase()))
        }
        assertEquals(ZoneStyle.DASH_DOT, normalizeZoneStyle("dash-dot"))
        assertEquals(ZoneStyle.DOUBLE_LINE, normalizeZoneStyle("double"))
        assertEquals(ZonePattern.NO_FILL, normalizeZonePattern("no-fill"))
        assertEquals("Без заливки", ZonePattern.NO_FILL.label)
        assertEquals(ZonePattern.CROSS_HATCH, normalizeZonePattern("cross-hatch"))
        assertEquals(ZonePattern.HATCH, normalizeZonePattern(null, legacyHatched = true))
        assertEquals(ZonePattern.NONE, normalizeZonePattern(null, legacyHatched = false))

        val jsonInput = """
            {
              "manifest": { "format": "urbex-t-db", "version": "1.0.0", "checksum": "manual" },
              "payload": {
                "objects": [
                  { "id": "zs1", "title": "Двойной контур", "lat": 55.75, "lon": 37.62, "zoneStyle": "double-line", "zonePattern": "hazard-stripes" },
                  { "id": "zs2", "title": "Наследие", "lat": 55.76, "lon": 37.63, "zoneStyle": "glow", "zoneHatched": true },
                  { "id": "zs3", "title": "Пустой контур", "lat": 55.77, "lon": 37.64, "zoneStyle": "solid", "zonePattern": "no_fill", "zoneHatched": true }
                ]
              }
            }
        """.trimIndent()
        val res = UrbexDbSerializer.parseAndVerify(jsonInput)
        assertTrue(res is ParseUrbexDbResult.Valid)
        val objs = (res as ParseUrbexDbResult.Valid).bundle.payload.objects
        assertEquals(ZoneStyle.DOUBLE_LINE, objs[0].zoneStyle)
        assertEquals(ZonePattern.HAZARD_STRIPES, objs[0].zonePattern)
        assertTrue(objs[0].zoneHatched)
        assertEquals(ZoneStyle.GLOW, objs[1].zoneStyle)
        assertEquals(ZonePattern.HATCH, objs[1].zonePattern)
        assertTrue(objs[1].zoneHatched)
        assertEquals(ZonePattern.NO_FILL, objs[2].zonePattern)
        assertEquals(false, objs[2].zoneHatched)
    }

    @Test
    fun mapPlaceSearchOfflineAndNominatimWork() {
        val sampleObjects = listOf(
            UrbexObject(
                id = "o-null-sub",
                title = "ТЭЦ-12",
                category = ObjectCategory.ENERGY,
                status = ReliabilityStatus.VERIFIED,
                lat = 55.744,
                lon = 37.545,
                elevationM = 130,
                description = "",
                accessNotes = "",
                hazards = emptyList(),
                createdAt = 1000L,
                updatedAt = 1000L,
                authorNodeId = "!a1",
                subtype = null
            ),
            UrbexObject(
                id = "o-with-sub",
                title = "Бункер-42",
                category = ObjectCategory.BUNKER,
                status = ReliabilityStatus.UNVERIFIED,
                lat = 55.741,
                lon = 37.651,
                elevationM = 110,
                description = "",
                accessNotes = "",
                hazards = emptyList(),
                createdAt = 1000L,
                updatedAt = 1000L,
                authorNodeId = "!a1",
                subtype = "Командный пункт"
            )
        )

        val defaultPopular = MapPlaceSearch.searchOffline("", sampleObjects)
        assertTrue(defaultPopular.isNotEmpty())
        assertTrue(defaultPopular.all { it.kind == MapSearchKind.CITY })

        val cityByAlias = MapPlaceSearch.searchOffline("питер", sampleObjects)
        assertEquals("Санкт-Петербург", cityByAlias.first().title)

        val objNoSub = MapPlaceSearch.searchOffline("ТЭЦ-12", sampleObjects)
        assertEquals("o-null-sub", objNoSub.first().objectId)
        assertEquals(ObjectCategory.ENERGY.label, objNoSub.first().subtitle)

        val objBySub = MapPlaceSearch.searchOffline("командный", sampleObjects)
        assertEquals("o-with-sub", objBySub.first().objectId)
        assertTrue(objBySub.first().subtitle.contains("Командный пункт"))

        val coordHits = MapPlaceSearch.searchOffline("55.7558, 37.6173", sampleObjects)
        assertEquals(MapSearchKind.COORD, coordHits.first().kind)
        assertEquals(55.7558, coordHits.first().lat, 0.0001)

        val osmJson = """
            [
              {
                "place_id": 101,
                "lat": "55.7558",
                "lon": "37.6173",
                "name": "Москва",
                "display_name": "Москва, Центральный федеральный округ, Россия",
                "addresstype": "city"
              },
              {
                "place_id": 202,
                "lat": "56.3112",
                "lon": "38.1356",
                "name": "Сергиев Посад",
                "display_name": "Сергиев Посад, Московская область, Россия",
                "addresstype": "town"
              }
            ]
        """.trimIndent()
        val parsed = MapPlaceSearch.parseNominatimJson(osmJson)
        assertEquals(2, parsed.size)
        assertEquals(MapSearchSource.ONLINE, parsed[0].source)
        val merged = MapPlaceSearch.mergeResults(MapPlaceSearch.searchOffline("Москва", sampleObjects), parsed)
        assertEquals(1, merged.count { it.title == "Москва" })
        assertTrue(merged.any { it.title == "Сергиев Посад" })
    }
}



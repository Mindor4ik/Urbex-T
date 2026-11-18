package com.urbext.core

import com.urbext.core.backup.ParseUrbexDbResult
import com.urbext.core.backup.UrbexDbSerializer
import com.urbext.core.geo.GeoUtils
import com.urbext.core.mesh.MeshtasticCodec
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.CoordinateFormat
import com.urbext.domain.model.MeshPacketKind
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.PdaOrientation
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile
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
                pdaModeEnabled = true,
                pdaOrientation = PdaOrientation.LANDSCAPE,
                updatedAt = 1000L
            )
        )

        val serialized = UrbexDbSerializer.serialize(payload, "!a48f20c1", 5000L)
        val valid = UrbexDbSerializer.parseAndVerify(serialized)
        assertTrue(valid is ParseUrbexDbResult.Valid)
        val decoded = (valid as ParseUrbexDbResult.Valid).bundle.payload.settings
        assertTrue(decoded.pdaModeEnabled)
        assertEquals(PdaOrientation.LANDSCAPE, decoded.pdaOrientation)

        val tampered = serialized.replace("Градирня ТЭЦ-4", "Подменённый объект")
        val invalid = UrbexDbSerializer.parseAndVerify(tampered)
        assertTrue(invalid is ParseUrbexDbResult.Invalid)
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
}

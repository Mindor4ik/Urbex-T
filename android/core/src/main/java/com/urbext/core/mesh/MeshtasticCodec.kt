package com.urbext.core.mesh

import com.urbext.domain.model.MeshMessage
import com.urbext.domain.model.MeshPacketKind
import java.util.Locale

object MeshtasticCodec {
    fun normalizeNodeId(raw: String): String {
        val hex = raw.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            .lowercase(Locale.US)
            .take(8)
            .padEnd(8, '0')
        return "!$hex"
    }

    fun encodeFrameHex(
        channelId: String,
        senderNodeId: String,
        kind: MeshPacketKind,
        hopLimit: Int,
        body: String,
        lat: Double? = null,
        lon: Double? = null
    ): String {
        val kindByte = when (kind) {
            MeshPacketKind.TEXT -> 0x01
            MeshPacketKind.POSITION -> 0x02
            MeshPacketKind.SOS -> 0x03
        }
        val clampedHop = hopLimit.coerceIn(1, 7)
        val header = byteArrayOf(0x94.toByte(), 0xC3.toByte(), kindByte.toByte(), clampedHop.toByte())
        val text = if (lat != null && lon != null) {
            String.format(Locale.US, "%s|%s|%.5f,%.5f|%s", channelId, senderNodeId, lat, lon, body)
        } else {
            "$channelId|$senderNodeId|$body"
        }
        val bodyBytes = text.take(180).toByteArray(Charsets.UTF_8)
        return (header + bodyBytes).joinToString("") { "%02x".format(it) }
    }

    fun buildMessage(
        channelId: String,
        senderNodeId: String,
        senderCallsign: String,
        kind: MeshPacketKind,
        body: String,
        hopLimit: Int,
        lat: Double? = null,
        lon: Double? = null,
        timestamp: Long = System.currentTimeMillis()
    ): MeshMessage {
        val trimmed = body.trim()
        val id = "pkt-${senderNodeId.drop(1).take(4)}-${timestamp.toString(36)}"
        return MeshMessage(
            id = id,
            channelId = channelId,
            senderNodeId = senderNodeId,
            senderCallsign = senderCallsign,
            kind = kind,
            body = trimmed,
            lat = lat,
            lon = lon,
            timestamp = timestamp,
            hopLimit = hopLimit.coerceIn(1, 7),
            acknowledged = true,
            frameHex = encodeFrameHex(channelId, senderNodeId, kind, hopLimit, trimmed, lat, lon)
        )
    }
}

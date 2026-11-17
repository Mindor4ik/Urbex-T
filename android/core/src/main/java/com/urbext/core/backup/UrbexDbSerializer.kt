package com.urbext.core.backup

import com.urbext.domain.model.UrbexDbBundle
import com.urbext.domain.model.UrbexDbCounts
import com.urbext.domain.model.UrbexDbManifest
import com.urbext.domain.model.UrbexDbPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface ParseUrbexDbResult {
    data class Valid(val bundle: UrbexDbBundle) : ParseUrbexDbResult
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
        val bundle = try {
            json.decodeFromString<UrbexDbBundle>(raw)
        } catch (_: Exception) {
            return ParseUrbexDbResult.Invalid("Файл повреждён или не является корректным JSON-контейнером .urbexdb.")
        }

        if (bundle.manifest.format != FORMAT_ID) {
            return ParseUrbexDbResult.Invalid("Неизвестный формат контейнера: ${bundle.manifest.format}.")
        }

        val major = bundle.manifest.version.substringBefore('.')
        if (major != "1") {
            return ParseUrbexDbResult.Invalid("Неподдерживаемая версия формата: ${bundle.manifest.version}.")
        }

        val expected = computeChecksum(bundle.payload)
        if (bundle.manifest.checksum != expected) {
            return ParseUrbexDbResult.Invalid(
                "Нарушена контрольная сумма целостности (ожидалось $expected, получено ${bundle.manifest.checksum})."
            )
        }

        return ParseUrbexDbResult.Valid(bundle)
    }
}

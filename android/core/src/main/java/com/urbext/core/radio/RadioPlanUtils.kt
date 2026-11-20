package com.urbext.core.radio

import com.urbext.domain.model.RadioChannelPreset
import com.urbext.domain.model.RadioEncryptionType
import com.urbext.domain.model.RadioModulation
import com.urbext.domain.model.RadioPowerLevel
import com.urbext.domain.model.ToneSquelchMode
import java.security.SecureRandom
import java.util.Locale

data class DuplexOffsetResult(
    val duplex: String,
    val offsetMhz: String
)

object RadioPlanUtils {
    private val secureRandom = SecureRandom()

    val CTCSS_TONES: List<String> = listOf(
        "67.0", "69.3", "71.9", "74.4", "77.0", "79.7", "82.5", "85.4", "88.5", "91.5",
        "94.8", "97.4", "100.0", "103.5", "107.2", "110.9", "114.8", "118.8", "123.0", "127.3",
        "131.8", "136.5", "141.3", "146.2", "151.4", "156.7", "162.2", "167.9", "173.8", "179.9",
        "186.2", "192.8", "203.5", "210.7", "218.1", "225.7", "233.6", "241.8", "250.3", "254.1"
    )

    val DCS_CODES: List<String> = listOf(
        "023", "025", "026", "031", "032", "043", "047", "051", "054", "065",
        "071", "072", "073", "074", "114", "115", "116", "125", "131", "132",
        "134", "143", "152", "155", "156", "162", "165", "172", "174", "205",
        "223", "226", "243", "244", "245", "251", "261", "263", "265", "271",
        "306", "311", "315", "331", "343", "346", "351", "364", "365", "371",
        "411", "412", "413", "423", "431", "432", "445", "464", "465", "466",
        "503", "506", "516", "532", "546", "565", "606", "612", "624", "627",
        "631", "632", "654", "662", "664", "703", "712", "723", "731", "732",
        "734", "743", "754"
    )

    val BANDWIDTH_OPTIONS: List<Pair<String, String>> = listOf(
        "12.5" to "12.5 кГц",
        "25" to "25.0 кГц",
        "62.5" to "62.5 кГц",
        "125" to "125 кГц",
        "250" to "250 кГц"
    )

    fun createDefaultRadioChannels(now: Long = System.currentTimeMillis()): List<RadioChannelPreset> = listOf(
        RadioChannelPreset(
            id = "rch-pmr-1",
            name = "PMR-1 · Разведка (основной)",
            rxFreqMhz = "446.00625",
            txFreqMhz = "446.00625",
            mode = RadioModulation.PMR446,
            bandwidthKhz = "12.5",
            toneMode = ToneSquelchMode.CTCSS,
            rxTone = "88.5",
            txTone = "88.5",
            squelchLevel = 3,
            powerLevel = RadioPowerLevel.LOW,
            encryptionType = RadioEncryptionType.NONE,
            encryptionKey = "",
            txInhibit = false,
            notes = "Безлицензионный ближний канал группы, субтон 88.5 Hz.",
            updatedAt = now
        ),
        RadioChannelPreset(
            id = "rch-lpd-tactical",
            name = "LPD-23 · Периметр (DCS)",
            rxFreqMhz = "433.62500",
            txFreqMhz = "433.62500",
            mode = RadioModulation.LPD,
            bandwidthKhz = "12.5",
            toneMode = ToneSquelchMode.DCS,
            rxTone = "023",
            txTone = "023",
            squelchLevel = 4,
            powerLevel = RadioPowerLevel.MID,
            encryptionType = RadioEncryptionType.SCRAMBLER,
            encryptionKey = "3300",
            txInhibit = false,
            notes = "Цифровой шумоподавитель D023N и инверсия спектра.",
            updatedAt = now
        ),
        RadioChannelPreset(
            id = "rch-vhf-call",
            name = "2м Вызывной / Репитер",
            rxFreqMhz = "145.50000",
            txFreqMhz = "145.50000",
            mode = RadioModulation.NFM,
            bandwidthKhz = "12.5",
            toneMode = ToneSquelchMode.CTCSS,
            rxTone = "NONE",
            txTone = "103.5",
            squelchLevel = 3,
            powerLevel = RadioPowerLevel.HIGH,
            encryptionType = RadioEncryptionType.NONE,
            encryptionKey = "",
            txInhibit = false,
            notes = "Общий вызов 2м; приём открыт, передача с CTCSS 103.5 Hz.",
            updatedAt = now
        ),
        RadioChannelPreset(
            id = "rch-lora-868",
            name = "LoRa RU868 · Шифрканал",
            rxFreqMhz = "868.12500",
            txFreqMhz = "868.12500",
            mode = RadioModulation.LORA,
            bandwidthKhz = "125",
            toneMode = ToneSquelchMode.NONE,
            rxTone = "NONE",
            txTone = "NONE",
            squelchLevel = 0,
            powerLevel = RadioPowerLevel.LOW,
            encryptionType = RadioEncryptionType.AES256,
            encryptionKey = "4a9f8c2e10b7d53a6f119c8e2b4d70a15e3c91f48b26a0d7c3e91f20b8a45c61",
            txInhibit = false,
            notes = "Цифровой канал Meshtastic LongFast RU868 с PSK AES-256.",
            updatedAt = now
        )
    )

    fun normalizeFrequencyMhz(raw: String): String? {
        val cleaned = raw.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        val parsed = cleaned.toDoubleOrNull() ?: return null
        if (!parsed.isFinite() || parsed < 1.0 || parsed > 1300.0) return null
        return String.format(Locale.US, "%.5f", parsed)
    }

    fun computeDuplexOffsetMhz(rxFreqMhz: String, txFreqMhz: String): DuplexOffsetResult {
        val rx = rxFreqMhz.replace(',', '.').toDoubleOrNull()
        val tx = txFreqMhz.replace(',', '.').toDoubleOrNull()
        if (rx == null || tx == null || !rx.isFinite() || !tx.isFinite()) {
            return DuplexOffsetResult("", "0.00000")
        }
        val diff = tx - rx
        if (kotlin.math.abs(diff) < 0.00001) {
            return DuplexOffsetResult("", "0.00000")
        }
        return DuplexOffsetResult(
            duplex = if (diff > 0) "+" else "-",
            offsetMhz = String.format(Locale.US, "%.5f", kotlin.math.abs(diff))
        )
    }

    fun generateRadioCryptoKey(type: RadioEncryptionType): String {
        if (type == RadioEncryptionType.NONE) return ""
        if (type == RadioEncryptionType.SCRAMBLER) {
            val presets = listOf("2600", "2900", "3100", "3300", "3490")
            return presets[secureRandom.nextInt(presets.size)]
        }
        val byteLen = when (type) {
            RadioEncryptionType.ARC4 -> 5
            RadioEncryptionType.AES128 -> 16
            RadioEncryptionType.AES256 -> 32
            else -> 16
        }
        val bytes = ByteArray(byteLen)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun csvEscape(value: String): String {
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    fun buildChirpCsv(channels: List<RadioChannelPreset>): String {
        val header = "Location,Name,Frequency,Duplex,Offset,Tone,rToneFreq,cToneFreq,DtcsCode,DtcsPolarity,Mode,TStep,Skip,Comment"
        val rows = channels.mapIndexed { idx, ch ->
            val rx = normalizeFrequencyMhz(ch.rxFreqMhz) ?: "446.00625"
            val tx = normalizeFrequencyMhz(ch.txFreqMhz) ?: rx
            val offsetRes = computeDuplexOffsetMhz(rx, tx)
            val effectiveDuplex = if (ch.txInhibit) "off" else offsetRes.duplex

            var toneCol = ""
            var rTone = "88.5"
            var cTone = "88.5"
            var dtcs = "023"
            var dtcsPol = "NN"

            when (ch.toneMode) {
                ToneSquelchMode.CTCSS -> {
                    val hasRx = ch.rxTone != "NONE" && ch.rxTone.isNotBlank()
                    val hasTx = ch.txTone != "NONE" && ch.txTone.isNotBlank()
                    when {
                        hasRx && hasTx -> {
                            toneCol = "TSQL"
                            rTone = ch.txTone
                            cTone = ch.rxTone
                        }
                        hasTx -> {
                            toneCol = "Tone"
                            rTone = ch.txTone
                        }
                        hasRx -> {
                            toneCol = "TSQL"
                            cTone = ch.rxTone
                        }
                    }
                }
                ToneSquelchMode.DCS, ToneSquelchMode.DCS_INV -> {
                    toneCol = "DTCS"
                    val raw = if (ch.txTone != "NONE") ch.txTone else ch.rxTone
                    dtcs = raw.filter { it.isDigit() }.ifEmpty { "023" }
                    dtcsPol = if (ch.toneMode == ToneSquelchMode.DCS_INV) "RR" else "NN"
                }
                ToneSquelchMode.NONE -> Unit
            }

            val chirpMode = when (ch.mode) {
                RadioModulation.FM -> "FM"
                RadioModulation.AM -> "AM"
                else -> "NFM"
            }
            val cryptoHint = if (ch.encryptionType != RadioEncryptionType.NONE) "[${ch.encryptionType.name}] " else ""
            val comment = "$cryptoHint${ch.notes}".trim()

            listOf(
                (idx + 1).toString(),
                csvEscape(ch.name.take(16)),
                rx,
                effectiveDuplex,
                offsetRes.offsetMhz,
                toneCol,
                rTone,
                cTone,
                dtcs,
                dtcsPol,
                chirpMode,
                "6.25",
                "",
                csvEscape(comment)
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }
}

package com.urbext.core.backup

import com.urbext.domain.model.CloudSyncConfig
import com.urbext.domain.model.EncryptedSyncEnvelope
import com.urbext.domain.model.UrbexDbBundle
import com.urbext.domain.model.UrbexDbPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CloudSyncCrypto {
    const val E2EE_ENVELOPE_FORMAT = "urbex-e2ee-v1"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val DEFAULT_ITERATIONS = 100_000

    private val secureRandom = SecureRandom()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Strips sensitive cloud API tokens, WebDAV logins, and E2EE passphrases from any payload
     * before exporting to .urbexdb or uploading to a remote provider.
     */
    fun sanitizePayloadForSync(payload: UrbexDbPayload): UrbexDbPayload {
        val sync = payload.settings.cloudSync
        return payload.copy(
            settings = payload.settings.copy(
                cloudSync = sync.copy(
                    authToken = "",
                    username = "",
                    encryptionPassphrase = ""
                )
            )
        )
    }

    fun buildRemoteFileName(config: CloudSyncConfig): String {
        val raw = config.remoteFileName.trim().ifEmpty { "urbex-t-sync.urbexdb" }
        return if (raw.endsWith(".urbexdb", ignoreCase = true)) raw else "$raw.urbexdb"
    }

    fun isEncryptedSyncEnvelope(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith("{") && trimmed.contains("\"$E2EE_ENVELOPE_FORMAT\"")
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val derivedBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(derivedBytes, "AES")
    }

    fun encryptSyncContent(plaintext: String, passphrase: String): String {
        val pass = passphrase.trim()
        require(pass.isNotEmpty()) {
            "Укажите парольную фразу для шифрования AES-256-GCM перед отправкой."
        }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val key = deriveAesKey(pass, salt, DEFAULT_ITERATIONS)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val encoder = Base64.getEncoder()
        val envelope = EncryptedSyncEnvelope(
            format = E2EE_ENVELOPE_FORMAT,
            kdf = "PBKDF2-SHA256",
            iterations = DEFAULT_ITERATIONS,
            saltB64 = encoder.encodeToString(salt),
            ivB64 = encoder.encodeToString(iv),
            cipherB64 = encoder.encodeToString(cipherBytes),
            updatedAt = System.currentTimeMillis()
        )
        return json.encodeToString(envelope)
    }

    fun decryptSyncContent(rawEnvelope: String, passphrase: String): String {
        val envelope = runCatching {
            json.decodeFromString<EncryptedSyncEnvelope>(rawEnvelope)
        }.getOrElse {
            throw IllegalArgumentException("Повреждённый контейнер шифрования.")
        }
        require(
            envelope.format == E2EE_ENVELOPE_FORMAT &&
                envelope.saltB64.isNotBlank() &&
                envelope.ivB64.isNotBlank() &&
                envelope.cipherB64.isNotBlank()
        ) {
            "Неподдерживаемый формат E2EE-контейнера."
        }
        val pass = passphrase.trim()
        require(pass.isNotEmpty()) {
            "Удалённый файл зашифрован (AES-256-GCM). Введите парольную фразу."
        }

        val decoder = Base64.getDecoder()
        val salt = decoder.decode(envelope.saltB64)
        val iv = decoder.decode(envelope.ivB64)
        val cipherBytes = decoder.decode(envelope.cipherB64)
        val iterations = envelope.iterations.coerceAtLeast(10_000)

        val key = deriveAesKey(pass, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalStateException("Неверный пароль расшифровки E2EE или файл повреждён.")
        }
    }

    fun encodeOutgoingSnapshot(
        payload: UrbexDbPayload,
        deviceNodeId: String,
        config: CloudSyncConfig
    ): String {
        val serialized = UrbexDbSerializer.serialize(sanitizePayloadForSync(payload), deviceNodeId)
        return if (config.encryptBeforeUpload) {
            encryptSyncContent(serialized, config.encryptionPassphrase)
        } else {
            serialized
        }
    }

    fun decodeRemoteSnapshot(rawText: String, passphrase: String): UrbexDbBundle {
        val plaintext = if (isEncryptedSyncEnvelope(rawText)) {
            decryptSyncContent(rawText, passphrase)
        } else {
            rawText
        }
        return when (val parsed = UrbexDbSerializer.parseAndVerify(plaintext)) {
            is ParseUrbexDbResult.Valid -> parsed.bundle
            is ParseUrbexDbResult.Invalid -> throw IllegalStateException(parsed.error)
        }
    }
}

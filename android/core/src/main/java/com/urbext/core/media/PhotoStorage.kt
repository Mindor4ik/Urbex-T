package com.urbext.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class SavedPhotoResult(
    val localUri: String,
    val sizeBytes: Long
)

object PhotoStorage {
    private const val PHOTOS_DIR = "urbex_photos"
    private const val MAX_DIM_PX = 1440
    private const val MAX_DOWNLOAD_BYTES = 15 * 1024 * 1024

    fun isLocalStoredUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        return uri.startsWith("file://") || uri.startsWith("data:image/")
    }

    suspend fun downloadPhotoToLocalDb(
        context: Context,
        rawUrl: String
    ): Result<SavedPhotoResult> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = rawUrl.trim()
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                "Укажите корректную ссылку http:// или https://"
            }

            val bytes = fetchBytesFollowingRedirects(trimmed, 0)
            val optimized = optimizeToJpegBytes(bytes)
            val file = saveBytesToPhotosDir(context, optimized)
            SavedPhotoResult(
                localUri = Uri.fromFile(file).toString(),
                sizeBytes = file.length()
            )
        }
    }

    suspend fun copyLocalPhotoToDb(
        context: Context,
        sourceUri: Uri
    ): Result<SavedPhotoResult> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: error("Не удалось прочитать выбранное изображение")
            val optimized = optimizeToJpegBytes(rawBytes)
            val file = saveBytesToPhotosDir(context, optimized)
            SavedPhotoResult(
                localUri = Uri.fromFile(file).toString(),
                sizeBytes = file.length()
            )
        }
    }

    private fun fetchBytesFollowingRedirects(urlStr: String, depth: Int): ByteArray {
        require(depth <= 4) { "Слишком много перенаправлений при загрузке фото" }
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Urbex-T/1.0 Android")
        }
        return try {
            val code = conn.responseCode
            if (code in 300..399) {
                val next = conn.getHeaderField("Location")
                    ?: error("Сервер вернул редирект ($code) без заголовка Location")
                val resolved = URL(URL(urlStr), next).toString()
                return fetchBytesFollowingRedirects(resolved, depth + 1)
            }
            if (code !in 200..299) {
                error("Сервер вернул код HTTP $code")
            }
            val raw = conn.inputStream.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > MAX_DOWNLOAD_BYTES) {
                        error("Изображение превышает лимит 15 МБ")
                    }
                    buffer.write(chunk, 0, n)
                }
                buffer.toByteArray()
            }
            require(raw.isNotEmpty()) { "Получен пустой ответ сервера" }
            raw
        } finally {
            conn.disconnect()
        }
    }

    private fun optimizeToJpegBytes(rawBytes: ByteArray): ByteArray {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
        require(boundsOpts.outWidth > 0 && boundsOpts.outHeight > 0) {
            "Файл по ссылке не является изображением (проверьте прямую ссылку на фото)"
        }

        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > MAX_DIM_PX * 2 ||
            boundsOpts.outHeight / sampleSize > MAX_DIM_PX * 2
        ) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: error("Не удалось декодировать изображение")

        val maxSide = maxOf(decoded.width, decoded.height)
        val scaled = if (maxSide > MAX_DIM_PX) {
            val ratio = MAX_DIM_PX.toFloat() / maxSide.toFloat()
            val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        scaled.recycle()
        return out.toByteArray()
    }

    private fun saveBytesToPhotosDir(context: Context, jpegBytes: ByteArray): File {
        val dir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
        file.writeBytes(jpegBytes)
        return file
    }
}

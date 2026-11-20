package com.urbext.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import com.urbext.core.backup.CloudSyncCrypto
import com.urbext.core.backup.UrbexDbSerializer
import com.urbext.data.local.SettingsDataStore
import com.urbext.data.repository.WorkspaceManager
import com.urbext.domain.model.CloudSyncConfig
import com.urbext.domain.model.CloudSyncProvider
import com.urbext.domain.model.CloudSyncStatus
import com.urbext.domain.model.UrbexDbBundle
import com.urbext.domain.model.UrbexDbManifest
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.usecase.MergeUrbexDbUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL

class CloudSyncEngine(
    private val context: Context,
    private val workspaceManager: WorkspaceManager,
    private val settingsStore: SettingsDataStore,
    private val mergeUseCase: MergeUrbexDbUseCase = MergeUrbexDbUseCase()
) {
    private val appContext = context.applicationContext
    private val syncMutex = Mutex()

    suspend fun syncNow(manual: Boolean): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val initialSettings = settingsStore.getOnce()
            val config = initialSettings.cloudSync

            if (config.provider == CloudSyncProvider.NONE) {
                return@withLock false to "Выберите провайдер синхронизации."
            }
            if (!manual && !config.enabled) {
                return@withLock false to "Автосинхронизация выключена."
            }

            if (config.provider != CloudSyncProvider.LOCAL_FOLDER) {
                val netIssue = checkNetworkEligibility(config.wifiOnly)
                if (netIssue != null) {
                    if (manual) {
                        updateSyncMeta(config, CloudSyncStatus.ERROR, netIssue)
                    }
                    return@withLock false to netIssue
                }
            }

            updateSyncMeta(config, CloudSyncStatus.SYNCING, "Синхронизация...")

            try {
                workspaceManager.ensureInitialized()

                // 1. Pull remote snapshot if present
                val pullResult = pullRemoteSnapshot(config)
                var mergedObjectsCount = 0

                val localPayload = workspaceManager.backupRepository.exportPayload()
                val deviceNodeId = localPayload.profile.nodeId.ifBlank { "!manual" }

                val outgoingPayload: UrbexDbPayload = if (!pullResult.content.isNullOrBlank()) {
                    val remoteBundle = CloudSyncCrypto.decodeRemoteSnapshot(
                        rawText = pullResult.content,
                        passphrase = config.encryptionPassphrase
                    )
                    val (merged, _) = mergeUseCase.merge(localPayload, remoteBundle.payload, config.conflictStrategy)
                    val preservedMerged = merged.copy(
                        settings = merged.settings.copy(
                            editorEnabled = localPayload.settings.editorEnabled,
                            pdaModeEnabled = localPayload.settings.pdaModeEnabled,
                            pdaOrientation = localPayload.settings.pdaOrientation,
                            showMapGrid = localPayload.settings.showMapGrid,
                            showCompassHud = localPayload.settings.showCompassHud,
                            hazardProximityAlertM = localPayload.settings.hazardProximityAlertM,
                            confirmBeforeDelete = localPayload.settings.confirmBeforeDelete,
                            hapticFeedback = localPayload.settings.hapticFeedback,
                            maskCoordinatesOnScreen = localPayload.settings.maskCoordinatesOnScreen,
                            imageCacheLimitMb = localPayload.settings.imageCacheLimitMb,
                            cloudSync = config
                        )
                    )
                    mergedObjectsCount = preservedMerged.objects.size

                    workspaceManager.suppressMutationCallbacks = true
                    try {
                        workspaceManager.backupRepository.importBundle(
                            bundle = UrbexDbBundle(
                                manifest = UrbexDbManifest(
                                    exportedAt = System.currentTimeMillis(),
                                    deviceNodeId = deviceNodeId
                                ),
                                payload = preservedMerged
                            ),
                            strategy = config.conflictStrategy
                        )
                    } finally {
                        workspaceManager.suppressMutationCallbacks = false
                    }
                    preservedMerged
                } else {
                    mergedObjectsCount = localPayload.objects.size
                    localPayload
                }

                // 2. Encode outgoing snapshot (sanitizes secrets + optional AES-256-GCM)
                val outgoingSnapshot = CloudSyncCrypto.encodeOutgoingSnapshot(
                    payload = outgoingPayload,
                    deviceNodeId = deviceNodeId,
                    config = config
                )

                // 3. Push outgoing snapshot
                val pushMeta = pushRemoteSnapshot(
                    config = config,
                    resolvedGistId = pullResult.resolvedGistId ?: config.gistId,
                    snapshotText = outgoingSnapshot
                )

                val finalGistId = pushMeta.gistId ?: pullResult.resolvedGistId ?: config.gistId
                val okMsg = buildString {
                    append("Синхронизировано (")
                    append(mergedObjectsCount)
                    append(" объ.")
                    if (config.encryptBeforeUpload) append(" · E2EE")
                    append(")")
                }

                val latestSettings = settingsStore.getOnce()
                settingsStore.saveSettings(
                    latestSettings.copy(
                        cloudSync = latestSettings.cloudSync.copy(
                            gistId = finalGistId,
                            lastSyncAt = System.currentTimeMillis(),
                            lastSyncStatus = CloudSyncStatus.SUCCESS,
                            lastSyncMessage = okMsg
                        )
                    )
                )
                true to okMsg
            } catch (e: Exception) {
                val errMsg = e.message?.take(220) ?: "Ошибка облачной синхронизации."
                updateSyncMeta(config, CloudSyncStatus.ERROR, errMsg)
                false to errMsg
            }
        }
    }

    private suspend fun updateSyncMeta(
        baseConfig: CloudSyncConfig,
        status: CloudSyncStatus,
        message: String
    ) {
        val current = settingsStore.getOnce()
        val activeConfig = current.cloudSync
        settingsStore.saveSettings(
            current.copy(
                cloudSync = activeConfig.copy(
                    gistId = activeConfig.gistId.ifBlank { baseConfig.gistId },
                    lastSyncAt = if (status == CloudSyncStatus.SUCCESS) System.currentTimeMillis() else activeConfig.lastSyncAt,
                    lastSyncStatus = status,
                    lastSyncMessage = message
                )
            )
        )
    }

    private fun checkNetworkEligibility(wifiOnly: Boolean): String? {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Служба сети недоступна."
        val network = cm.activeNetwork ?: return "Нет активного подключения к интернету."
        val caps = cm.getNetworkCapabilities(network) ?: return "Нет сетевых параметров."
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return "Отсутствует доступ в интернет."
        }
        if (wifiOnly && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "Ожидание Wi-Fi (включён режим «Только по Wi-Fi»)."
        }
        return null
    }

    private data class PullResult(
        val content: String? = null,
        val resolvedGistId: String? = null
    )

    private data class PushResult(
        val gistId: String? = null
    )

    private fun pullRemoteSnapshot(config: CloudSyncConfig): PullResult {
        val fileName = CloudSyncCrypto.buildRemoteFileName(config)
        return when (config.provider) {
            CloudSyncProvider.NONE -> PullResult()
            CloudSyncProvider.GITHUB_GIST -> {
                val token = config.authToken.trim()
                require(token.isNotEmpty()) { "Укажите GitHub Token (scope: gist)." }
                val gistId = config.gistId.trim()
                if (gistId.isEmpty()) return PullResult(content = null)

                val resp = httpRequest(
                    url = "https://api.github.com/gists/$gistId",
                    method = "GET",
                    headers = mapOf(
                        "Authorization" to "Bearer $token",
                        "Accept" to "application/vnd.github+json",
                        "X-GitHub-Api-Version" to "2022-11-28",
                        "User-Agent" to "Urbex-T-Android"
                    ),
                    allowNotFound = true
                )
                if (resp.code == 404) return PullResult(content = null)
                if (resp.code !in 200..299) {
                    throw IOException("GitHub API ${resp.code}: ${resp.body.take(140)}")
                }
                val root = JSONObject(resp.body)
                val filesObj = root.optJSONObject("files") ?: return PullResult(null, gistId)
                var fileObj = filesObj.optJSONObject(fileName)
                if (fileObj == null) {
                    val keys = filesObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key.endsWith(".urbexdb", ignoreCase = true) || keys.hasNext().not()) {
                            fileObj = filesObj.optJSONObject(key)
                            break
                        }
                    }
                }
                if (fileObj == null) return PullResult(null, gistId)
                val content = if (fileObj.optBoolean("truncated", false)) {
                    val rawUrl = fileObj.optString("raw_url")
                    if (rawUrl.isNotBlank()) {
                        val rawResp = httpRequest(
                            url = rawUrl,
                            method = "GET",
                            headers = mapOf(
                                "Authorization" to "Bearer $token",
                                "User-Agent" to "Urbex-T-Android"
                            )
                        )
                        if (rawResp.code in 200..299) rawResp.body else fileObj.optString("content")
                    } else {
                        fileObj.optString("content")
                    }
                } else {
                    fileObj.optString("content")
                }
                PullResult(content = content.takeIf { it.isNotBlank() }, resolvedGistId = gistId)
            }

            CloudSyncProvider.WEBDAV -> {
                val url = buildWebDavTargetUrl(config.remoteEndpoint, fileName)
                val headers = buildWebDavAuthHeaders(config)
                val resp = httpRequest(
                    url = url,
                    method = "GET",
                    headers = headers,
                    allowNotFound = true
                )
                if (resp.code == 404) return PullResult(content = null)
                if (resp.code !in 200..299) {
                    throw IOException("WebDAV GET ${resp.code}: ${resp.body.take(140)}")
                }
                PullResult(content = resp.body.takeIf { it.isNotBlank() })
            }

            CloudSyncProvider.CUSTOM_HTTP -> {
                val endpoint = config.remoteEndpoint.trim()
                require(endpoint.isNotEmpty()) { "Укажите URL сервера синхронизации." }
                val headers = buildMap {
                    if (config.authToken.isNotBlank()) {
                        put("Authorization", "Bearer ${config.authToken.trim()}")
                    }
                    put("Accept", "application/json, text/plain")
                }
                val resp = httpRequest(
                    url = endpoint,
                    method = "GET",
                    headers = headers,
                    allowNotFound = true
                )
                if (resp.code == 404) return PullResult(content = null)
                if (resp.code !in 200..299) {
                    throw IOException("HTTP GET ${resp.code}: ${resp.body.take(140)}")
                }
                PullResult(content = resp.body.takeIf { it.isNotBlank() })
            }

            CloudSyncProvider.LOCAL_FOLDER -> {
                val uriRaw = config.remoteEndpoint.trim()
                require(uriRaw.isNotEmpty()) { "Выберите системную папку синхронизации." }
                val treeUri = Uri.parse(uriRaw)
                val existingDoc = findSafDocument(treeUri, fileName) ?: return PullResult(content = null)
                val text = appContext.contentResolver.openInputStream(existingDoc)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                PullResult(content = text?.takeIf { it.isNotBlank() })
            }
        }
    }

    private fun pushRemoteSnapshot(
        config: CloudSyncConfig,
        resolvedGistId: String,
        snapshotText: String
    ): PushResult {
        val fileName = CloudSyncCrypto.buildRemoteFileName(config)
        return when (config.provider) {
            CloudSyncProvider.NONE -> PushResult()
            CloudSyncProvider.GITHUB_GIST -> {
                val token = config.authToken.trim()
                require(token.isNotEmpty()) { "Укажите GitHub Token (scope: gist)." }
                val filesJson = JSONObject().put(fileName, JSONObject().put("content", snapshotText))
                val bodyJson = JSONObject()
                    .put("description", "Urbex-T Cloud Sync Backup")
                    .put("public", false)
                    .put("files", filesJson)

                val gistId = resolvedGistId.trim()
                val targetUrl = if (gistId.isNotEmpty()) {
                    "https://api.github.com/gists/$gistId"
                } else {
                    "https://api.github.com/gists"
                }
                val method = if (gistId.isNotEmpty()) "PATCH" else "POST"
                val resp = httpRequest(
                    url = targetUrl,
                    method = method,
                    headers = mapOf(
                        "Authorization" to "Bearer $token",
                        "Accept" to "application/vnd.github+json",
                        "X-GitHub-Api-Version" to "2022-11-28",
                        "User-Agent" to "Urbex-T-Android",
                        "Content-Type" to "application/json; charset=utf-8"
                    ),
                    body = bodyJson.toString()
                )
                if (resp.code !in 200..299) {
                    throw IOException("GitHub API ${resp.code}: ${resp.body.take(160)}")
                }
                val respId = runCatching { JSONObject(resp.body).optString("id") }.getOrNull()
                PushResult(gistId = respId?.takeIf { it.isNotBlank() } ?: gistId)
            }

            CloudSyncProvider.WEBDAV -> {
                val url = buildWebDavTargetUrl(config.remoteEndpoint, fileName)
                val headers = buildWebDavAuthHeaders(config) + mapOf(
                    "Content-Type" to "application/json; charset=utf-8"
                )
                val resp = httpRequest(
                    url = url,
                    method = "PUT",
                    headers = headers,
                    body = snapshotText
                )
                if (resp.code !in 200..299) {
                    throw IOException("WebDAV PUT ${resp.code}: ${resp.body.take(160)}")
                }
                PushResult()
            }

            CloudSyncProvider.CUSTOM_HTTP -> {
                val endpoint = config.remoteEndpoint.trim()
                require(endpoint.isNotEmpty()) { "Укажите URL сервера синхронизации." }
                val headers = buildMap {
                    if (config.authToken.isNotBlank()) {
                        put("Authorization", "Bearer ${config.authToken.trim()}")
                    }
                    put("Content-Type", "application/json; charset=utf-8")
                }
                val resp = httpRequest(
                    url = endpoint,
                    method = "PUT",
                    headers = headers,
                    body = snapshotText
                )
                if (resp.code !in 200..299) {
                    throw IOException("HTTP PUT ${resp.code}: ${resp.body.take(160)}")
                }
                PushResult()
            }

            CloudSyncProvider.LOCAL_FOLDER -> {
                val uriRaw = config.remoteEndpoint.trim()
                require(uriRaw.isNotEmpty()) { "Выберите системную папку синхронизации." }
                val treeUri = Uri.parse(uriRaw)
                val existingDoc = findSafDocument(treeUri, fileName)
                val targetUri = existingDoc ?: run {
                    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
                    DocumentsContract.createDocument(
                        appContext.contentResolver,
                        parentUri,
                        "application/octet-stream",
                        fileName
                    ) ?: throw IOException("Не удалось создать файл $fileName в выбранной папке.")
                }

                val outStream = runCatching {
                    appContext.contentResolver.openOutputStream(targetUri, "wt")
                }.getOrNull() ?: appContext.contentResolver.openOutputStream(targetUri, "w")
                    ?: throw IOException("Нет доступа на запись в выбранную папку.")

                outStream.use { stream ->
                    stream.write(snapshotText.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
                PushResult()
            }
        }
    }

    private fun buildWebDavTargetUrl(rawBase: String, fileName: String): String {
        val base = rawBase.trim().trimEnd('/')
        require(base.isNotEmpty()) { "Укажите WebDAV URL (например https://webdav.yandex.ru/UrbexT)." }
        return if (base.endsWith(".urbexdb", ignoreCase = true) || base.endsWith(".json", ignoreCase = true)) {
            base
        } else {
            "$base/$fileName"
        }
    }

    private fun buildWebDavAuthHeaders(config: CloudSyncConfig): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        val user = config.username.trim()
        val token = config.authToken.trim()
        if (user.isNotEmpty() && token.isNotEmpty()) {
            val encoded = Base64.encodeToString("$user:$token".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            headers["Authorization"] = "Basic $encoded"
        } else if (token.isNotEmpty()) {
            headers["Authorization"] = if (token.startsWith("Basic ", true) || token.startsWith("Bearer ", true) || token.startsWith("OAuth ", true)) {
                token
            } else {
                "Bearer $token"
            }
        }
        return headers
    }

    private fun findSafDocument(treeUri: Uri, fileName: String): Uri? {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        appContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val docId = if (idIdx >= 0) cursor.getString(idIdx) else null
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                if (docId != null && name != null && name.equals(fileName, ignoreCase = true)) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                }
            }
        }
        return null
    }

    private data class HttpResponse(val code: Int, val body: String)

    private fun httpRequest(
        url: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        allowNotFound: Boolean = false
    ): HttpResponse {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            useCaches = false
            try {
                requestMethod = method
            } catch (_: ProtocolException) {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", method)
            }
            for ((k, v) in headers) {
                setRequestProperty(k, v)
            }
            if (body != null) {
                doOutput = true
            }
        }

        return try {
            if (body != null) {
                conn.outputStream.use { os ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code == 404 && allowNotFound) {
                HttpResponse(404, text)
            } else {
                HttpResponse(code, text)
            }
        } finally {
            conn.disconnect()
        }
    }
}

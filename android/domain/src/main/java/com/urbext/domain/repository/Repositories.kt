package com.urbext.domain.repository

import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.ArchiveItem
import com.urbext.domain.model.ArchiveMediaType
import com.urbext.domain.model.BaseArticle
import com.urbext.domain.model.ConflictStrategy
import com.urbext.domain.model.MeshMessage
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexDbBundle
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ObjectRepository {
    fun observeObjects(statusFilter: ReliabilityStatus? = null, query: String = ""): Flow<List<UrbexObject>>
    suspend fun getObjectById(id: String): UrbexObject?
    suspend fun upsertObject(obj: UrbexObject)
    suspend fun deleteObject(id: String)
}

interface MeshRepository {
    fun observeMessages(channelId: String): Flow<List<MeshMessage>>
    fun observeAllMessages(): Flow<List<MeshMessage>>
    suspend fun sendMessage(message: MeshMessage)
}

interface BaseRepository {
    fun observeArticles(query: String = ""): Flow<List<BaseArticle>>
    suspend fun upsertArticle(article: BaseArticle)
    suspend fun deleteArticle(id: String)
}

interface ArchiveRepository {
    fun observeItems(typeFilter: ArchiveMediaType? = null): Flow<List<ArchiveItem>>
    suspend fun addItem(item: ArchiveItem)
    suspend fun deleteItem(id: String)
}

interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile>
    suspend fun updateProfile(profile: UserProfile)
}

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
}

data class MergeStats(
    val added: Int,
    val updated: Int,
    val skipped: Int
)

interface BackupRepository {
    suspend fun exportPayload(): UrbexDbPayload
    suspend fun importBundle(bundle: UrbexDbBundle, strategy: ConflictStrategy): MergeStats
    suspend fun resetToSeed()
}

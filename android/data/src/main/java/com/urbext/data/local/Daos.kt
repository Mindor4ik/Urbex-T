package com.urbext.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UrbexObjectDao {
    @Query(
        """
        SELECT * FROM urbex_objects
        WHERE (:status IS NULL OR status = :status)
          AND (
            :query = ''
            OR title LIKE '%' || :query || '%'
            OR subtype LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%'
            OR accessNotes LIKE '%' || :query || '%'
          )
        ORDER BY updatedAt DESC
        """
    )
    fun observeObjects(status: String?, query: String): Flow<List<UrbexObjectEntity>>

    @Query("SELECT * FROM urbex_objects ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<UrbexObjectEntity>

    @Query("SELECT * FROM urbex_objects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UrbexObjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UrbexObjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<UrbexObjectEntity>)

    @Query("DELETE FROM urbex_objects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM urbex_objects")
    suspend fun clearAll()
}

@Dao
interface MeshMessageDao {
    @Query("SELECT * FROM mesh_messages WHERE channelId = :channelId ORDER BY timestamp ASC")
    fun observeByChannel(channelId: String): Flow<List<MeshMessageEntity>>

    @Query("SELECT * FROM mesh_messages ORDER BY timestamp ASC")
    fun observeAllMessages(): Flow<List<MeshMessageEntity>>

    @Query("SELECT * FROM mesh_messages ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<MeshMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MeshMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MeshMessageEntity>)

    @Query("DELETE FROM mesh_messages")
    suspend fun clearAll()
}

@Dao
interface BaseArticleDao {
    @Query(
        """
        SELECT * FROM base_articles
        WHERE (
            :query = ''
            OR title LIKE '%' || :query || '%'
            OR summary LIKE '%' || :query || '%'
            OR content LIKE '%' || :query || '%'
        )
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun observeArticles(query: String): Flow<List<BaseArticleEntity>>

    @Query("SELECT * FROM base_articles")
    suspend fun getAllOnce(): List<BaseArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BaseArticleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BaseArticleEntity>)

    @Query("DELETE FROM base_articles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM base_articles")
    suspend fun clearAll()
}

@Dao
interface ArchiveItemDao {
    @Query(
        """
        SELECT * FROM archive_items
        WHERE (:type IS NULL OR type = :type)
        ORDER BY createdAt DESC
        """
    )
    fun observeItems(type: String?): Flow<List<ArchiveItemEntity>>

    @Query("SELECT * FROM archive_items ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<ArchiveItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ArchiveItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ArchiveItemEntity>)

    @Query("DELETE FROM archive_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM archive_items")
    suspend fun clearAll()
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 'local' LIMIT 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 'local' LIMIT 1")
    suspend fun getOnce(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserProfileEntity)
}

@Dao
interface BackupTransactionDao {
    @Transaction
    suspend fun replaceAll(
        objectDao: UrbexObjectDao,
        meshDao: MeshMessageDao,
        baseDao: BaseArticleDao,
        archiveDao: ArchiveItemDao,
        profileDao: UserProfileDao,
        objects: List<UrbexObjectEntity>,
        messages: List<MeshMessageEntity>,
        articles: List<BaseArticleEntity>,
        archives: List<ArchiveItemEntity>,
        profile: UserProfileEntity
    ) {
        objectDao.clearAll()
        meshDao.clearAll()
        baseDao.clearAll()
        archiveDao.clearAll()
        objectDao.upsertAll(objects)
        meshDao.upsertAll(messages)
        baseDao.upsertAll(articles)
        archiveDao.upsertAll(archives)
        profileDao.upsert(profile)
    }
}

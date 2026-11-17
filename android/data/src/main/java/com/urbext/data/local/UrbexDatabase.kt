package com.urbext.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.urbext.data.seed.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UrbexObjectEntity::class,
        MeshMessageEntity::class,
        BaseArticleEntity::class,
        ArchiveItemEntity::class,
        UserProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class UrbexDatabase : RoomDatabase() {
    abstract fun objectDao(): UrbexObjectDao
    abstract fun meshDao(): MeshMessageDao
    abstract fun baseDao(): BaseArticleDao
    abstract fun archiveDao(): ArchiveItemDao
    abstract fun profileDao(): UserProfileDao
    abstract fun backupTransactionDao(): BackupTransactionDao

    companion object {
        @Volatile
        private var instance: UrbexDatabase? = null

        fun getInstance(context: Context): UrbexDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }
        }

        private fun buildDatabase(appContext: Context): UrbexDatabase {
            lateinit var db: UrbexDatabase
            db = Room.databaseBuilder(
                appContext,
                UrbexDatabase::class.java,
                "urbex_t.db"
            )
                .addCallback(object : Callback() {
                    override fun onCreate(sqliteDb: SupportSQLiteDatabase) {
                        super.onCreate(sqliteDb)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedInto(db)
                        }
                    }
                })
                .build()
            return db
        }

        suspend fun seedInto(db: UrbexDatabase) {
            val seed = SeedData.createPayload()
            db.backupTransactionDao().replaceAll(
                objectDao = db.objectDao(),
                meshDao = db.meshDao(),
                baseDao = db.baseDao(),
                archiveDao = db.archiveDao(),
                profileDao = db.profileDao(),
                objects = seed.objects.map { it.toEntity() },
                messages = seed.meshMessages.map { it.toEntity() },
                articles = seed.baseArticles.map { it.toEntity() },
                archives = seed.archiveItems.map { it.toEntity() },
                profile = seed.profile.toEntity()
            )
        }
    }
}

package com.urbext.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.urbext.data.seed.SeedData

@Database(
    entities = [
        UrbexObjectEntity::class,
        MeshMessageEntity::class,
        BaseArticleEntity::class,
        ArchiveItemEntity::class,
        UserProfileEntity::class
    ],
    version = 6,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE urbex_objects ADD COLUMN securityLevel TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE urbex_objects ADD COLUMN entryDifficulty TEXT NOT NULL DEFAULT 'MODERATE'")
                db.execSQL("ALTER TABLE urbex_objects ADD COLUMN floorsOrDepth TEXT")
                db.execSQL("ALTER TABLE urbex_objects ADD COLUMN customParamsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE urbex_objects ADD COLUMN zoneJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE urbex_objects ADD COLUMN zoneColor TEXT")
                db.execSQL("ALTER TABLE urbex_objects ADD COLUMN zoneStyle TEXT NOT NULL DEFAULT 'SOLID'")
                db.execSQL("ALTER TABLE urbex_objects ADD COLUMN zoneHatched INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE urbex_objects ADD COLUMN photosJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE urbex_objects ADD COLUMN icon TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE urbex_objects ADD COLUMN subtype TEXT")
            }
        }

        fun getInstance(context: Context): UrbexDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }
        }

        private fun buildDatabase(appContext: Context): UrbexDatabase {
            return Room.databaseBuilder(
                appContext,
                UrbexDatabase::class.java,
                "urbex_t.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
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

package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StudentEntity::class,
        StudentProfileEntity::class,
        CardEntity::class,
        ScanLogEntity::class,
        SyncMetadataEntity::class,
        PendingChangeEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao
    abstract fun studentProfileDao(): StudentProfileDao
    abstract fun cardDao(): CardDao
    abstract fun scanLogDao(): ScanLogDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun pendingChangeDao(): PendingChangeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Non-destructive migration from Version 4 to Version 5:
         * Adds `qrToken` column with default empty string to `students` table.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `students` ADD COLUMN `qrToken` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Non-destructive migration from Version 5 to Version 6:
         * Adds `pending_changes` table and indices for offline-first change tracking queue.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_changes` (
                        `changeId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `recordId` TEXT NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `payloadJson` TEXT,
                        `status` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastError` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `lastAttemptAt` INTEGER,
                        PRIMARY KEY(`changeId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_changes_status` ON `pending_changes` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_changes_createdAt` ON `pending_changes` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_changes_entityType_recordId` ON `pending_changes` (`entityType`, `recordId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "oakridge_student_access.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                .allowMainThreadQueries()
                .build()
        }
    }
}

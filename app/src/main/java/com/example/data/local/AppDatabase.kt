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
        PendingChangeEntity::class,
        MealRecordEntity::class,
        AttendanceRecordEntity::class,
        ExamExceptionEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao
    abstract fun studentProfileDao(): StudentProfileDao
    abstract fun cardDao(): CardDao
    abstract fun scanLogDao(): ScanLogDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun pendingChangeDao(): PendingChangeDao
    abstract fun mealRecordDao(): MealRecordDao
    abstract fun attendanceRecordDao(): AttendanceRecordDao
    abstract fun examExceptionDao(): ExamExceptionDao

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

        /**
         * Non-destructive migration from Version 6 to Version 7:
         * Adds multi-department operational tables:
         * 1. `meal_records` with unique constraint on (studentId, mealType, mealDate)
         * 2. `attendance_records` with unique constraint on (studentId, lessonDate, sessionName)
         * 3. `exam_exceptions` with unique constraint on (studentId, examCode)
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Meal records table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `meal_records` (
                        `id` TEXT NOT NULL,
                        `studentId` TEXT NOT NULL,
                        `studentNumber` TEXT NOT NULL,
                        `studentName` TEXT NOT NULL,
                        `gradeClass` TEXT NOT NULL,
                        `mealType` TEXT NOT NULL,
                        `mealDate` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `serverName` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `notes` TEXT NOT NULL DEFAULT '',
                        `isSyncedToCloud` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_meal_records_studentId_mealType_mealDate` ON `meal_records` (`studentId`, `mealType`, `mealDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_records_mealDate` ON `meal_records` (`mealDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_records_isSyncedToCloud` ON `meal_records` (`isSyncedToCloud`)")

                // 2. Attendance records table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attendance_records` (
                        `id` TEXT NOT NULL,
                        `studentId` TEXT NOT NULL,
                        `studentNumber` TEXT NOT NULL,
                        `studentName` TEXT NOT NULL,
                        `gradeClass` TEXT NOT NULL,
                        `subject` TEXT NOT NULL,
                        `sessionName` TEXT NOT NULL,
                        `lessonDate` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `teacherName` TEXT NOT NULL,
                        `isPresent` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL DEFAULT '',
                        `isCorrection` INTEGER NOT NULL DEFAULT 0,
                        `correctedBy` TEXT,
                        `isSyncedToCloud` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_records_studentId_lessonDate_sessionName` ON `attendance_records` (`studentId`, `lessonDate`, `sessionName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_gradeClass_lessonDate` ON `attendance_records` (`gradeClass`, `lessonDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_isSyncedToCloud` ON `attendance_records` (`isSyncedToCloud`)")

                // 3. Examination exceptions table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exam_exceptions` (
                        `id` TEXT NOT NULL,
                        `studentId` TEXT NOT NULL,
                        `studentNumber` TEXT NOT NULL,
                        `studentName` TEXT NOT NULL,
                        `examCode` TEXT NOT NULL,
                        `requestedByBursar` TEXT NOT NULL,
                        `recommendationReason` TEXT NOT NULL,
                        `isApproved` INTEGER NOT NULL,
                        `approvedByHeadTeacher` TEXT,
                        `requestedAt` INTEGER NOT NULL,
                        `approvedAt` INTEGER,
                        `isSyncedToCloud` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exam_exceptions_studentId_examCode` ON `exam_exceptions` (`studentId`, `examCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_exceptions_isApproved` ON `exam_exceptions` (`isApproved`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_exceptions_isSyncedToCloud` ON `exam_exceptions` (`isSyncedToCloud`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "oakridge_student_access.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .allowMainThreadQueries()
                .build()
        }
    }
}

package com.gyan.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubjectEntity::class, SessionEntity::class, AttendanceEntity::class,
        AttendanceOverrideEntity::class,
        ClassNoteEntity::class,
        TaskEntity::class,
        ExpenseEntity::class, BudgetEntity::class, SubscriptionEntity::class, WarrantyEntity::class,
        ScholarshipEntity::class, StudyFileEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class GyanDatabase : RoomDatabase() {
    abstract fun dao(): GyanDao

    suspend fun <T> inTransaction(block: suspend GyanDao.() -> T): T =
        withTransaction { dao().block() }

    companion object {
        @Volatile private var instance: GyanDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add totalUnits column to subjects
                db.execSQL("ALTER TABLE subjects ADD COLUMN totalUnits INTEGER NOT NULL DEFAULT 5")

                // Add isLab column to sessions
                db.execSQL("ALTER TABLE sessions ADD COLUMN isLab INTEGER NOT NULL DEFAULT 0")

                // Attendance table: old PK was (subjectId, dayMillis)
                // New PK is (subjectId, dayMillis, sessionId) + isLab column
                // SQLite doesn't support altering PKs, so recreate the table.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attendance_new (
                        subjectId INTEGER NOT NULL,
                        dayMillis INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        sessionId INTEGER NOT NULL DEFAULT 0,
                        isLab INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(subjectId, dayMillis, sessionId)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO attendance_new (subjectId, dayMillis, status, sessionId, isLab)
                    SELECT subjectId, dayMillis, status, 0, 0 FROM attendance
                """.trimIndent())
                db.execSQL("DROP TABLE attendance")
                db.execSQL("ALTER TABLE attendance_new RENAME TO attendance")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add oneOffDateMillis to sessions (nullable → NULL means it's a recurring weekly session)
                db.execSQL("ALTER TABLE sessions ADD COLUMN oneOffDateMillis INTEGER")

                // New attendance_overrides table for mid-semester starting values
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attendance_overrides (
                        subjectId INTEGER NOT NULL,
                        isLab INTEGER NOT NULL DEFAULT 0,
                        attendedBefore INTEGER NOT NULL DEFAULT 0,
                        totalBefore INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(subjectId, isLab)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attendance_overrides ADD COLUMN startingPercent REAL")
                // Earlier percentage-only setup used a fake denominator of 100.
                // Reclassify it as a percentage-only baseline so forecasts do
                // not pretend that 100 real classes were held.
                db.execSQL("UPDATE attendance_overrides SET startingPercent = CASE WHEN totalBefore > 0 THEN attendedBefore * 100.0 / totalBefore ELSE NULL END, attendedBefore = 0, totalBefore = 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS class_notes (
                        subjectId INTEGER NOT NULL,
                        sessionId INTEGER NOT NULL,
                        dayMillis INTEGER NOT NULL,
                        topic TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(subjectId, sessionId, dayMillis)
                    )
                """.trimIndent())
            }
        }

        fun get(context: Context): GyanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GyanDatabase::class.java,
                    "gyan.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { instance = it }
            }
    }
}

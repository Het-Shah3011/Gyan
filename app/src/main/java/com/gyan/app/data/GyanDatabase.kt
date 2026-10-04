package com.gyan.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubjectEntity::class, SessionEntity::class, AttendanceEntity::class, TaskEntity::class,
        ExpenseEntity::class, BudgetEntity::class, SubscriptionEntity::class, WarrantyEntity::class,
        ScholarshipEntity::class, StudyFileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GyanDatabase : RoomDatabase() {
    abstract fun dao(): GyanDao

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

        fun get(context: Context): GyanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GyanDatabase::class.java,
                    "gyan.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}

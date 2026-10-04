package com.gyan.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String = "",
    val semester: String = "",
    val colorArgb: Long = 0xFF6750A4,
    val minAttendance: Float = 75f,
    /** Total number of units/chapters in this subject (0 = unset, uses default 5) */
    val totalUnits: Int = 5
)

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("subjectId")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val dayOfWeek: Int,          // Calendar.MONDAY..SUNDAY
    val startMinutes: Int,       // minutes from midnight
    val endMinutes: Int,
    val room: String = "",
    /** True = lab session; lab attendance is counted separately from theory */
    val isLab: Boolean = false
)

/**
 * Attendance is now tracked per SESSION (not just per subject+day).
 * sessionId = 0 is reserved for legacy / manual entries not tied to a slot.
 */
@Entity(
    tableName = "attendance",
    primaryKeys = ["subjectId", "dayMillis", "sessionId"]
)
data class AttendanceEntity(
    val subjectId: Long,
    val dayMillis: Long,         // start-of-day millis
    val status: String,          // PRESENT / ABSENT
    /** The SessionEntity.id this record belongs to. 0 = not tied to a slot. */
    val sessionId: Long = 0L,
    /** True = lab session record; counted separately */
    val isLab: Boolean = false
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectId: Long? = null,
    val type: String = "TASK",   // TASK / ASSIGNMENT / EXAM
    val dueMillis: Long? = null,
    val status: String = "TODO", // TODO / DONE
    val notes: String = "",
    val reminderMinutesBefore: Long = 60L
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Float,
    val category: String,
    val note: String = "",
    val dateMillis: Long,
    val isIncome: Boolean = false
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,   // use "TOTAL" for the monthly budget
    val monthlyAmount: Float
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Float,
    val cycleDays: Int = 30,
    val nextRenewalMillis: Long,
    val notes: String = ""
)

@Entity(tableName = "warranties")
data class WarrantyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val product: String,
    val purchaseMillis: Long,
    val warrantyMonths: Int,
    val notes: String = "",
    val receiptPath: String = ""
)

@Entity(tableName = "scholarships")
data class ScholarshipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Float = 0f,
    val deadlineMillis: Long? = null,
    val requirements: String = "",
    val status: String = "TRACKING", // TRACKING / APPLIED / APPROVED / REJECTED
    val paid: Boolean = false
)

@Entity(tableName = "studyfiles")
data class StudyFileEntity(
    @PrimaryKey val path: String,
    val displayName: String,
    val subjectId: Long? = null,
    val category: String = "INBOX",  // INBOX or e.g. "Unit 1", "Assignments"
    val addedMillis: Long = System.currentTimeMillis()
)

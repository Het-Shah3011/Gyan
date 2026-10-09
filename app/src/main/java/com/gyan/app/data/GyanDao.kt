package com.gyan.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GyanDao {

    // ---- Subjects ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSubject(s: SubjectEntity): Long
    @Delete suspend fun deleteSubject(s: SubjectEntity)
    @Query("SELECT * FROM subjects ORDER BY name") fun subjectsFlow(): Flow<List<SubjectEntity>>
    @Query("SELECT * FROM subjects") suspend fun subjectsOnce(): List<SubjectEntity>
    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1") suspend fun subjectById(id: Long): SubjectEntity?

    // ---- Timetable sessions ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(s: SessionEntity): Long
    @Delete suspend fun deleteSession(s: SessionEntity)
    @Query("SELECT * FROM sessions") fun sessionsFlow(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions") suspend fun sessionsOnce(): List<SessionEntity>

    // ---- Attendance ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun markAttendance(a: AttendanceEntity)
    @Query("DELETE FROM attendance WHERE subjectId = :sid AND dayMillis = :day AND sessionId = :sessionId")
    suspend fun clearAttendance(sid: Long, day: Long, sessionId: Long)
    @Query("SELECT * FROM attendance") fun attendanceFlow(): Flow<List<AttendanceEntity>>
    @Query("SELECT * FROM attendance") suspend fun attendanceOnce(): List<AttendanceEntity>

    // ---- Attendance overrides (mid-semester starting values) ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAttendanceOverride(o: AttendanceOverrideEntity)
    @Query("SELECT * FROM attendance_overrides") fun attendanceOverridesFlow(): Flow<List<AttendanceOverrideEntity>>
    @Query("SELECT * FROM attendance_overrides") suspend fun attendanceOverridesOnce(): List<AttendanceOverrideEntity>
    @Query("DELETE FROM attendance_overrides WHERE subjectId = :subjectId AND isLab = :isLab")
    suspend fun clearAttendanceOverride(subjectId: Long, isLab: Boolean)
    @Query("DELETE FROM attendance_overrides") suspend fun clearAttendanceOverrides()
    @Query("DELETE FROM class_notes") suspend fun clearClassNotes()

    // ---- Per-meeting topic notes ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertClassNote(note: ClassNoteEntity)
    @Query("SELECT * FROM class_notes") fun classNotesFlow(): Flow<List<ClassNoteEntity>>
    @Query("SELECT * FROM class_notes") suspend fun classNotesOnce(): List<ClassNoteEntity>
    @Query("DELETE FROM class_notes WHERE subjectId = :subjectId AND sessionId = :sessionId AND dayMillis = :dayMillis")
    suspend fun deleteClassNote(subjectId: Long, sessionId: Long, dayMillis: Long)
    @Query("DELETE FROM class_notes WHERE subjectId = :subjectId") suspend fun deleteClassNotesForSubject(subjectId: Long)
    @Query("DELETE FROM class_notes WHERE sessionId = :sessionId") suspend fun deleteClassNotesForSession(sessionId: Long)

    // ---- Tasks / assignments / exams ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTask(t: TaskEntity): Long
    @Delete suspend fun deleteTask(t: TaskEntity)
    @Query("SELECT * FROM tasks ORDER BY status DESC, COALESCE(dueMillis, 4102444800000)") fun tasksFlow(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks") suspend fun tasksOnce(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE dueMillis IS NOT NULL AND dueMillis > :now AND dueMillis < :limit AND status = 'TODO'")
    suspend fun upcomingTasks(now: Long, limit: Long): List<TaskEntity>

    // ---- Money ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertExpense(e: ExpenseEntity)
    @Delete suspend fun deleteExpense(e: ExpenseEntity)
    @Query("SELECT * FROM expenses ORDER BY dateMillis DESC") fun expensesFlow(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM expenses") suspend fun expensesOnce(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBudget(b: BudgetEntity)
    @Delete suspend fun deleteBudget(b: BudgetEntity)
    @Query("SELECT * FROM budgets") fun budgetsFlow(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets") suspend fun budgetsOnce(): List<BudgetEntity>

    // ---- Subscriptions ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSubscription(s: SubscriptionEntity): Long
    @Delete suspend fun deleteSubscription(s: SubscriptionEntity)
    @Query("SELECT * FROM subscriptions ORDER BY nextRenewalMillis") fun subscriptionsFlow(): Flow<List<SubscriptionEntity>>
    @Query("SELECT * FROM subscriptions") suspend fun subscriptionsOnce(): List<SubscriptionEntity>

    // ---- Warranties ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWarranty(x: WarrantyEntity): Long
    @Delete suspend fun deleteWarranty(x: WarrantyEntity)
    @Query("SELECT * FROM warranties ORDER BY purchaseMillis DESC") fun warrantiesFlow(): Flow<List<WarrantyEntity>>
    @Query("SELECT * FROM warranties") suspend fun warrantiesOnce(): List<WarrantyEntity>

    // ---- Scholarships ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertScholarship(s: ScholarshipEntity): Long
    @Delete suspend fun deleteScholarship(s: ScholarshipEntity)
    @Query("SELECT * FROM scholarships ORDER BY COALESCE(deadlineMillis, 4102444800000)") fun scholarshipsFlow(): Flow<List<ScholarshipEntity>>
    @Query("SELECT * FROM scholarships") suspend fun scholarshipsOnce(): List<ScholarshipEntity>

    // ---- Files ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFile(f: StudyFileEntity)
    @Delete suspend fun deleteFile(f: StudyFileEntity)
    @Query("SELECT * FROM studyfiles ORDER BY addedMillis DESC") fun filesFlow(): Flow<List<StudyFileEntity>>
    @Query("SELECT * FROM studyfiles") suspend fun filesOnce(): List<StudyFileEntity>

    // ---- Clear all (for restore) ----
    @Query("DELETE FROM subjects") suspend fun clearSubjects()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM attendance") suspend fun clearAttendanceAll()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM expenses") suspend fun clearExpenses()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM subscriptions") suspend fun clearSubscriptions()
    @Query("DELETE FROM warranties") suspend fun clearWarranties()
    @Query("DELETE FROM scholarships") suspend fun clearScholarships()
    @Query("DELETE FROM studyfiles") suspend fun clearFiles()

    suspend fun clearAll() {
        clearSessions(); clearAttendanceAll(); clearAttendanceOverrides(); clearTasks()
        clearClassNotes(); clearExpenses(); clearBudgets(); clearSubscriptions(); clearWarranties()
        clearScholarships(); clearFiles(); clearSubjects()
    }

    /** Clear schedule rows only. Subjects and personal attendance remain on this device. */
    suspend fun clearTimetableOnly() {
        clearSessions()
    }
}

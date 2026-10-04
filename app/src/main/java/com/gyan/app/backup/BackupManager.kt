package com.gyan.app.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.gyan.app.data.AttendanceEntity
import com.gyan.app.data.BudgetEntity
import com.gyan.app.data.ExpenseEntity
import com.gyan.app.data.GyanRepository
import com.gyan.app.data.ScholarshipEntity
import com.gyan.app.data.SessionEntity
import com.gyan.app.data.StudyFileEntity
import com.gyan.app.data.SubjectEntity
import com.gyan.app.data.SubscriptionEntity
import com.gyan.app.data.TaskEntity
import com.gyan.app.data.WarrantyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val subjects: List<SubjectEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val attendance: List<AttendanceEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val warranties: List<WarrantyEntity> = emptyList(),
    val scholarships: List<ScholarshipEntity> = emptyList(),
    val studyFiles: List<StudyFileEntity> = emptyList()
)

object BackupManager {
    private val gson = Gson()

    suspend fun export(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dao = GyanRepository.get(context).dao
            val data = BackupData(
                subjects = dao.subjectsOnce(),
                sessions = dao.sessionsOnce(),
                attendance = dao.attendanceOnce(),
                tasks = dao.tasksOnce(),
                expenses = dao.expensesOnce(),
                budgets = dao.budgetsOnce(),
                subscriptions = dao.subscriptionsOnce(),
                warranties = dao.warrantiesOnce(),
                scholarships = dao.scholarshipsOnce(),
                studyFiles = dao.filesOnce()
            )
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(gson.toJson(data).toByteArray(Charsets.UTF_8))
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun import(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext false
            val data = gson.fromJson(json, BackupData::class.java) ?: return@withContext false
            val dao = GyanRepository.get(context).dao
            dao.clearAll()
            data.subjects.forEach { dao.upsertSubject(it) }
            data.sessions.forEach { dao.upsertSession(it) }
            data.attendance.forEach { dao.markAttendance(it) }
            data.tasks.forEach { dao.upsertTask(it) }
            data.expenses.forEach { dao.upsertExpense(it) }
            data.budgets.forEach { dao.upsertBudget(it) }
            data.subscriptions.forEach { dao.upsertSubscription(it) }
            data.warranties.forEach { dao.upsertWarranty(it) }
            data.scholarships.forEach { dao.upsertScholarship(it) }
            data.studyFiles.forEach { dao.upsertFile(it) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

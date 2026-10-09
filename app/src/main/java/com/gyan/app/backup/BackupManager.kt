package com.gyan.app.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.gyan.app.data.AttendanceEntity
import com.gyan.app.data.AttendanceOverrideEntity
import com.gyan.app.data.BudgetEntity
import com.gyan.app.data.ClassNoteEntity
import com.gyan.app.data.ExpenseEntity
import com.gyan.app.data.GyanRepository
import com.gyan.app.data.GyanDatabase
import com.gyan.app.data.ScholarshipEntity
import com.gyan.app.data.SessionEntity
import com.gyan.app.data.StudyFileEntity
import com.gyan.app.data.SubjectEntity
import com.gyan.app.data.SubscriptionEntity
import com.gyan.app.data.TaskEntity
import com.gyan.app.data.WarrantyEntity
import com.gyan.app.reminders.LectureAlarmScheduler
import com.gyan.app.reminders.ReminderScheduler
import com.gyan.app.reminders.BootReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backup payload stored in a .het file.
 *
 * The .het file is JSON with a GYAN format marker so the app can validate it.
 * Extension .het = Het Shah's app format — treated as "application/octet-stream"
 * by most file managers, so we also accept text/plain for flexibility.
 *
 * version history:
 *   1 → initial JSON backup
 *   2 → added attendanceOverrides
 *   3 → sessions now have oneOffDateMillis
 */
data class BackupData(
    val magic: String = HET_MAGIC,
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val subjects: List<SubjectEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val attendance: List<AttendanceEntity> = emptyList(),
    val attendanceOverrides: List<AttendanceOverrideEntity> = emptyList(),
    val classNotes: List<ClassNoteEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val warranties: List<WarrantyEntity> = emptyList(),
    val scholarships: List<ScholarshipEntity> = emptyList(),
    val studyFiles: List<StudyFileEntity> = emptyList()
) {
    companion object {
        const val HET_MAGIC = "GYAN_HET_BACKUP"
        const val CURRENT_VERSION = 4
    }
}

sealed class ImportResult {
    data class Success(val message: String) : ImportResult()
    data class Error(val reason: String) : ImportResult()
}

object BackupManager {
    private val gson = Gson()

    // ─────────────────────────────────────────────────────────────────────────
    // Export
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun export(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dao = GyanRepository.get(context).dao
            val data = BackupData(
                subjects           = dao.subjectsOnce(),
                sessions           = dao.sessionsOnce(),
                attendance         = dao.attendanceOnce(),
                attendanceOverrides= dao.attendanceOverridesOnce(),
                classNotes          = dao.classNotesOnce(),
                tasks              = dao.tasksOnce(),
                expenses           = dao.expensesOnce(),
                budgets            = dao.budgetsOnce(),
                subscriptions      = dao.subscriptionsOnce(),
                warranties         = dao.warrantiesOnce(),
                scholarships       = dao.scholarshipsOnce(),
                studyFiles         = dao.filesOnce()
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

    /** Export just the shared schedule so classmates do not receive private data. */
    suspend fun exportTimetable(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dao = GyanRepository.get(context).dao
            val data = BackupData(
                subjects = dao.subjectsOnce(),
                sessions = dao.sessionsOnce()
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

    // ─────────────────────────────────────────────────────────────────────────
    // Full import (replaces everything)
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun import(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val json = readJson(context, uri) ?: return@withContext ImportResult.Error("Could not read file")
            val data = parseAndValidate(json) ?: return@withContext ImportResult.Error(
                "Invalid .het file — this doesn't look like a GYAN backup"
            )
            val dao = GyanRepository.get(context).dao
            val oldSessions = dao.sessionsOnce()
            val oldTasks = dao.tasksOnce()
            LectureAlarmScheduler.cancelSessions(context, oldSessions)
            oldTasks.forEach { ReminderScheduler.cancel(context, BootReceiver.requestCodeFor(it.id)) }
            GyanDatabase.get(context).inTransaction {
                clearAll()
                restoreAll(this, data)
            }
            rescheduleLectures(context)
            rescheduleTaskReminders(context)
            ImportResult.Success("Full backup restored ✅")
        } catch (e: Exception) {
            e.printStackTrace()
            runCatching { rescheduleLectures(context) }
            runCatching { rescheduleTaskReminders(context) }
            ImportResult.Error("Import failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timetable-only import (keeps money, tasks, files, etc. intact)
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun importTimetableOnly(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val json = readJson(context, uri) ?: return@withContext ImportResult.Error("Could not read file")
            val data = parseAndValidate(json) ?: return@withContext ImportResult.Error(
                "Invalid .het file — this doesn't look like a GYAN backup"
            )
            val dao = GyanRepository.get(context).dao
            val oldSessions = dao.sessionsOnce()
            LectureAlarmScheduler.cancelSessions(context, oldSessions)
            GyanDatabase.get(context).inTransaction {
                clearTimetableOnly()

                // Match shared subjects to local subjects so tasks, files and
                // attendance keep pointing at this device's own subject IDs.
                val localSubjects = subjectsOnce().toMutableList()
                val importedIds = mutableMapOf<Long, Long>()
                for (incoming in data.subjects) {
                    val local = localSubjects.firstOrNull { subjectMatches(it, incoming) }
                    val localId = if (local != null) {
                        upsertSubject(
                            incoming.copy(
                                id = local.id,
                                minAttendance = local.minAttendance,
                                totalUnits = local.totalUnits,
                                colorArgb = local.colorArgb
                            )
                        )
                        local.id
                    } else {
                        upsertSubject(incoming.copy(id = 0L))
                    }
                    importedIds[incoming.id] = localId
                    subjectById(localId)?.let { saved ->
                        localSubjects.removeAll { it.id == saved.id }
                        localSubjects.add(saved)
                    }
                }

                data.sessions.forEach { incoming ->
                    val localSubjectId = importedIds[incoming.subjectId]
                        ?: throw IllegalArgumentException("Timetable class refers to a missing subject")
                    upsertSession(incoming.copy(id = 0L, subjectId = localSubjectId))
                }
            }
            rescheduleLectures(context)
            ImportResult.Success("Timetable imported. Your attendance, tasks, money and files are unchanged.")
        } catch (e: Exception) {
            e.printStackTrace()
            runCatching { rescheduleLectures(context) }
            ImportResult.Error("Import failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun readJson(context: Context, uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun parseAndValidate(json: String): BackupData? {
        return try {
            val root = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val hasMagic = root.has("magic") && !root.get("magic").isJsonNull
            if (hasMagic && root.get("magic").asString != BackupData.HET_MAGIC) return null
            if (!root.has("subjects") || !root.get("subjects").isJsonArray ||
                !root.has("sessions") || !root.get("sessions").isJsonArray
            ) return null
            val knownSections = listOf(
                "subjects", "sessions", "attendance", "tasks", "expenses", "budgets",
                "subscriptions", "warranties", "scholarships", "studyFiles"
            )
            if (!hasMagic && knownSections.none { root.has(it) && root.get(it).isJsonArray }) return null
            val data = gson.fromJson(json, BackupData::class.java) ?: return null
            if (data.version !in 1..BackupData.CURRENT_VERSION) return null
            data
        } catch (_: Exception) {
            null
        }
    }

    private fun subjectMatches(local: SubjectEntity, incoming: SubjectEntity): Boolean {
        val localCode = local.code.trim()
        val incomingCode = incoming.code.trim()
        if (localCode.isNotEmpty() && incomingCode.isNotEmpty()) {
            return localCode.equals(incomingCode, ignoreCase = true)
        }
        return local.name.trim().equals(incoming.name.trim(), ignoreCase = true) &&
            local.semester.trim().equals(incoming.semester.trim(), ignoreCase = true)
    }

    private suspend fun rescheduleLectures(context: Context) {
        val dao = GyanRepository.get(context).dao
        LectureAlarmScheduler.reschedule(
            context,
            dao.sessionsOnce(),
            dao.subjectsOnce(),
            dao.attendanceOnce(),
            dao.attendanceOverridesOnce()
        )
    }

    private suspend fun rescheduleTaskReminders(context: Context) {
        val dao = GyanRepository.get(context).dao
        val now = System.currentTimeMillis()
        dao.upcomingTasks(now, Long.MAX_VALUE).forEach { task ->
            val due = task.dueMillis ?: return@forEach
            ReminderScheduler.schedule(
                context,
                BootReceiver.requestCodeFor(task.id),
                "📚 Keep moving: ${task.title}",
                "You still have this task to finish · Due ${com.gyan.app.ui.formatDateTime(due)}",
                due - task.reminderMinutesBefore * 60_000L
            )
        }
    }

    private suspend fun restoreAll(dao: com.gyan.app.data.GyanDao, data: BackupData) {
        data.subjects.forEach { dao.upsertSubject(it) }
        data.sessions.forEach { dao.upsertSession(it) }
        data.attendance.forEach { dao.markAttendance(it) }
        data.attendanceOverrides.forEach { dao.upsertAttendanceOverride(it) }
        data.classNotes.forEach { dao.upsertClassNote(it) }
        data.tasks.forEach { dao.upsertTask(it) }
        data.expenses.forEach { dao.upsertExpense(it) }
        data.budgets.forEach { dao.upsertBudget(it) }
        data.subscriptions.forEach { dao.upsertSubscription(it) }
        data.warranties.forEach { dao.upsertWarranty(it) }
        data.scholarships.forEach { dao.upsertScholarship(it) }
        data.studyFiles.forEach { dao.upsertFile(it) }
    }
}

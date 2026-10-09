package com.gyan.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gyan.app.data.GyanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = GyanRepository.get(context).dao
                val now = System.currentTimeMillis()

                // ---- Task / exam reminders ----
                dao.upcomingTasks(now, Long.MAX_VALUE).forEach { task ->
                    val due = task.dueMillis ?: return@forEach
                    ReminderScheduler.schedule(
                        context,
                        requestCodeFor(task.id),
                    "📚 Keep moving: ${task.title}",
                    "You still have this task to finish · Due ${com.gyan.app.ui.formatDateTime(due)}",
                        due - task.reminderMinutesBefore * 60_000
                    )
                }

                // ---- Lecture 10-min alarms ----
                val sessions   = dao.sessionsOnce()
                val subjects   = dao.subjectsOnce()
                val attendance = dao.attendanceOnce()
                val overrides  = dao.attendanceOverridesOnce()
                LectureAlarmScheduler.reschedule(context, sessions, subjects, attendance, overrides)

            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun requestCodeFor(taskId: Long) = (100000 + taskId).toInt()
    }
}

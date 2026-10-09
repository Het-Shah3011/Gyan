package com.gyan.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.gyan.app.data.GyanRepository
import com.gyan.app.reminders.LectureAlarmScheduler
import com.gyan.app.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GyanApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Create notification channels (Android 8+)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    ReminderScheduler.CHANNEL_ID,
                    "Study & task nudges",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Reminders for tasks, assignments and exams" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    ReminderScheduler.CHANNEL_LECTURE,
                    "Class starting alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "10-minute heads-up before each class starts" }
            )
        }

        // Schedule lecture alarms for the coming week on every app start
        // (ensures alarms are set even if the user doesn't go through BootReceiver)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = GyanRepository.get(this@GyanApp).dao
                val now = System.currentTimeMillis()
                dao.upcomingTasks(now, Long.MAX_VALUE).forEach { task ->
                    val due = task.dueMillis ?: return@forEach
                    ReminderScheduler.schedule(
                        this@GyanApp,
                        com.gyan.app.reminders.BootReceiver.requestCodeFor(task.id),
                        "📚 Keep moving: ${task.title}",
                        "You still have this task to finish · Due ${com.gyan.app.ui.formatDateTime(due)}",
                        due - task.reminderMinutesBefore * 60_000L
                    )
                }
                val sessions   = dao.sessionsOnce()
                val subjects   = dao.subjectsOnce()
                val attendance = dao.attendanceOnce()
                val overrides  = dao.attendanceOverridesOnce()
                LectureAlarmScheduler.reschedule(this@GyanApp, sessions, subjects, attendance, overrides)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

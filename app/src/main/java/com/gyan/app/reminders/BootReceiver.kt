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
                dao.upcomingTasks(now, now + 48L * 3600_000).forEach { task ->
                    val due = task.dueMillis ?: return@forEach
                    ReminderScheduler.schedule(
                        context,
                        requestCodeFor(task.id),
                        task.title,
                        "Due ${com.gyan.app.ui.formatDateTime(due)}",
                        due - task.reminderMinutesBefore * 60_000
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun requestCodeFor(taskId: Long) = (100000 + taskId).toInt()
    }
}

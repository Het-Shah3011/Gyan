package com.gyan.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object ReminderScheduler {
    const val CHANNEL_ID = "gyan_reminders_v2"
    const val CHANNEL_LECTURE = "gyan_lectures_v2"

    // Request code ranges:
    //   100_000..199_999 → task reminders (BootReceiver.requestCodeFor)
    //   1_000_000+       → weekly lecture alarms, keyed by sessionId + week slot
    //   2_000_000+       → one-off class alarms, keyed by sessionId

    fun schedule(context: Context, requestCode: Int, title: String, message: String, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
            putExtra(ReminderReceiver.EXTRA_CHANNEL, CHANNEL_ID)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    /**
     * Schedule a "class starting in 10 minutes" notification.
     * [requestCode] must be unique per session per day-of-week slot.
     */
    fun scheduleLecture(
        context: Context,
        requestCode: Int,
        subjectName: String,
        timeLabel: String,
        room: String,
        isLab: Boolean,
        triggerAtMillis: Long,
        canMissCount: Int,
        projectedAttendancePercent: Float,
        nextOccurrenceMillis: Long? = null
    ) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val typeTag = if (isLab) "Lab" else "Lecture"
        val missInfo = when {
            canMissCount == Int.MIN_VALUE -> "Add the class count to get a reliable attendance forecast."
            projectedAttendancePercent < 0f -> "Set your starting attendance to see how missing this class would affect it."
            canMissCount > 0 -> "You can still miss $canMissCount class${if (canMissCount > 1) "es" else ""}."
            canMissCount == 0 -> "⚠️ Skipping now will drop you below minimum!"
            else -> "🚨 Attendance already critical!"
        }
        val roomInfo = if (room.isNotBlank()) " • Room $room" else ""
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, "🔔 $typeTag starts in 10 minutes")
            putExtra(ReminderReceiver.EXTRA_MESSAGE, "$subjectName\n$timeLabel$roomInfo\n$missInfo")
            putExtra(ReminderReceiver.EXTRA_CHANNEL, CHANNEL_LECTURE)
            putExtra(ReminderReceiver.EXTRA_CAN_MISS, canMissCount)
            putExtra(ReminderReceiver.EXTRA_SUBJECT, subjectName)
            putExtra(ReminderReceiver.EXTRA_TIME_LABEL, timeLabel)
            putExtra(ReminderReceiver.EXTRA_ROOM, room)
            putExtra(ReminderReceiver.EXTRA_IS_LAB, isLab)
            putExtra(ReminderReceiver.EXTRA_PROJECTED_PERCENT, projectedAttendancePercent)
            putExtra(ReminderReceiver.EXTRA_NEXT_OCCURRENCE, nextOccurrenceMillis ?: -1L)
            putExtra(ReminderReceiver.EXTRA_REQUEST_CODE, requestCode)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    fun cancel(context: Context, requestCode: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    /** Request code for a weekly lecture alarm:  (sessionId * 10 + weekIndex) + base */
    fun lectureRequestCode(sessionId: Long, weekdayIndex: Int = 0): Int =
        (1_000_000 + sessionId * 8 + weekdayIndex).toInt()

    /** Request code for a one-off makeup class alarm */
    fun oneOffRequestCode(sessionId: Long): Int = (2_000_000 + sessionId).toInt()
}

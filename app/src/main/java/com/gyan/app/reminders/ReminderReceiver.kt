package com.gyan.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gyan.app.MainActivity
import com.gyan.app.R

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TITLE   = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_CHANNEL = "extra_channel"
        const val EXTRA_CAN_MISS = "extra_can_miss"   // -1 = critical, 0 = on edge, n = can miss n
        const val EXTRA_SUBJECT  = "extra_subject"
        const val EXTRA_TIME_LABEL = "extra_time_label"
        const val EXTRA_ROOM = "extra_room"
        const val EXTRA_IS_LAB = "extra_is_lab"
        const val EXTRA_PROJECTED_PERCENT = "extra_projected_percent"
        const val EXTRA_NEXT_OCCURRENCE = "extra_next_occurrence"
        const val EXTRA_REQUEST_CODE = "extra_request_code"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val title    = intent.getStringExtra(EXTRA_TITLE)   ?: "GYAN reminder"
        val message  = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val channel  = intent.getStringExtra(EXTRA_CHANNEL) ?: ReminderScheduler.CHANNEL_ID
        val canMiss  = intent.getIntExtra(EXTRA_CAN_MISS, Int.MIN_VALUE)
        val subject  = intent.getStringExtra(EXTRA_SUBJECT) ?: ""
        val nextOccurrence = intent.getLongExtra(EXTRA_NEXT_OCCURRENCE, -1L)
        val projectedPct = intent.getFloatExtra(EXTRA_PROJECTED_PERCENT, 0f)

        ensureChannels(context)

        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF4F46E5.toInt())
            .setCategory(if (channel == ReminderScheduler.CHANNEL_LECTURE) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(pi)

        // For lecture notifications: show attendance-aware sub-text
        if (canMiss != Int.MIN_VALUE && channel == ReminderScheduler.CHANNEL_LECTURE) {
            val subText = when {
                projectedPct < 0f -> "Attendance forecast needs a class count"
                canMiss > 0  -> "✅ Safe to skip (can miss $canMiss more)"
                canMiss == 0 -> "⚠️ Skipping risks dropping below minimum"
                else         -> "🚨 Attendance critical — don't miss this!"
            }
            builder.setSubText(subText)
            val attendanceImpact = if (projectedPct < 0f) {
                "No starting attendance is recorded yet."
            } else {
                "If you miss this one: ${"%.1f".format(projectedPct)}% attendance."
            }
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$message\n$attendanceImpact"))
            // Color-code the notification icon
            builder.color = when {
                projectedPct < 0f -> 0xFF64748B.toInt()
                canMiss > 0  -> 0xFF22C55E.toInt()   // green
                canMiss == 0 -> 0xFFFFA726.toInt()   // amber
                else         -> 0xFFEF4444.toInt()   // red
            }
        }

        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100_000).toInt(), builder.build())
        }

        // Chain the next weekly occurrence so reminders continue even if the
        // app is not opened again. One-off classes have no next occurrence.
        if (channel == ReminderScheduler.CHANNEL_LECTURE && nextOccurrence > System.currentTimeMillis()) {
            ReminderScheduler.scheduleLecture(
                context = context,
                requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0),
                subjectName = subject,
                timeLabel = intent.getStringExtra(EXTRA_TIME_LABEL) ?: "",
                room = intent.getStringExtra(EXTRA_ROOM) ?: "",
                isLab = intent.getBooleanExtra(EXTRA_IS_LAB, false),
                triggerAtMillis = nextOccurrence,
                canMissCount = canMiss,
                projectedAttendancePercent = projectedPct,
                nextOccurrenceMillis = java.util.Calendar.getInstance().apply {
                    timeInMillis = nextOccurrence
                    add(java.util.Calendar.DAY_OF_YEAR, 7)
                }.timeInMillis
            )
        }
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(ReminderScheduler.CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    ReminderScheduler.CHANNEL_ID,
                    "Study & task nudges",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Upcoming tasks, assignments and exam reminders" }
            )
        }
        if (nm.getNotificationChannel(ReminderScheduler.CHANNEL_LECTURE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    ReminderScheduler.CHANNEL_LECTURE,
                    "Class starting alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "10-minute heads-up before each class starts" }
            )
        }
    }
}

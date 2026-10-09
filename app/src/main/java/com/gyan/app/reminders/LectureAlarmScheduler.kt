package com.gyan.app.reminders

import android.content.Context
import com.gyan.app.data.AttendanceEntity
import com.gyan.app.data.AttendanceOverrideEntity
import com.gyan.app.data.SessionEntity
import com.gyan.app.data.SubjectEntity
import com.gyan.app.ui.minutesLabel
import com.gyan.app.ui.attendanceStats
import java.util.Calendar

/**
 * Schedules (or cancels) 10-minute-before-lecture alarms for all sessions
 * in the next 7 days. Call this whenever timetable or attendance changes.
 */
object LectureAlarmScheduler {

    private const val LOOK_AHEAD_DAYS = 7

    /**
     * Reschedule all lecture alarms for the coming week.
     *
     * @param sessions       All sessions in the DB.
     * @param subjects       All subjects (to look up canMiss).
     * @param attendance     All attendance records.
     * @param overrides      Attendance overrides (mid-semester starting values).
     * @param context        Android context.
     */
    fun reschedule(
        context: Context,
        sessions: List<SessionEntity>,
        subjects: List<SubjectEntity>,
        attendance: List<AttendanceEntity>,
        overrides: List<AttendanceOverrideEntity>
    ) {
        if (!context.getSharedPreferences("gyan_preferences", Context.MODE_PRIVATE)
                .getBoolean("class_reminders_enabled", true)) {
            cancelSessions(context, sessions)
            return
        }
        val subjectMap = subjects.associateBy { it.id }
        val now = System.currentTimeMillis()

        // Cancel all previously scheduled lecture alarms (by iterating known range)
        // We cancel by re-creating PendingIntents with the same codes.
        cancelSessions(context, sessions)

        val cal = Calendar.getInstance()

        sessions.forEach { session ->
            val sub = subjectMap[session.subjectId] ?: return@forEach

            // Compute canMiss for this subject/type
            val isLab = session.isLab
            val subAttendance = attendance.filter { it.subjectId == sub.id && it.isLab == isLab }
            val override = overrides.firstOrNull { it.subjectId == sub.id && it.isLab == isLab }
            val stats = attendanceStats(subAttendance, sub.minAttendance, override)
            val canMiss = if (!stats.forecastAvailable) Int.MIN_VALUE
                else if (stats.pct >= sub.minAttendance) stats.canMiss else -1
            val projectedPct = if (!stats.forecastAvailable) -1f
                else stats.attended * 100f / (stats.total + 1)

            val timeLabel = "${minutesLabel(session.startMinutes)} – ${minutesLabel(session.endMinutes)}"

            if (session.oneOffDateMillis != null) {
                // One-off makeup class: schedule for that specific date only
                val triggerAt = session.oneOffDateMillis + session.startMinutes * 60_000L - 10 * 60_000L
                if (triggerAt > now) {
                    ReminderScheduler.scheduleLecture(
                        context,
                        requestCode    = ReminderScheduler.oneOffRequestCode(session.id),
                        subjectName    = sub.name,
                        timeLabel      = timeLabel,
                        room           = session.room,
                        isLab          = isLab,
                        triggerAtMillis = triggerAt,
                        canMissCount   = canMiss,
                        projectedAttendancePercent = projectedPct
                    )
                }
            } else {
                // Recurring weekly: schedule for each occurrence in the next LOOK_AHEAD_DAYS
                for (offset in 0 until LOOK_AHEAD_DAYS) {
                    cal.timeInMillis = now
                    cal.add(Calendar.DAY_OF_YEAR, offset)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                    if (dayOfWeek != session.dayOfWeek) continue

                    val triggerAt = cal.timeInMillis + session.startMinutes * 60_000L - 10 * 60_000L
                    if (triggerAt <= now) continue

                    val nextOccurrence = Calendar.getInstance().apply {
                        timeInMillis = triggerAt
                        add(Calendar.DAY_OF_YEAR, 7)
                    }.timeInMillis

                    ReminderScheduler.scheduleLecture(
                        context,
                        requestCode     = ReminderScheduler.lectureRequestCode(session.id, offset),
                        subjectName     = sub.name,
                        timeLabel       = timeLabel,
                        room            = session.room,
                        isLab           = isLab,
                        triggerAtMillis = triggerAt,
                        canMissCount    = canMiss,
                        projectedAttendancePercent = projectedPct,
                        nextOccurrenceMillis = nextOccurrence
                    )
                    break  // Only one alarm per session per week; AlarmManager is not repeating
                }
            }
        }
    }

    fun cancelSessions(context: Context, sessions: List<SessionEntity>) {
        sessions.forEach { session ->
            for (weekSlot in 0 until LOOK_AHEAD_DAYS) {
                ReminderScheduler.cancel(context, ReminderScheduler.lectureRequestCode(session.id, weekSlot))
            }
            ReminderScheduler.cancel(context, ReminderScheduler.oneOffRequestCode(session.id))
        }
    }
}

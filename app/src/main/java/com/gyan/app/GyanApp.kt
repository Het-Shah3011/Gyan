package com.gyan.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.gyan.app.reminders.ReminderScheduler

class GyanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ReminderScheduler.CHANNEL_ID, "GYAN Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}

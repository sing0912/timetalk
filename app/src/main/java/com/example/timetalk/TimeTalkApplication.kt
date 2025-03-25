package com.example.timetalk

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager

class TimeTalkApplication : Application(), Configuration.Provider {
    private val TAG = "TimeTalkApplication"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Initialize WorkManager
        WorkManager.initialize(
            this,
            getWorkManagerConfiguration()
        )
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "time_announcement",
                "Time Announcement",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for time announcement notifications"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }
} 
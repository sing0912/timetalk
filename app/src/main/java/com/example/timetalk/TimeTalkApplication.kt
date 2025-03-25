package com.example.timetalk

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.work.Configuration

class TimeTalkApplication : Application(), Configuration.Provider {
    private val TAG = "TimeTalkApplication"

    override fun onCreate() {
        super.onCreate()
        
        // 알림 채널 생성
        createNotificationChannel()
        
        // 배터리 최적화 설정 확인
        checkBatteryOptimization()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 시간 알림용 채널
            val channel = NotificationChannel(
                "time_announcement",
                "Time Announcement",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for time announcement notifications"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "알림 채널 생성 완료")
        }
    }
    
    private fun checkBatteryOptimization() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "배터리 최적화가 활성화되어 있습니다. 앱이 백그라운드에서 제한될 수 있습니다.")
            } else {
                Log.d(TAG, "배터리 최적화가 비활성화되어 있습니다. 앱이 백그라운드에서 정상 동작합니다.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "배터리 최적화 설정 확인 중 오류 발생", e)
        }
    }
} 
} 
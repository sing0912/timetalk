package com.example.timetalk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import io.mockk.mockk

/**
 * 테스트 목적의 NotificationHelper 클래스
 */
class NotificationHelper {
    companion object {
        const val CHANNEL_ID = "time_announcement"
        
        fun createNotificationChannel(context: Context) {
            // 테스트 환경에서는 아무 작업도 하지 않음
        }
    }
} 
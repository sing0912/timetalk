package com.example.timetalk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager

/**
 * 백그라운드 모드를 제어하기 위한 BroadcastReceiver
 * 알림에서 "종료" 버튼을 눌렀을 때 작업을 처리
 */
class BackgroundControlReceiver : BroadcastReceiver() {
    private val TAG = "BackgroundControlReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.example.timetalk.EXIT_BACKGROUND" -> {
                Log.d(TAG, "★★★★★★★★★★ 백그라운드 모드 종료 요청 수신 ★★★★★★★★★★")
                
                // 모든 작업 취소
                WorkManager.getInstance(context).cancelAllWork()
                
                // MainActivity로 알림이 발생했음을 전달하는 인텐트 생성
                val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXIT_BACKGROUND", true)
                }
                
                // MainActivity 시작
                context.startActivity(mainActivityIntent)
                
                // 알림 취소
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(1)
                
                Log.d(TAG, "★★★★★★★★★★ 모든 작업 취소 및 MainActivity 실행 ★★★★★★★★★★")
            }
        }
    }
} 
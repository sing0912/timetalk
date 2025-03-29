package com.example.timetalk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 테스트 목적의 가짜 BackgroundControlReceiver
 */
class BackgroundControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 테스트용 더미 클래스이므로 실제 동작은 구현하지 않음
    }
} 
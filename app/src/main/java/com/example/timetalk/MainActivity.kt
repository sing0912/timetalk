package com.example.timetalk

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var announceTimeButton: Button
    private lateinit var statusTextView: TextView
    private var isTtsReady = false
    
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 초기화
        announceTimeButton = findViewById(R.id.startButton)
        statusTextView = findViewById(R.id.statusTextView)
        
        // TTS 초기화
        tts = TextToSpeech(this, this)
        
        // 버튼 클릭 이벤트 설정
        announceTimeButton.setOnClickListener {
            if (isTtsReady) {
                announceCurrentTime()
            } else {
                Toast.makeText(this, "TTS가 아직 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
            }
        }
        
        updateStatus("TTS 초기화 중...")
    }

    private fun announceCurrentTime() {
        try {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            val timeString = String.format("현재 시각은 %d시 %d분 입니다.", hour, minute)
            Log.d(TAG, "시간 알림: $timeString")
            
            tts.speak(timeString, TextToSpeech.QUEUE_FLUSH, null, "TIME_ANNOUNCE")
            updateStatus("현재 시각: ${hour}시 ${minute}분")
        } catch (e: Exception) {
            Log.e(TAG, "시간 알림 중 오류 발생", e)
            Toast.makeText(this, "시간 알림 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus(status: String) {
        statusTextView.text = status
        Log.d(TAG, "상태 업데이트: $status")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "한국어가 지원되지 않습니다")
                Toast.makeText(this, "한국어가 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
                updateStatus("오류: 한국어 지원되지 않음")
            } else {
                isTtsReady = true
                Log.d(TAG, "TTS 초기화 성공")
                updateStatus("준비 완료")
            }
        } else {
            Log.e(TAG, "TTS 초기화 실패")
            Toast.makeText(this, "TTS 초기화에 실패했습니다.", Toast.LENGTH_SHORT).show()
            updateStatus("오류: TTS 초기화 실패")
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
} 
package com.example.timetalk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*
import androidx.work.WorkManager

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var announceTimeButton: Button
    private lateinit var statusTextView: TextView
    internal var isTtsReady = false
    internal var tts: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val TAG = "MainActivity"
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 초기화
        announceTimeButton = findViewById(R.id.startButton)
        statusTextView = findViewById(R.id.statusTextView)
        
        // TTS 초기화
        tts = TextToSpeech(this, this)
        
        // 권한 체크
        checkPermissions()
        
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

    // 앱에 필요한 권한을 확인하고 요청하는 메서드
    internal fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // 알림 권한 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // 포그라운드 서비스 권한 (Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE)
            }
        }
        
        // 웨이크락 권한
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WAKE_LOCK)
        }
        
        // 필요한 권한 요청
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, 
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun announceCurrentTime() {
        try {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            val timeString = String.format("현재 시각은 %d시 %d분 입니다.", hour, minute)
            Log.d(TAG, "시간 알림: $timeString")
            
            tts?.speak(timeString, TextToSpeech.QUEUE_FLUSH, null, "TIME_ANNOUNCE")
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
            val result = tts?.setLanguage(Locale.KOREAN)
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
        tts?.let {
            it.stop()
            it.shutdown()
        }
        super.onDestroy()
    }
    
    // 주기적인 시간 알림 시작
    internal fun startTimeAnnouncement() {
        Log.d(TAG, "주기적 시간 알림 시작")
        // WorkManager를 사용한 주기적 작업 등록
        if (isTtsReady) {
            val workManager = WorkManager.getInstance(applicationContext)
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<TimeAnnouncementWorker>(
                15, // 15분마다 실행 (테스트용으로 짧게 설정)
                java.util.concurrent.TimeUnit.MINUTES
            ).build()
            
            workManager.enqueueUniquePeriodicWork(
                "time_announcement",
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            
            Toast.makeText(this, "시간 알림이 시작되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "TTS가 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 주기적인 시간 알림 중지
    internal fun stopTimeAnnouncement() {
        Log.d(TAG, "주기적 시간 알림 중지")
        // WorkManager 작업 취소
        val workManager = WorkManager.getInstance(applicationContext)
        workManager.cancelUniqueWork("time_announcement")
    }
} 
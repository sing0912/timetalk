package com.example.timetalk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 메인 액티비티 클래스
 * TextToSpeech.OnInitListener를 구현하여 음성 변환 초기화 콜백을 처리
 */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    // TextToSpeech 객체: 텍스트를 음성으로 변환하는 기능을 제공
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false
    // UI 컴포넌트: 시간 알림 시작 버튼
    private lateinit var startButton: Button
    // UI 컴포넌트: 시간 알림 중지 버튼
    private lateinit var stopButton: Button
    
    private val TAG = "MainActivity"

    /**
     * 액티비티가 생성될 때 호출되는 메소드
     * UI 초기화 및 이벤트 리스너 설정
     * 
     * @param savedInstanceState 이전 상태가 저장된 경우 해당 데이터를 포함하는 Bundle 객체
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        
        // activity_main.xml 레이아웃을 현재 액티비티에 설정
        setContentView(R.layout.activity_main)

        // Initialize TTS
        tts = TextToSpeech(this, this)
        
        // Request necessary permissions
        checkPermissions()

        // 레이아웃에서 버튼 컴포넌트 찾기
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        // 시작 버튼 클릭 이벤트 처리
        startButton.setOnClickListener {
            Log.d(TAG, "Start button clicked")
            startTimeAnnouncement()
        }

        // 중지 버튼 클릭 이벤트 처리
        stopButton.setOnClickListener {
            Log.d(TAG, "Stop button clicked")
            stopTimeAnnouncement()
        }
    }

    /**
     * 알림 권한이 부여되었는지 확인하는 메소드
     * 
     * @return Boolean 권한이 부여된 경우 true, 그렇지 않은 경우 false
     */
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.WAKE_LOCK
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, 1)
        }
    }

    /**
     * 시간 알림을 시작하는 메소드
     * WorkManager를 사용하여 1분마다 실행되는 백그라운드 작업을 스케줄링
     */
    private fun startTimeAnnouncement() {
        Log.d(TAG, "Starting time announcement service")
        
        // Test immediate announcement
        if (isTtsReady) {
            val currentTime = Calendar.getInstance()
            val hour = currentTime.get(Calendar.HOUR_OF_DAY)
            val minute = currentTime.get(Calendar.MINUTE)
            val timeString = String.format("현재 시각은 %d시 %d분 입니다.", hour, minute)
            tts.speak(timeString, TextToSpeech.QUEUE_FLUSH, null, "initial_announcement")
            Log.d(TAG, "Initial announcement: $timeString")
        }

        // Set up WorkManager for periodic announcements
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)  // Allow running when battery is low
            .setRequiresCharging(false)       // Allow running when not charging
            .setRequiresDeviceIdle(false)     // Allow running when device is not idle
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<TimeAnnouncementWorker>(
            1, TimeUnit.MINUTES,  // Repeat every 1 minute
            30, TimeUnit.SECONDS  // Flex interval of 30 seconds
        )
            .setConstraints(constraints)
            .addTag("time_announcement")
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                10, TimeUnit.SECONDS
            )
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "time_announcement",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )

        // Monitor work status
        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData("time_announcement")
            .observe(this) { workInfoList ->
                workInfoList?.forEach { workInfo ->
                    Log.d(TAG, "Work status: ${workInfo.state}")
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            Log.d(TAG, "Work completed successfully")
                        }
                        WorkInfo.State.FAILED -> {
                            Log.e(TAG, "Work failed")
                        }
                        else -> {
                            Log.d(TAG, "Work state: ${workInfo.state}")
                        }
                    }
                }
            }

        Toast.makeText(this, "시간 알림이 시작되었습니다.", Toast.LENGTH_SHORT).show()
    }

    /**
     * 시간 알림을 중지하는 메소드
     * WorkManager를 통해 스케줄링된 작업을 취소
     */
    private fun stopTimeAnnouncement() {
        Log.d(TAG, "Stopping time announcement service")
        WorkManager.getInstance(this).cancelUniqueWork("time_announcement")
        Toast.makeText(this, "시간 알림이 중지되었습니다.", Toast.LENGTH_SHORT).show()
    }

    /**
     * TextToSpeech 초기화 완료 시 호출되는 콜백 메소드
     * 
     * @param status 초기화 상태 (SUCCESS 또는 ERROR)
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Korean language is not supported")
                Toast.makeText(this, "한국어가 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
            } else {
                isTtsReady = true
                Log.d(TAG, "TTS initialized successfully")
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
            Toast.makeText(this, "TTS 초기화에 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 액티비티가 소멸될 때 호출되는 메소드
     * TextToSpeech 리소스 해제
     */
    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
} 
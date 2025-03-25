package com.example.timetalk

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
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
    // Make isTtsReady accessible for testing
    @VisibleForTesting
    internal var isTtsReady = false
    // UI 컴포넌트: 시간 알림 시작 버튼
    private lateinit var startButton: Button
    // UI 컴포넌트: 시간 알림 중지 버튼
    private lateinit var stopButton: Button
    private lateinit var backgroundModeButton: Button
    private lateinit var statusTextView: TextView
    
    private val TAG = "MainActivity"
    private var isBackgroundMode = false

    /**
     * 액티비티가 생성될 때 호출되는 메소드
     * UI 초기화 및 이벤트 리스너 설정
     * 
     * @param savedInstanceState 이전 상태가 저장된 경우 해당 데이터를 포함하는 Bundle 객체
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate 호출됨")
        
        // activity_main.xml 레이아웃을 현재 액티비티에 설정
        setContentView(R.layout.activity_main)

        // Initialize UI components
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        backgroundModeButton = findViewById(R.id.backgroundModeButton)
        statusTextView = findViewById(R.id.statusTextView)
        
        // Initialize TTS
        tts = TextToSpeech(this, this)
        
        // Request necessary permissions
        checkPermissions()

        // 시작 버튼 클릭 이벤트 처리
        startButton.setOnClickListener {
            Log.d(TAG, "시작 버튼 클릭됨")
            updateStatus("상태: 시간 알림 시작됨")
            startTimeAnnouncement()
        }

        // 중지 버튼 클릭 이벤트 처리
        stopButton.setOnClickListener {
            Log.d(TAG, "중지 버튼 클릭됨")
            updateStatus("상태: 시간 알림 중지됨")
            stopTimeAnnouncement()
        }
        
        backgroundModeButton.setOnClickListener {
            Log.d(TAG, "백그라운드 모드 버튼 클릭됨")
            if (!isBackgroundMode) {
                startBackgroundMode()
            } else {
                stopBackgroundMode()
            }
        }
    }

    private fun updateStatus(status: String) {
        statusTextView.text = status
        Log.d(TAG, "상태 업데이트: $status")
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
            Log.d(TAG, "권한 요청: ${permissionsToRequest.joinToString()}")
        } else {
            Log.d(TAG, "모든 권한이 이미 허용됨")
        }
    }

    /**
     * 시간 알림을 시작하는 메소드
     * WorkManager를 사용하여 1분마다 실행되는 백그라운드 작업을 스케줄링
     */
    private fun startTimeAnnouncement() {
        Log.d(TAG, "시간 알림 서비스 시작")
        
        // Test immediate announcement
        if (isTtsReady) {
            val currentTime = Calendar.getInstance()
            val hour = currentTime.get(Calendar.HOUR_OF_DAY)
            val minute = currentTime.get(Calendar.MINUTE)
            val timeString = String.format("현재 시각은 %d시 %d분 입니다.", hour, minute)
            tts.speak(timeString, TextToSpeech.QUEUE_FLUSH, null, "initial_announcement")
            Log.d(TAG, "초기 시간 알림: $timeString")
        } else {
            Log.e(TAG, "TTS가 준비되지 않았습니다. 초기화를 기다려주세요.")
            Toast.makeText(this, "TTS가 준비되지 않았습니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            return
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
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "time_announcement",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
        
        Log.d(TAG, "WorkManager에 주기적 시간 알림 작업 등록: 1분마다 실행")

        // Monitor work status
        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData("time_announcement")
            .observe(this) { workInfoList ->
                workInfoList?.forEach { workInfo ->
                    Log.d(TAG, "작업 상태: ${workInfo.state}")
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            Log.d(TAG, "작업이 성공적으로 완료됨")
                        }
                        WorkInfo.State.FAILED -> {
                            Log.e(TAG, "작업 실패")
                        }
                        WorkInfo.State.RUNNING -> {
                            Log.d(TAG, "작업이 실행 중")
                        }
                        WorkInfo.State.ENQUEUED -> {
                            Log.d(TAG, "작업이 대기열에 추가됨")
                        }
                        else -> {
                            Log.d(TAG, "작업 상태: ${workInfo.state}")
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
        Log.d(TAG, "시간 알림 서비스 중지")
        WorkManager.getInstance(this).cancelUniqueWork("time_announcement")
        Toast.makeText(this, "시간 알림이 중지되었습니다.", Toast.LENGTH_SHORT).show()
    }
    
    private fun startBackgroundMode() {
        Log.d(TAG, "백그라운드 모드 시작")
        isBackgroundMode = true
        backgroundModeButton.text = "백그라운드 모드 종료"
        updateStatus("상태: 백그라운드 모드 실행 중")
        
        // 배터리 최적화 제외 요청
        requestIgnoreBatteryOptimization()
        
        // 백그라운드에서도 강제로 실행되도록 설정
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .build()

        val backgroundWorkRequest = PeriodicWorkRequestBuilder<TimeAnnouncementWorker>(
            1, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("background_time_announcement")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "background_time_announcement",
            ExistingPeriodicWorkPolicy.UPDATE,
            backgroundWorkRequest
        )
        
        Log.d(TAG, "백그라운드 모드 WorkManager 작업 등록 완료")
        Toast.makeText(this, "백그라운드 모드가 시작되었습니다. 앱을 최소화해도 시간을 알려드립니다.", Toast.LENGTH_LONG).show()
    }
    
    private fun requestIgnoreBatteryOptimization() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "배터리 최적화 제외 요청")
                
                // 배터리 최적화 제외 요청 의도 생성
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                
                // 사용자에게 알려줌
                Toast.makeText(this, "앱이 백그라운드에서 정상 동작하려면 '배터리 최적화 안함'을 선택해주세요", Toast.LENGTH_LONG).show()
                
                // 설정 화면 실행
                startActivity(intent)
            } else {
                Log.d(TAG, "이미 배터리 최적화에서 제외되어 있습니다")
            }
        } catch (e: Exception) {
            Log.e(TAG, "배터리 최적화 제외 요청 중 오류 발생", e)
        }
    }
    
    private fun stopBackgroundMode() {
        Log.d(TAG, "백그라운드 모드 종료")
        isBackgroundMode = false
        backgroundModeButton.text = "백그라운드 모드 시작"
        updateStatus("상태: 백그라운드 모드 종료됨")
        
        WorkManager.getInstance(this).cancelUniqueWork("background_time_announcement")
        Toast.makeText(this, "백그라운드 모드가 종료되었습니다.", Toast.LENGTH_SHORT).show()
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
                Log.e(TAG, "한국어가 지원되지 않습니다")
                Toast.makeText(this, "한국어가 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
            } else {
                isTtsReady = true
                Log.d(TAG, "TTS 초기화 성공 - 한국어 설정됨")
                updateStatus("상태: TTS 준비 완료")
            }
        } else {
            Log.e(TAG, "TTS 초기화 실패: $status")
            Toast.makeText(this, "TTS 초기화에 실패했습니다.", Toast.LENGTH_SHORT).show()
            updateStatus("상태: TTS 초기화 실패")
        }
    }

    /**
     * 액티비티가 소멸될 때 호출되는 메소드
     * TextToSpeech 리소스 해제
     */
    override fun onDestroy() {
        Log.d(TAG, "onDestroy 호출됨")
        
        // 백그라운드 모드가 활성화된 경우 앱 종료 시에도 작업 유지
        if (!isBackgroundMode) {
            WorkManager.getInstance(this).cancelAllWork()
            Log.d(TAG, "모든 WorkManager 작업 취소됨")
        } else {
            Log.d(TAG, "백그라운드 모드 활성화 - 작업 유지됨")
        }
        
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
            Log.d(TAG, "TTS 자원 해제됨")
        }
        super.onDestroy()
    }
} 
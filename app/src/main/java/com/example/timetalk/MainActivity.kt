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
import java.text.SimpleDateFormat
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
        
        // 알림 채널 생성
        createNotificationChannel()

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
        
        // 백그라운드 모드 종료 인텐트 확인
        if (intent.getBooleanExtra("EXIT_BACKGROUND", false)) {
            Log.d(TAG, "★★★★★★★★★★ 백그라운드 모드 종료 인텐트 수신됨 ★★★★★★★★★★")
            isBackgroundMode = true // 현재 활성화된 것으로 간주하고 종료 처리
            stopBackgroundMode()
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
        updateStatus("상태: 백그라운드 모드 시작 중...")
        
        // 배터리 최적화 제외 요청
        requestIgnoreBatteryOptimization()
        
        // 현재 등록된 모든 작업 취소
        WorkManager.getInstance(this).cancelAllWork()
        Log.d(TAG, "★★★ 기존 등록된 모든 작업 취소됨 ★★★")
        
        // 잠시 대기하여 이전 작업이 모두 취소되도록 함
        Thread {
            try {
                Thread.sleep(1000)
                runOnUiThread {
                    // 즉시 한 번 시간 알림 (UI 스레드에서 실행)
                    if (isTtsReady) {
                        val currentTime = Calendar.getInstance()
                        val hour = currentTime.get(Calendar.HOUR_OF_DAY)
                        val minute = currentTime.get(Calendar.MINUTE)
                        val timeString = String.format("현재 시각은 %d시 %d분 입니다. 이후 백그라운드에서도 1시간마다 시간을 알려드립니다.", hour, minute)
                        tts.speak(timeString, TextToSpeech.QUEUE_FLUSH, null, "initial_announcement")
                        Log.d(TAG, "★★★ 초기 시간 알림 직접 실행: $timeString ★★★")
                    }
                    
                    // 백그라운드에서도 강제로 실행되도록 설정
                    val constraints = Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .setRequiresCharging(false)
                        .setRequiresDeviceIdle(false)
                        .build()
                        
                    try {
                        // 15초 후에 즉시 실행되는 일회성 작업 등록 (테스트 용도)
                        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<TimeAnnouncementWorker>()
                            .setConstraints(constraints)
                            .setInitialDelay(15, TimeUnit.SECONDS)
                            .addTag("one_time_announcement")
                            .build()
                            
                        WorkManager.getInstance(applicationContext).enqueue(oneTimeWorkRequest)
                        Log.d(TAG, "★★★ 15초 후 실행되는 일회성 작업 등록됨 (ID: ${oneTimeWorkRequest.id}) ★★★")
                        
                        // 1시간마다 실행되는 주기적 작업 등록
                        val periodicWorkRequest = PeriodicWorkRequestBuilder<TimeAnnouncementWorker>(
                            1, TimeUnit.HOURS  // 1시간 간격
                        )
                            .setConstraints(constraints)
                            .addTag("background_time_announcement")
                            .setBackoffCriteria(
                                BackoffPolicy.LINEAR,
                                10, TimeUnit.SECONDS
                            )
                            .build()

                        // 1시간마다 실행되는 주기적 작업
                        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                            "background_time_announcement",
                            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, // 기존 작업 취소하고 다시 등록
                            periodicWorkRequest
                        )
                        
                        Log.d(TAG, "★★★ 주기적 작업 등록 완료 (ID: ${periodicWorkRequest.id}) ★★★")
                        
                        // 앞으로 3시간 동안 1시간마다 실행되는 정확한 일회성 작업도 함께 등록
                        scheduleHourlyWorkers()
                        
                        // 작업 상태 모니터링 (일회성 작업)
                        WorkManager.getInstance(applicationContext)
                            .getWorkInfosByTagLiveData("one_time_announcement")
                            .observe(this@MainActivity) { workInfoList ->
                                workInfoList?.forEach { workInfo ->
                                    val state = workInfo.state.name
                                    Log.d(TAG, "★★★ 일회성 작업 상태: $state (ID: ${workInfo.id}) ★★★")
                                    updateStatus("상태: 일회성 작업 $state")
                                }
                            }
                            
                        // 작업 상태 모니터링 (주기적 작업)
                        WorkManager.getInstance(applicationContext)
                            .getWorkInfosByTagLiveData("background_time_announcement")
                            .observe(this@MainActivity) { workInfoList ->
                                workInfoList?.forEach { workInfo ->
                                    val state = workInfo.state.name
                                    Log.d(TAG, "★★★ 주기적 작업 상태: $state (ID: ${workInfo.id}) ★★★")
                                    updateStatus("상태: 주기적 작업 $state")
                                    
                                    // 실행 중인 경우 시간 표시
                                    if (workInfo.state == WorkInfo.State.RUNNING) {
                                        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                        updateStatus("상태: 주기적 작업 실행 중 ($currentTime)")
                                    }
                                }
                            }
                            
                    } catch (e: Exception) {
                        Log.e(TAG, "★★★ 작업 등록 중 오류 발생: ${e.message} ★★★", e)
                        updateStatus("상태: 작업 등록 오류 - ${e.message}")
                    }
                    
                    Toast.makeText(this@MainActivity, "백그라운드 모드가 시작되었습니다. 15초 후와 이후 1시간마다 시간을 알려드립니다.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "★★★ 백그라운드 모드 초기화 오류: ${e.message} ★★★", e)
            }
        }.start()
    }
    
    private fun scheduleHourlyWorkers() {
        // 다음 3시간 동안 1시간마다 실행될 일회성 작업을 미리 예약
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .build()
            
        for (i in 1..3) {
            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<TimeAnnouncementWorker>()
                .setConstraints(constraints)
                .setInitialDelay(i * 60L, TimeUnit.MINUTES) // 1시간, 2시간, 3시간 후에 실행
                .addTag("hourly_worker_$i")
                .build()
                
            WorkManager.getInstance(applicationContext).enqueue(oneTimeWorkRequest)
            Log.d(TAG, "★★★ ${i}시간 후 실행될 일회성 작업 예약됨 (ID: ${oneTimeWorkRequest.id}) ★★★")
        }
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

    // 인텐트로부터 액티비티가 다시 시작될 때 호출
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent 호출됨")
        
        // 백그라운드 모드 종료 요청 확인
        if (intent.getBooleanExtra("EXIT_BACKGROUND", false)) {
            Log.d(TAG, "★★★★★★★★★★ 백그라운드 모드 종료 인텐트 수신됨 ★★★★★★★★★★")
            isBackgroundMode = true // 현재 활성화된 것으로 간주하고 종료 처리
            stopBackgroundMode()
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "시간 알림"
            val descriptionText = "시간 알림 서비스를 위한 알림 채널"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel("time_announcement", name, importance).apply {
                description = descriptionText
            }
            
            // 시스템에 알림 채널 등록
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "알림 채널 생성됨: time_announcement")
        }
    }
} 
package com.example.timetalk

import android.Manifest
import android.content.Intent
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
import java.util.concurrent.TimeUnit

/**
 * 메인 액티비티 클래스
 * TextToSpeech.OnInitListener를 구현하여 음성 변환 초기화 콜백을 처리
 */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    // TextToSpeech 객체: 텍스트를 음성으로 변환하는 기능을 제공
    private lateinit var textToSpeech: TextToSpeech
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
        Log.d(TAG, "onCreate: 액티비티 생성")
        
        // activity_main.xml 레이아웃을 현재 액티비티에 설정
        setContentView(R.layout.activity_main)

        // TextToSpeech 초기화
        textToSpeech = TextToSpeech(this, this)
        Log.d(TAG, "TextToSpeech 초기화")
        
        // 레이아웃에서 버튼 컴포넌트 찾기
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        // 시작 버튼 클릭 이벤트 처리
        startButton.setOnClickListener {
            Log.d(TAG, "시작 버튼 클릭")
            if (checkPermissions()) {
                startTimeAnnouncement()
            } else {
                requestPermissions()
            }
        }

        // 중지 버튼 클릭 이벤트 처리
        stopButton.setOnClickListener {
            Log.d(TAG, "중지 버튼 클릭")
            stopTimeAnnouncement()
        }
    }

    /**
     * 알림 권한이 부여되었는지 확인하는 메소드
     * 
     * @return Boolean 권한이 부여된 경우 true, 그렇지 않은 경우 false
     */
    private fun checkPermissions(): Boolean {
        val isGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "권한 확인: $isGranted")
        return isGranted
    }

    /**
     * 알림 권한을 요청하는 메소드
     * 사용자에게 권한 요청 다이얼로그를 표시
     */
    private fun requestPermissions() {
        Log.d(TAG, "권한 요청")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            PERMISSION_REQUEST_CODE
        )
    }

    /**
     * 시간 알림을 시작하는 메소드
     * WorkManager를 사용하여 15분마다 실행되는 백그라운드 작업을 스케줄링
     */
    private fun startTimeAnnouncement() {
        Log.d(TAG, "시간 알림 시작")
        
        // 작업 제약 조건 설정
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        // 15분마다 실행되는 주기적 작업 생성
        val timeAnnouncementWork = PeriodicWorkRequestBuilder<TimeAnnouncementWorker>(
            15, TimeUnit.MINUTES,  // 반복 주기
            5, TimeUnit.MINUTES    // 유연성 간격
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES)  // 첫 실행 1분 후로 설정
            .build()

        // WorkManager를 통해 작업 스케줄링
        WorkManager.getInstance(this).also { workManager ->
            // 기존 작업 취소
            workManager.cancelUniqueWork("time_announcement")
            
            // 새 작업 등록
            workManager.enqueueUniquePeriodicWork(
                "time_announcement",
                ExistingPeriodicWorkPolicy.UPDATE,
                timeAnnouncementWork
            )
            
            // 작업 상태 관찰
            workManager.getWorkInfosForUniqueWorkLiveData("time_announcement")
                .observe(this) { workInfos ->
                    workInfos?.forEach { workInfo ->
                        Log.d(TAG, "작업 상태: ${workInfo.state}")
                    }
                }
        }

        // 시작 메시지 표시
        Toast.makeText(this, "시간 알림이 시작되었습니다.", Toast.LENGTH_SHORT).show()
    }

    /**
     * 시간 알림을 중지하는 메소드
     * WorkManager를 통해 스케줄링된 작업을 취소
     */
    private fun stopTimeAnnouncement() {
        Log.d(TAG, "시간 알림 중지")
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
            Log.d(TAG, "TTS 초기화 성공")
            textToSpeech.language = java.util.Locale.KOREAN
        } else {
            Log.e(TAG, "TTS 초기화 실패: $status")
            Toast.makeText(this, "TTS 초기화 실패", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 액티비티가 소멸될 때 호출되는 메소드
     * TextToSpeech 리소스 해제
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: 액티비티 소멸")
        textToSpeech.shutdown()
    }

    companion object {
        // 권한 요청 시 사용할 요청 코드
        private const val PERMISSION_REQUEST_CODE = 123
    }
} 
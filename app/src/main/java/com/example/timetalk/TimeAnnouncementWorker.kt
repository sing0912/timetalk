package com.example.timetalk

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 백그라운드에서 시간을 음성으로 알려주는 Worker 클래스
 * WorkManager를 통해 주기적으로 실행됨
 * 
 * @property context 앱의 Context 객체
 * @property params Worker 실행에 필요한 파라미터
 */
class TimeAnnouncementWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    private var textToSpeech: TextToSpeech? = null
    private val TAG = "TimeAnnouncementWorker"

    /**
     * Worker가 실행될 때 호출되는 메소드
     * TextToSpeech 초기화 및 시간 알림 실행
     * 
     * @return Result 작업 실행 결과 (성공/실패/재시도)
     */
    override fun doWork(): Result {
        Log.d(TAG, "doWork: 시간 알림 작업 시작")
        
        try {
            announceTime()
            Log.d(TAG, "doWork: 시간 알림 작업 성공")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork: 시간 알림 작업 실패", e)
            return Result.failure()
        }
    }

    /**
     * 현재 시간을 음성으로 알려주는 메소드
     * "HH시 mm분" 형식으로 시간을 포맷팅하여 음성으로 변환
     */
    private fun announceTime() {
        Log.d(TAG, "announceTime: TTS 초기화 시작")
        
        // TTS 초기화 및 시간 알림
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TTS 초기화 성공")
                textToSpeech?.language = Locale.KOREAN

                // 현재 시간 가져오기
                val calendar = Calendar.getInstance()
                val timeFormat = SimpleDateFormat("HH시 mm분", Locale.KOREAN)
                val timeString = timeFormat.format(calendar.time)
                Log.d(TAG, "현재 시간: $timeString")

                // 음성 출력
                textToSpeech?.speak(timeString, TextToSpeech.QUEUE_FLUSH, null, "TimeAnnouncement")
                
                // TTS가 말하기를 완료할 때까지 대기
                Thread.sleep(3000)
                
                // TTS 자원 해제
                textToSpeech?.stop()
                textToSpeech?.shutdown()
                Log.d(TAG, "TTS 자원 해제 완료")
            } else {
                Log.e(TAG, "TTS 초기화 실패: $status")
            }
        }
    }
} 
package com.example.timetalk

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

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
) : CoroutineWorker(context, params) {

    private val TAG = "TimeAnnouncementWorker"
    private var tts: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    /**
     * Worker가 실행될 때 호출되는 메소드
     * TextToSpeech 초기화 및 시간 알림 실행
     * 
     * @return Result 작업 실행 결과 (성공/실패/재시도)
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        Log.d(TAG, "★★★★★★★★★★ 시간 알림 작업 시작 - 현재 시각: $currentTime ★★★★★★★★★★")
        
        try {
            // 오디오 매니저 초기화
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // CPU가 절전 모드로 들어가는 것을 방지
            acquireWakeLock()
            Log.d(TAG, "★★★★★★★★★★ WakeLock 획득 완료 ★★★★★★★★★★")
            
            // Foreground 서비스로 실행하여 시스템에 의한 종료 방지
            val foregroundInfo = createForegroundInfo()
            setForeground(foregroundInfo)
            Log.d(TAG, "★★★★★★★★★★ Foreground 서비스 시작됨 ★★★★★★★★★★")
            
            // 오디오 포커스 획득
            val audioFocusResult = requestAudioFocus()
            Log.d(TAG, "★★★★★★★★★★ 오디오 포커스 획득 결과: $audioFocusResult ★★★★★★★★★★")
            
            // TTS 초기화
            Log.d(TAG, "★★★★★★★★★★ TTS 초기화 시작 ★★★★★★★★★★")
            val startTime = System.currentTimeMillis()
            
            // TTS 직접 초기화
            val ttsInitResult = suspendCancellableCoroutine<Boolean> { continuation ->
                try {
                    tts = TextToSpeech(context) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            val result = tts?.setLanguage(Locale.KOREAN)
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                Log.e(TAG, "★★★★★★★★★★ 한국어 TTS가 지원되지 않습니다 ★★★★★★★★★★")
                                continuation.resume(false)
                            } else {
                                val initTime = System.currentTimeMillis() - startTime
                                Log.d(TAG, "★★★★★★★★★★ TTS 초기화 성공 (소요 시간: ${initTime}ms) ★★★★★★★★★★")
                                
                                // TTS 음량 최대로 설정
                                tts?.setSpeechRate(0.9f)  // 약간 느리게 설정
                                tts?.setPitch(1.0f)      // 기본 피치
                                
                                // 음성 출력 전 잠시 대기
                                Thread.sleep(500)
                                
                                continuation.resume(true)
                            }
                        } else {
                            Log.e(TAG, "★★★★★★★★★★ TTS 초기화 실패: $status ★★★★★★★★★★")
                            continuation.resume(false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "★★★★★★★★★★ TTS 초기화 중 예외 발생: ${e.message} ★★★★★★★★★★", e)
                    continuation.resume(false)
                }
            }
            
            if (!ttsInitResult) {
                Log.e(TAG, "★★★★★★★★★★ TTS 초기화 실패 ★★★★★★★★★★")
                return@withContext Result.retry()
            }

            // 현재 시간 가져오기
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val timeString = String.format("현재 시각은 %d시 %d분 입니다.", hour, minute)
            
            // 음성 출력 시작 전 로그
            Log.d(TAG, "★★★★★★★★★★ 시간 알림 재생 시작: $timeString ★★★★★★★★★★")

            // 시간 알림 음성 출력
            val speakResult = speakText(timeString)
            if (!speakResult) {
                Log.e(TAG, "★★★★★★★★★★ 시간 알림 음성 재생 실패 ★★★★★★★★★★")
                return@withContext Result.retry()
            }

            Log.d(TAG, "★★★★★★★★★★ 시간 알림 음성 재생 완료 ★★★★★★★★★★")
            
            // 추가 대기 시간
            Log.d(TAG, "★★★★★★★★★★ 추가 안정화 대기 시간 3초 시작 ★★★★★★★★★★")
            delay(3000)
            Log.d(TAG, "★★★★★★★★★★ 추가 안정화 대기 시간 종료 ★★★★★★★★★★")
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ 시간 알림 작업 중 오류 발생: ${e.message} ★★★★★★★★★★", e)
            Result.retry()
        } finally {
            Log.d(TAG, "★★★★★★★★★★ 자원 해제 시작 ★★★★★★★★★★")
            abandonAudioFocus()
            releaseWakeLock()
            tts?.stop()
            tts?.shutdown()
            tts = null
            Log.d(TAG, "★★★★★★★★★★ 자원 해제 완료, 작업 종료 ★★★★★★★★★★")
        }
    }

    private suspend fun speakText(text: String): Boolean = withContext(Dispatchers.Main) {
        try {
            suspendCancellableCoroutine { continuation ->
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        Log.d(TAG, "★★★★★★★★★★ 음성 재생 시작: $text (ID: $utteranceId) ★★★★★★★★★★")
                    }

                    override fun onDone(utteranceId: String) {
                        Log.d(TAG, "★★★★★★★★★★ 음성 재생 완료: $text (ID: $utteranceId) ★★★★★★★★★★")
                        continuation.resume(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) {
                        Log.e(TAG, "★★★★★★★★★★ 음성 재생 오류 발생: $text (ID: $utteranceId) ★★★★★★★★★★")
                        continuation.resume(false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String, errorCode: Int) {
                        Log.e(TAG, "★★★★★★★★★★ 음성 재생 오류 발생 (코드: $errorCode): $text (ID: $utteranceId) ★★★★★★★★★★")
                        continuation.resume(false)
                    }
                })

                Log.d(TAG, "★★★★★★★★★★ TTS speak() 메서드 호출: $text ★★★★★★★★★★")
                
                // 볼륨 최대화 
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                
                // Use the non-deprecated version of speak
                val bundle = Bundle()
                bundle.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "timeAnnouncement")
                bundle.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) // 최대 볼륨
                
                val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, bundle, "timeAnnouncement")
                Log.d(TAG, "★★★★★★★★★★ TTS speak() 호출 결과: $speakResult ★★★★★★★★★★")
                
                if (speakResult == TextToSpeech.ERROR) {
                    Log.e(TAG, "★★★★★★★★★★ TTS speak() 메서드가 ERROR를 반환했습니다 ★★★★★★★★★★")
                    continuation.resume(false)
                }
                
                // 음성 재생이 멈추는 것을 방지하기 위해 타임아웃 설정
                Thread {
                    try {
                        Thread.sleep(8000) // 8초 타임아웃
                        if (continuation.isActive) {
                            Log.d(TAG, "★★★★★★★★★★ TTS 타임아웃: 수동으로 완료 처리 ★★★★★★★★★★")
                            continuation.resume(true)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "★★★★★★★★★★ TTS 타임아웃 스레드 오류: ${e.message} ★★★★★★★★★★")
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ 음성 재생 중 예외 발생: ${e.message} ★★★★★★★★★★", e)
            false
        }
    }

    private fun requestAudioFocus(): Int {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                    
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { }
                    .build()
                    
                return audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                return audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ 오디오 포커스 요청 오류: ${e.message} ★★★★★★★★★★")
            return AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
    }
    
    private fun abandonAudioFocus() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            Log.d(TAG, "★★★★★★★★★★ 오디오 포커스 해제됨 ★★★★★★★★★★")
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ 오디오 포커스 해제 오류: ${e.message} ★★★★★★★★★★")
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = androidx.core.app.NotificationCompat.Builder(context, "time_announcement")
            .setContentTitle("시간 알림")
            .setContentText("시간 알림이 실행 중입니다.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        return ForegroundInfo(1, notification)
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TimeTalk:TimeAnnouncement"
            ).apply {
                acquire(60 * 1000L) // 1분 타임아웃
            }
            Log.d(TAG, "★★★★★★★★★★ WakeLock 획득됨 (60초) ★★★★★★★★★★")
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ WakeLock 획득 중 오류 발생 ★★★★★★★★★★", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "★★★★★★★★★★ WakeLock 해제됨 ★★★★★★★★★★")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ WakeLock 해제 중 오류 발생 ★★★★★★★★★★", e)
        }
    }
} 
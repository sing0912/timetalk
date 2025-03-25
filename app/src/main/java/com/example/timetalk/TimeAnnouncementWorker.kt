package com.example.timetalk

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
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
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isTtsInitialized = false
    
    // 싱글톤 TTS 인스턴스를 위한 컴패니언 객체
    companion object {
        private var sharedTts: TextToSpeech? = null
        private var isTtsInitializing = false
    }

    /**
     * Worker가 실행될 때 호출되는 메소드
     * TextToSpeech 초기화 및 시간 알림 실행
     * 
     * @return Result 작업 실행 결과 (성공/실패/재시도)
     */
    override suspend fun doWork(): Result {
        Log.d(TAG, "★★★★★★★★★★ 시간 알림 작업 시작 - 현재 시각: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())} ★★★★★★★★★★")
        
        try {
            // 웨이크락 획득 (화면이 꺼져도 작업 진행)
            acquireWakeLock()
            
            // 포그라운드 서비스로 설정하여 안정적인 실행 보장
            setForeground(createForegroundInfo())
            Log.d(TAG, "★★★★★★★★★★ Foreground 서비스 시작됨 ★★★★★★★★★★")
            
            // 오디오 매니저 초기화 및 오디오 포커스 요청
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val focusResult = requestAudioFocus()
            Log.d(TAG, "★★★★★★★★★★ 오디오 포커스 획득 결과: $focusResult ★★★★★★★★★★")
            
            // TTS 초기화 및 시간 알림
            if (announceCurrentTime()) {
                Log.d(TAG, "★★★★★★★★★★ 시간 알림 성공 ★★★★★★★★★★")
                return Result.success()
            } else {
                Log.e(TAG, "★★★★★★★★★★ 시간 알림 실패 ★★★★★★★★★★")
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ 작업 실행 중 예외 발생: ${e.message} ★★★★★★★★★★", e)
            return Result.retry()
        } finally {
            // 리소스 해제
            releaseResources()
        }
    }
    
    private suspend fun announceCurrentTime(): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                // 싱글톤 TTS 사용
                if (sharedTts == null && !isTtsInitializing) {
                    isTtsInitializing = true
                    initTTS()
                }
                
                // TTS 초기화 확인
                if (isTtsInitialized && sharedTts != null) {
                    // 현재 시간 가져오기
                    val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    val hour = currentTime.substring(0, 2).toInt()
                    val minute = currentTime.substring(3, 5).toInt()
                    
                    // 시간 알림 텍스트 생성
                    val text = "현재 시각은 $hour 시 $minute 분입니다."
                    Log.d(TAG, "★★★★★★★★★★ 현재 시간: $text ★★★★★★★★★★")
                    
                    // 볼륨 최대로 설정
                    val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                    Log.d(TAG, "★★★★★★★★★★ 볼륨 최대로 설정: $maxVolume ★★★★★★★★★★")
                    
                    // TTS로 시간 알림
                    speakText(text)
                    return@withContext true
                } else {
                    Log.e(TAG, "★★★★★★★★★★ TTS가 초기화되지 않았습니다 ★★★★★★★★★★")
                    // MainActivity에 의도적으로 알림을 보내 앱을 재시작
                    sendTtsErrorBroadcast()
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "★★★★★★★★★★ 시간 알림 중 예외 발생: ${e.message} ★★★★★★★★★★", e)
                sendTtsErrorBroadcast()
                return@withContext false
            }
        }
    }
    
    private fun sendTtsErrorBroadcast() {
        try {
            val intent = Intent("com.example.timetalk.TTS_ERROR")
            context.sendBroadcast(intent)
            Log.d(TAG, "★★★★★★★★★★ TTS 오류 브로드캐스트 전송됨 ★★★★★★★★★★")
        } catch (e: Exception) {
            Log.e(TAG, "브로드캐스트 전송 중 오류: ${e.message}", e)
        }
    }
    
    private suspend fun initTTS() {
        try {
            Log.d(TAG, "★★★★★★★★★★ TTS 초기화 시작 ★★★★★★★★★★")
            
            // TTS 객체가 이미 초기화된 경우
            if (sharedTts != null) {
                tts = sharedTts
                isTtsInitialized = true
                Log.d(TAG, "★★★★★★★★★★ 기존 TTS 인스턴스 사용 ★★★★★★★★★★")
                return
            }
            
            // 타임아웃 설정 (10초)
            withTimeout(10000L) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val newTts = TextToSpeech(context) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            sharedTts = newTts
                            tts = newTts
                            
                            // 언어 설정
                            val result = newTts.setLanguage(Locale.KOREAN)
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                Log.e(TAG, "★★★★★★★★★★ 한국어 지원되지 않음, 기본 언어 사용 ★★★★★★★★★★")
                                newTts.setLanguage(Locale.getDefault())
                            }
                            
                            // 오디오 스트림 설정
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                val audioAttributes = AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                                newTts.setAudioAttributes(audioAttributes)
                            } else {
                                @Suppress("DEPRECATION")
                                newTts.setAudioStreamType(AudioManager.STREAM_MUSIC)
                            }
                            
                            // 음성 속도 및 피치 설정
                            newTts.setSpeechRate(1.0f)
                            newTts.setPitch(1.0f)
                            
                            isTtsInitialized = true
                            Log.d(TAG, "★★★★★★★★★★ TTS 초기화 완료 ★★★★★★★★★★")
                        } else {
                            Log.e(TAG, "★★★★★★★★★★ TTS 초기화 실패: $status ★★★★★★★★★★")
                        }
                        isTtsInitializing = false
                        continuation.resume(Unit)
                    }
                }
            }
        } catch (e: Exception) {
            isTtsInitializing = false
            Log.e(TAG, "★★★★★★★★★★ TTS 초기화 중 예외 발생: ${e.message} ★★★★★★★★★★", e)
        }
    }
    
    private suspend fun speakText(text: String): Boolean {
        return try {
            withTimeout(15000L) { // 15초 타임아웃
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
                            Log.e(TAG, "★★★★★★★★★★ 음성 재생 오류: $text (ID: $utteranceId) ★★★★★★★★★★")
                            continuation.resume(false)
                        }
                        
                        override fun onError(utteranceId: String, errorCode: Int) {
                            super.onError(utteranceId, errorCode)
                            Log.e(TAG, "★★★★★★★★★★ 음성 재생 오류 (코드: $errorCode): $text (ID: $utteranceId) ★★★★★★★★★★")
                            continuation.resume(false)
                        }
                    })
                    
                    val utteranceId = "timeAnnouncement_${System.currentTimeMillis()}"
                    val params = Bundle()
                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) // 최대 볼륨
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    } else {
                        @Suppress("DEPRECATION")
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, HashMap<String, String>().apply {
                            put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                        })
                    }
                    
                    // 5초 후에도 완료되지 않으면 강제로 완료 처리 (안전 장치)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (continuation.isActive) {
                            Log.d(TAG, "★★★★★★★★★★ 음성 재생 타임아웃으로 강제 완료 ★★★★★★★★★★")
                            continuation.resume(true)
                        }
                    }, 5000)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ TTS 음성 합성 중 예외 발생: ${e.message} ★★★★★★★★★★", e)
            false
        }
    }
    
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "TimeTalk:TimeAnnouncementWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            
            wakeLock?.acquire(60 * 1000L) // 60초 동안 웨이크락 유지
            Log.d(TAG, "★★★★★★★★★★ WakeLock 획득됨 (60초) ★★★★★★★★★★")
            Log.d(TAG, "★★★★★★★★★★ WakeLock 획득 완료 ★★★★★★★★★★")
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ WakeLock 획득 실패: ${e.message} ★★★★★★★★★★", e)
        }
    }
    
    private fun requestAudioFocus(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener { }
                    .build()
                
                audioManager?.requestAudioFocus(audioFocusRequest!!) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ 오디오 포커스 요청 실패: ${e.message} ★★★★★★★★★★", e)
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
    }
    
    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
            Log.d(TAG, "★★★★★★★★★★ 오디오 포커스 해제됨 ★★★★★★★★★★")
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ 오디오 포커스 해제 실패: ${e.message} ★★★★★★★★★★", e)
        }
    }
    
    private fun releaseResources() {
        try {
            // WakeLock 해제
            try {
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Log.d(TAG, "★★★★★★★★★★ WakeLock 해제됨 ★★★★★★★★★★")
                    }
                }
                wakeLock = null
            } catch (e: Exception) {
                Log.e(TAG, "WakeLock 해제 실패: ${e.message}", e)
            }
            
            // 오디오 포커스 해제
            try {
                abandonAudioFocus()
            } catch (e: Exception) {
                Log.e(TAG, "오디오 포커스 해제 실패: ${e.message}", e)
            }
            
            // TTS는 공유 인스턴스이므로 여기서 해제하지 않음
            // 대신 MainActivity에서 앱 종료 시 해제
            
            Log.d(TAG, "★★★★★★★★★★ 모든 리소스 해제 완료 ★★★★★★★★★★")
        } catch (e: Exception) {
            Log.e(TAG, "★★★★★★★★★★ 리소스 해제 중 예외 발생: ${e.message} ★★★★★★★★★★", e)
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        // 백그라운드 모드 종료를 위한 인텐트 생성
        val exitIntent = Intent(context, BackgroundControlReceiver::class.java).apply {
            action = "com.example.timetalk.EXIT_BACKGROUND"
        }
        val exitPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            0,
            exitIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, "time_announcement")
            .setContentTitle("시간 알림")
            .setContentText("시간 알림이 실행 중입니다. 현재 시각: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_delete, "종료", exitPendingIntent)
            .setOngoing(true)
            .build()

        return ForegroundInfo(1, notification)
    }
} 
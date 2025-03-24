package com.example.timetalk

import android.content.Context
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

    /**
     * Worker가 실행될 때 호출되는 메소드
     * TextToSpeech 초기화 및 시간 알림 실행
     * 
     * @return Result 작업 실행 결과 (성공/실패/재시도)
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        Log.d(TAG, "Starting time announcement work")
        
        acquireWakeLock()
        
        try {
            // Set foreground service notification
            setForeground(createForegroundInfo())
            
            // Initialize TTS
            tts = await { callback ->
                TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(Locale.KOREAN)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e(TAG, "Korean language is not supported")
                            callback(false)
                        } else {
                            Log.d(TAG, "TTS initialized successfully")
                            callback(true)
                        }
                    } else {
                        Log.e(TAG, "TTS initialization failed with status: $status")
                        callback(false)
                    }
                }
            }

            if (tts == null) {
                Log.e(TAG, "Failed to initialize TTS")
                return@withContext Result.retry()
            }

            // Get current time
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val timeString = String.format("현재 시각은 %d시 %d분 입니다.", hour, minute)
            Log.d(TAG, "Announcing time: $timeString")

            // Speak the time
            val result = speakText(timeString)
            if (!result) {
                Log.e(TAG, "Failed to speak time announcement")
                return@withContext Result.retry()
            }

            Log.d(TAG, "Time announcement completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in time announcement work", e)
            Result.retry()
        } finally {
            releaseWakeLock()
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }

    private suspend fun speakText(text: String): Boolean = withContext(Dispatchers.Main) {
        try {
            suspendCancellableCoroutine { continuation ->
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        Log.d(TAG, "Started speaking: $utteranceId")
                    }

                    override fun onDone(utteranceId: String) {
                        Log.d(TAG, "Finished speaking: $utteranceId")
                        continuation.resume(true)
                    }

                    override fun onError(utteranceId: String) {
                        Log.e(TAG, "Error speaking: $utteranceId")
                        continuation.resume(false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String, errorCode: Int) {
                        Log.e(TAG, "Error speaking: $utteranceId, error code: $errorCode")
                        continuation.resume(false)
                    }
                })

                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "timeAnnouncement"
                
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in speakText", e)
            false
        }
    }

    private suspend fun await(init: (callback: (Boolean) -> Unit) -> Unit): TextToSpeech? = suspendCancellableCoroutine { continuation ->
        try {
            init { success ->
                if (success) {
                    continuation.resume(tts)
                } else {
                    continuation.resume(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in TTS initialization", e)
            continuation.resume(null)
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
                acquire(10 * 60 * 1000L) // 10 minutes timeout
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }
} 
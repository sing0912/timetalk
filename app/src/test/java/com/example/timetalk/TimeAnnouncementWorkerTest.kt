package com.example.timetalk

import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioManager
import org.robolectric.shadows.ShadowPowerManager
import java.util.*
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowPowerManager::class, ShadowAudioManager::class])
class TimeAnnouncementWorkerTest {
    private lateinit var context: Context
    private lateinit var worker: TimeAnnouncementWorker
    private lateinit var tts: TextToSpeech
    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // WorkManager 테스트 초기화
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
            
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        
        // 모의 객체 설정
        tts = mockk(relaxed = true)
        audioManager = mockk(relaxed = true)
        powerManager = mockk(relaxed = true)
        wakeLock = mockk(relaxed = true)
        
        // 서비스 모킹
        mockkStatic(TextToSpeech::class)
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.newWakeLock(any(), any()) } returns wakeLock
        
        // TimeAnnouncementWorker 생성
        worker = TestListenableWorkerBuilder<TimeAnnouncementWorker>(context).build()
    }
    
    @Test
    fun `test successful work execution`() = runTest {
        // Given: TTS 초기화 성공 및 발화 성공 설정
        every { tts.setLanguage(Locale.KOREAN) } returns TextToSpeech.SUCCESS
        every { tts.speak(any(), any(), any(), any()) } returns TextToSpeech.SUCCESS
        
        // When: Worker 실행
        val result = worker.doWork()
        
        // Then: 성공 결과 확인
        assertEquals(ListenableWorker.Result.success(), result)
    }
    
    @Test
    fun `test retry on TTS error`() = runTest {
        // Given: TTS 초기화 오류 설정
        every { tts.setLanguage(Locale.KOREAN) } returns TextToSpeech.ERROR
        
        // When: Worker 실행
        val result = worker.doWork()
        
        // Then: retry 결과 확인
        assertEquals(ListenableWorker.Result.retry(), result)
    }
    
    @Test
    fun `test wake lock acquisition and release`() = runTest {
        // Given: WakeLock 관련 설정
        every { wakeLock.acquire(any<Long>()) } just runs
        every { wakeLock.isHeld } returns true
        every { wakeLock.release() } just runs
        
        // When: Worker 실행
        worker.doWork()
        
        // Then: WakeLock 획득 및 해제 확인
        verify { 
            wakeLock.acquire(any<Long>())
            wakeLock.release()
        }
    }
    
    @Test
    fun `test audio focus request and abandonment`() = runTest {
        // Given: 오디오 포커스 관련 설정
        every { audioManager.requestAudioFocus(any(), any(), any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { audioManager.abandonAudioFocus(any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        
        // When: Worker 실행
        worker.doWork()
        
        // Then: 오디오 포커스 요청 및 해제 확인
        verify { 
            audioManager.requestAudioFocus(any(), any(), any())
            audioManager.abandonAudioFocus(any())
        }
    }
} 
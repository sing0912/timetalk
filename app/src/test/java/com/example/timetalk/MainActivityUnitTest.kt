package com.example.timetalk

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MainActivityUnitTest {
    private lateinit var activityScenario: ActivityScenario<MainActivity>
    private lateinit var tts: TextToSpeech
    private lateinit var workManager: WorkManager
    
    @Before
    fun setup() {
        // WorkManager 모킹
        workManager = mockk(relaxed = true)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager
        
        // 실제 테스트에서 activityScenario를 초기화
    }
    
    @After
    fun tearDown() {
        // ActivityScenario 닫기
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
        
        // 모든 모킹 해제
        unmockkAll()
    }
    
    @Test
    fun `test TTS initialization success`() {
        // Given
        val ttsCallback = slot<TextToSpeech.OnInitListener>()
        
        // 실제 TextToSpeech 생성을 모킹하고 OnInitListener 캡처
        mockkConstructor(TextToSpeech::class)
        every { 
            anyConstructed<TextToSpeech>().setLanguage(Locale.KOREAN) 
        } returns TextToSpeech.SUCCESS
        
        every {
            constructedWith<TextToSpeech>(match { it: Array<Any> -> it.size == 2 && it[1] is TextToSpeech.OnInitListener })
        } answers {
            // 생성자의 두 번째 인자(OnInitListener)를 캡처
            val listener = secondArg<TextToSpeech.OnInitListener>()
            ttsCallback.captured = listener
            mockk()
        }
        
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        activityScenario.onActivity { activity ->
            // When: OnInit 콜백 호출
            ttsCallback.captured.onInit(TextToSpeech.SUCCESS)
            
            // Then: TTS 준비 상태 확인
            assertTrue(activity.isTtsReady)
        }
    }
    
    @Test
    fun `test TTS initialization failure`() {
        // Given
        val ttsCallback = slot<TextToSpeech.OnInitListener>()
        
        // 실제 TextToSpeech 생성을 모킹하고 OnInitListener 캡처
        mockkConstructor(TextToSpeech::class)
        every {
            constructedWith<TextToSpeech>(match { it: Array<Any> -> it.size == 2 && it[1] is TextToSpeech.OnInitListener })
        } answers {
            // 생성자의 두 번째 인자(OnInitListener)를 캡처
            val listener = secondArg<TextToSpeech.OnInitListener>()
            ttsCallback.captured = listener
            mockk()
        }
        
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        activityScenario.onActivity { activity ->
            // When: OnInit 콜백 호출 (오류 상태)
            ttsCallback.captured.onInit(TextToSpeech.ERROR)
            
            // Then: TTS 준비 상태 확인
            assertFalse(activity.isTtsReady)
        }
    }
    
    @Test
    fun `test permission check and request`() {
        // ActivityScenario 초기화
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        activityScenario.onActivity { activity ->
            // Given
            val shadowActivity = shadowOf(activity)
            
            // When
            activity.checkPermissions()
            
            // Then
            val requestedPermissions = shadowActivity.lastRequestedPermission
            
            // 요청된 권한 확인 - lastRequestedPermission.permissions 대신 requestedPermissions 객체 직접 사용
            assertTrue(requestedPermissions.requestedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS))
            assertTrue(requestedPermissions.requestedPermissions.contains(Manifest.permission.FOREGROUND_SERVICE))
            assertTrue(requestedPermissions.requestedPermissions.contains(Manifest.permission.WAKE_LOCK))
        }
    }
    
    @Test
    fun `test start time announcement`() {
        // ActivityScenario 초기화
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        activityScenario.onActivity { activity ->
            // Given
            activity.isTtsReady = true
            
            // When
            activity.startTimeAnnouncement()
            
            // Then
            verify {
                workManager.enqueueUniquePeriodicWork(
                    "time_announcement",
                    any(),
                    any()
                )
            }
        }
    }
    
    @Test
    fun `test stop time announcement`() {
        // ActivityScenario 초기화
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        activityScenario.onActivity { activity ->
            // When
            activity.stopTimeAnnouncement()
            
            // Then
            verify {
                workManager.cancelUniqueWork("time_announcement")
            }
        }
    }
} 
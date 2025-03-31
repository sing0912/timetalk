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
    
    @Before
    fun setup() {
        // 각 테스트에서 필요한 초기화 수행
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
        
        // TTS 모킹 - 생성자 전체를 모킹하는 대신 더 간단한 접근 방식 사용
        mockkConstructor(TextToSpeech::class)
        
        // 생성자 호출 시 OnInitListener 캡처
        every { 
            anyConstructed<TextToSpeech>().setLanguage(Locale.KOREAN) 
        } returns TextToSpeech.SUCCESS
        
        // ActivityScenario 시작
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        activityScenario.onActivity { activity ->
            // MainActivity의 TTS 초기화 메서드 직접 호출
            activity.onInit(TextToSpeech.SUCCESS)
            
            // Then: TTS 준비 상태 확인
            assertTrue(activity.isTtsReady)
        }
    }
    
    @Test
    fun `test TTS initialization failure`() {
        // ActivityScenario 시작
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        activityScenario.onActivity { activity ->
            // MainActivity의 TTS 초기화 메서드 직접 호출
            activity.onInit(TextToSpeech.ERROR)
            
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
        
        // Mock WorkManager 설정 - 전체 WorkManager 클래스를 모킹
        val mockWorkManager = mockk<WorkManager>(relaxed = true)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockWorkManager
        
        activityScenario.onActivity { activity ->
            // Given: TTS 준비 상태 설정
            activity.isTtsReady = true
            
            // When: 시간 알림 시작 메서드 호출
            activity.startTimeAnnouncement()
            
            // Then: WorkManager의 enqueueUniquePeriodicWork 메서드가 호출되었는지 확인
            verify {
                mockWorkManager.enqueueUniquePeriodicWork(
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
        
        // Mock WorkManager 설정 - 전체 WorkManager 클래스를 모킹
        val mockWorkManager = mockk<WorkManager>(relaxed = true)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockWorkManager
        
        activityScenario.onActivity { activity ->
            // When: 시간 알림 중지 메서드 호출
            activity.stopTimeAnnouncement()
            
            // Then: WorkManager의 cancelUniqueWork 메서드가 호출되었는지 확인
            verify {
                mockWorkManager.cancelUniqueWork("time_announcement")
            }
        }
    }
} 
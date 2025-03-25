package com.example.timetalk

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import io.mockk.*
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
class MainActivityTest {
    private lateinit var activityScenario: ActivityScenario<MainActivity>
    private lateinit var tts: TextToSpeech
    private lateinit var workManager: WorkManager
    
    @Before
    fun setup() {
        tts = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager
        
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
    }
    
    @Test
    fun `test TTS initialization success`() {
        // Given
        val ttsCallback = slot<TextToSpeech.OnInitListener>()
        
        activityScenario.onActivity { activity ->
            // When
            every { tts.setLanguage(Locale.KOREAN) } returns TextToSpeech.SUCCESS
            ttsCallback.captured.onInit(TextToSpeech.SUCCESS)
            
            // Then
            assertTrue(activity.isTtsReady)
        }
    }
    
    @Test
    fun `test TTS initialization failure`() {
        // Given
        val ttsCallback = slot<TextToSpeech.OnInitListener>()
        
        activityScenario.onActivity { activity ->
            // When
            ttsCallback.captured.onInit(TextToSpeech.ERROR)
            
            // Then
            assertFalse(activity.isTtsReady)
        }
    }
    
    @Test
    fun `test permission check and request`() {
        activityScenario.onActivity { activity ->
            // Given
            val shadowActivity = shadowOf(activity)
            
            // When
            activity.checkPermissions()
            
            // Then
            val requestedPermissions = shadowActivity.lastRequestedPermission
            assertEquals(
                listOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.WAKE_LOCK
                ),
                requestedPermissions.permissions.toList()
            )
        }
    }
    
    @Test
    fun `test start time announcement`() {
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
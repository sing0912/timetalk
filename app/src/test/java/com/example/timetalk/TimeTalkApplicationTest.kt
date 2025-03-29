package com.example.timetalk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class TimeTalkApplicationTest {
    private lateinit var application: TimeTalkApplication
    private lateinit var notificationManager: NotificationManager
    
    @Before
    fun setup() {
        notificationManager = mockk<NotificationManager>(relaxed = true)
        application = spyk(TimeTalkApplication())
        every { application.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
    }
    
    @After
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `test notification channel creation`() {
        // When
        application.onCreate()
        
        // Then
        verify {
            notificationManager.createNotificationChannel(any())
        }
    }
    
    @Test
    fun `test WorkManager configuration`() {
        // When
        val config = application.workManagerConfiguration
        
        // Then
        assertNotNull(config)
        assertEquals(android.util.Log.DEBUG, config.minimumLoggingLevel)
    }
    
    @Test
    fun `test notification channel properties`() {
        // Given
        val channelSlot = slot<NotificationChannel>()
        
        // When
        application.onCreate()
        
        // Then
        verify {
            notificationManager.createNotificationChannel(capture(channelSlot))
        }
        
        with(channelSlot.captured) {
            assertEquals("time_announcement", id)
            assertEquals("Time Announcement", name)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, importance)
            assertEquals("Channel for time announcement notifications", description)
        }
    }
} 
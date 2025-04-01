package com.example.timetalk

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class MainActivityTest {
    private lateinit var context: Context
    private lateinit var activity: MainActivity
    private lateinit var startButton: Button
    private lateinit var statusTextView: TextView
    
    @Mock
    private lateinit var mockTTS: TextToSpeech
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        activity = MainActivity()
        activity.onCreate(null)
        
        startButton = activity.findViewById(R.id.startButton)
        statusTextView = activity.findViewById(R.id.statusTextView)
    }
    
    @Test
    fun testInitialState() {
        assertNotNull(startButton)
        assertNotNull(statusTextView)
        assertTrue(startButton.isEnabled)
        assertEquals("TTS 초기화 중...", statusTextView.text)
    }
    
    @Test
    fun testTimeAnnouncementButton() {
        startButton.performClick()
        assertEquals("TTS가 준비되지 않았습니다.", statusTextView.text)
    }
    
    @Test
    fun testTTSInitializationStates() {
        // Test initial state
        assertEquals("TTS 초기화 중...", statusTextView.text)
        
        // Test successful initialization
        activity.onInit(TextToSpeech.SUCCESS)
        assertEquals("TTS가 준비되었습니다.", statusTextView.text)
        
        // Test failed initialization
        activity.onInit(TextToSpeech.ERROR)
        assertEquals("TTS 초기화 실패", statusTextView.text)
    }
} 
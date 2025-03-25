package com.example.timetalk

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class TimeAnnouncementWorkerTest {
    private lateinit var context: Context
    private lateinit var worker: TimeAnnouncementWorker
    private lateinit var tts: TextToSpeech
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tts = mockk(relaxed = true)
        worker = spyk(
            TestListenableWorkerBuilder<TimeAnnouncementWorker>(context).build()
        )
    }
    
    @Test
    fun `test successful time announcement`() = runTest {
        // Given
        val utteranceProgressListenerSlot = slot<UtteranceProgressListener>()
        every { tts.setLanguage(Locale.KOREAN) } returns TextToSpeech.SUCCESS
        every { tts.setOnUtteranceProgressListener(capture(utteranceProgressListenerSlot)) } just Runs
        
        // When
        val result = worker.doWork()
        
        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        verify {
            tts.speak(any(), TextToSpeech.QUEUE_FLUSH, any(), any())
        }
    }
    
    @Test
    fun `test TTS initialization failure`() = runTest {
        // Given
        every { tts.setLanguage(Locale.KOREAN) } returns TextToSpeech.ERROR
        
        // When
        val result = worker.doWork()
        
        // Then
        assertEquals(ListenableWorker.Result.retry(), result)
    }
    
    @Test
    fun `test TTS speak failure`() = runTest {
        // Given
        val utteranceProgressListenerSlot = slot<UtteranceProgressListener>()
        every { tts.setLanguage(Locale.KOREAN) } returns TextToSpeech.SUCCESS
        every { tts.setOnUtteranceProgressListener(capture(utteranceProgressListenerSlot)) } answers {
            utteranceProgressListenerSlot.captured.onError("timeAnnouncement")
        }
        
        // When
        val result = worker.doWork()
        
        // Then
        assertEquals(ListenableWorker.Result.retry(), result)
    }
    
    @Test
    fun `test correct time format`() = runTest {
        // Given
        val timeStringSlot = slot<String>()
        every { tts.speak(capture(timeStringSlot), any(), any(), any()) } returns TextToSpeech.SUCCESS
        
        // When
        worker.doWork()
        
        // Then
        assert(timeStringSlot.captured.matches(Regex("현재 시각은 \\d{1,2}시 \\d{1,2}분 입니다\\.")))
    }
} 
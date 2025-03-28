package com.example.timetalk

import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class MainActivityUnitTest {
    
    @Mock
    private lateinit var mockTts: TextToSpeech
    
    @Mock
    private lateinit var mockButton: Button
    
    @Mock
    private lateinit var mockStatusTextView: TextView
    
    private lateinit var activity: MainActivity

    @Before
    fun setup() {
        activity = MainActivity()
        activity.tts = mockTts
        activity.announceTimeButton = mockButton
        activity.statusTextView = mockStatusTextView
    }

    @Test
    fun `TTS 초기화 성공시 상태가 올바르게 설정됨`() {
        // Given
        `when`(mockTts.setLanguage(Locale.KOREAN)).thenReturn(TextToSpeech.SUCCESS)

        // When
        activity.onInit(TextToSpeech.SUCCESS)

        // Then
        verify(mockStatusTextView).text = "준비 완료"
        assert(activity.isTtsReady)
    }

    @Test
    fun `TTS 초기화 실패시 상태가 올바르게 설정됨`() {
        // When
        activity.onInit(TextToSpeech.ERROR)

        // Then
        verify(mockStatusTextView).text = "오류: TTS 초기화 실패"
        assert(!activity.isTtsReady)
    }

    @Test
    fun `한국어 지원하지 않을 경우 상태가 올바르게 설정됨`() {
        // Given
        `when`(mockTts.setLanguage(Locale.KOREAN)).thenReturn(TextToSpeech.LANG_MISSING_DATA)

        // When
        activity.onInit(TextToSpeech.SUCCESS)

        // Then
        verify(mockStatusTextView).text = "오류: 한국어 지원되지 않음"
        assert(!activity.isTtsReady)
    }

    @Test
    fun `현재 시각 알림이 올바르게 동작함`() {
        // Given
        activity.isTtsReady = true
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val expectedTimeString = String.format("현재 시각은 %d시 %d분 입니다.", hour, minute)

        // When
        activity.announceCurrentTime()

        // Then
        verify(mockTts).speak(eq(expectedTimeString), eq(TextToSpeech.QUEUE_FLUSH), isNull(), eq("TIME_ANNOUNCE"))
        verify(mockStatusTextView).text = "현재 시각: ${hour}시 ${minute}분"
    }

    @Test
    fun `리소스 해제가 올바르게 동작함`() {
        // When
        activity.onDestroy()

        // Then
        verify(mockTts).stop()
        verify(mockTts).shutdown()
    }
} 
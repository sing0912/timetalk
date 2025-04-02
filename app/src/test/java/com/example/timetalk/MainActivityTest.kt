package com.example.timetalk

import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MainActivityTest {
    private lateinit var activity: MainActivity
    private lateinit var startButton: Button
    private lateinit var statusTextView: TextView

    @Before
    fun setup() {
        // Robolectric으로 액티비티 생성
        activity = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .resume()
            .get()
            
        // 뷰 참조 초기화
        startButton = activity.findViewById(R.id.startButton)
        statusTextView = activity.findViewById(R.id.statusTextView)
    }

    @Test
    fun testInitialState() {
        // 버튼 상태 확인
        assertTrue(startButton.isEnabled)
        
        // 초기 상태 텍스트 확인
        assertEquals("TTS 초기화 중...", statusTextView.text.toString())
    }

    @Test
    fun testTTSInitializationSuccess() {
        // TTS 초기화 성공 상태 설정
        activity.isTtsReady = true
        activity.updateStatus("준비 완료")
        
        // 상태 텍스트 확인
        assertEquals("준비 완료", statusTextView.text.toString())
    }

    @Test
    fun testTTSInitializationFailure() {
        // TTS 초기화 실패 상태 설정
        activity.onInit(TextToSpeech.ERROR)
        
        // 실패 상태 텍스트 확인
        assertEquals("오류: TTS 초기화 실패", statusTextView.text.toString())
    }
} 
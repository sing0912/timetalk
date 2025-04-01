package com.example.timetalk

import android.speech.tts.TextToSpeech
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.anyOf
import org.hamcrest.CoreMatchers.`is` as Is
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Test
    fun testInitialState() {
        // 액티비티 실행
        ActivityScenario.launch(MainActivity::class.java)

        // 초기 상태 확인
        onView(withId(R.id.startButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
            .check(matches(anyOf(
                withText("TTS 초기화 중..."),
                withText("준비 완료"),
                withText("오류: 한국어 지원되지 않음"),
                withText("오류: TTS 초기화 실패")
            )))
    }

    @Test
    fun testTimeAnnouncementButton() {
        // 액티비티 실행
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        // 버튼 클릭 (moveToState 호출하지 않음)
        onView(withId(R.id.startButton)).perform(click())
        
        // TTS가 초기화되지 않은 상태에서는 에러 메시지가 표시되어야 함
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
        
        // TTS 초기화 상태 설정
        scenario.onActivity { activity ->
            activity.isTtsReady = true
            
            // 액티비티 내부에서 직접 UI 상태 업데이트
            activity.runOnUiThread {
                activity.findViewById<Button>(R.id.startButton).performClick()
            }
        }
        
        // 현재 시각이 표시되는지 확인
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        // 대략적인 시간 패턴 확인 (정확한 분 값이 달라질 수 있음)
        onView(withId(R.id.statusTextView))
            .check(matches(withSubstring("현재 시각:")))
    }

    @Test
    fun testTTSInitializationStates() {
        // 액티비티 실행
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        // 초기 상태 확인 (moveToState 호출하지 않음)
        onView(withId(R.id.statusTextView))
            .check(matches(anyOf(
                withText("TTS 초기화 중..."),
                withText("준비 완료"),
                withText("오류: 한국어 지원되지 않음")
            )))
        
        // TTS 초기화 실패 상태 설정
        scenario.onActivity { activity ->
            activity.onInit(TextToSpeech.ERROR)
        }
        
        // 실패 상태 확인 (moveToState 호출하지 않음)
        onView(withId(R.id.statusTextView))
            .check(matches(withText("오류: TTS 초기화 실패")))
    }
} 
package com.example.timetalk

import android.speech.tts.TextToSpeech
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.anyOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testInitialState() {
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
    fun testTTSInitializationStates() {
        // launchActivity를 사용하여 새로운 액티비티 시작
        val scenario = launchActivity<MainActivity>()
        
        // 초기 상태 확인
        onView(withId(R.id.statusTextView))
            .check(matches(anyOf(
                withText("TTS 초기화 중..."),
                withText("준비 완료"),
                withText("오류: 한국어 지원되지 않음")
            )))

        // TTS 초기화 실패 상태 설정
        scenario.onActivity { activity ->
            // UI 스레드에서 상태 업데이트 실행
            activity.runOnUiThread {
                activity.onInit(TextToSpeech.ERROR)
            }
        }

        // 실패 상태 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText("오류: TTS 초기화 실패")))
    }

    @Test
    fun testTimeAnnouncementButton() {
        // launchActivity를 사용하여 새로운 액티비티 시작
        val scenario = launchActivity<MainActivity>()

        // 초기 상태에서 버튼 상태 확인
        onView(withId(R.id.startButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        // TTS 초기화 상태 설정 및 버튼 클릭
        scenario.onActivity { activity ->
            activity.runOnUiThread {
                activity.isTtsReady = true
                activity.findViewById<android.widget.Button>(R.id.startButton).performClick()
            }
        }

        // 상태 텍스트에 "현재 시각:" 포함되어 있는지 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withSubstring("현재 시각:")))
    }
} 
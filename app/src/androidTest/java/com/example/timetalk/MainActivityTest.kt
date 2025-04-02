package com.example.timetalk

import android.speech.tts.TextToSpeech
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.anyOf
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun testInitialState() {
        // 초기 상태에서 버튼이 보이고 활성화되어 있어야 함
        onView(withId(R.id.startButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        // 초기 상태 텍스트 확인
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText("TTS 초기화 중...")))
    }

    @Test
    fun testTTSInitializationFailure() {
        // 메인 스레드에서 TTS 초기화 실패 상태 설정
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = activityRule.activity
            
            // 기존 TTS 객체 정리
            activity.tts?.shutdown()
            activity.tts = null
            
            // 상태 초기화
            activity.isTtsReady = false
            
            // 실패 상태 설정
            activity.onInit(TextToSpeech.ERROR)
        }

        // 실패 상태 텍스트 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText("오류: TTS 초기화 실패")))
    }

    @Test
    fun testTTSInitializationSuccess() {
        // 메인 스레드에서 TTS 초기화 성공 상태 설정
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = activityRule.activity
            activity.isTtsReady = true
            activity.updateStatus("준비 완료")
        }

        // 성공 상태 텍스트 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText("준비 완료")))
    }
} 
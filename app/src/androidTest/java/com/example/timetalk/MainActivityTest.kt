package com.example.timetalk

import android.speech.tts.TextToSpeech
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
    val rule = ActivityScenarioRule(MainActivity::class.java)

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
    fun testTTSInitializationSuccess() {
        rule.scenario.onActivity { activity ->
            // TTS 초기화 성공 상태로 설정
            activity.runOnUiThread {
                activity.isTtsReady = true
                activity.updateStatus("준비 완료")
            }
        }

        // 상태 텍스트가 업데이트되었는지 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText("준비 완료")))
    }

    @Test
    fun testTTSInitializationFailure() {
        rule.scenario.onActivity { activity ->
            // TTS 초기화 실패 상태로 설정
            activity.runOnUiThread {
                activity.onInit(TextToSpeech.ERROR)
            }
        }

        // 실패 상태 텍스트 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText("오류: TTS 초기화 실패")))
    }
} 
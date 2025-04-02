package com.example.timetalk

import android.speech.tts.TextToSpeech
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.anyOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import org.junit.After
import org.junit.Before
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private fun waitForCondition(timeoutInSeconds: Long = 5) {
        val latch = CountDownLatch(1)
        latch.await(timeoutInSeconds, TimeUnit.SECONDS)
    }

    @Test
    fun testInitialState() {
        // 버튼 상태 확인
        onView(withId(R.id.startButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        // 상태 텍스트 확인
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
        // 초기 상태 확인
        onView(withId(R.id.statusTextView))
            .check(matches(anyOf(
                withText("TTS 초기화 중..."),
                withText("준비 완료"),
                withText("오류: 한국어 지원되지 않음")
            )))

        // TTS 초기화 실패 상태 설정
        activityRule.scenario.onActivity { activity ->
            activity.post {
                activity.onInit(TextToSpeech.ERROR)
            }
        }

        // UI 업데이트 대기
        waitForCondition()

        // 실패 상태 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText("오류: TTS 초기화 실패")))
    }

    @Test
    fun testTimeAnnouncementButton() {
        // TTS 준비 상태로 설정
        activityRule.scenario.onActivity { activity ->
            activity.post {
                activity.isTtsReady = true
            }
        }

        // UI 업데이트 대기
        waitForCondition()

        // 버튼 클릭
        onView(withId(R.id.startButton)).perform(click())

        // UI 업데이트 대기
        waitForCondition()

        // 상태 텍스트에 "현재 시각:" 포함되어 있는지 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText(containsString("현재 시각:"))))
    }
} 
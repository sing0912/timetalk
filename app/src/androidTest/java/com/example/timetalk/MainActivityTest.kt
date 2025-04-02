package com.example.timetalk

import android.speech.tts.TextToSpeech
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        // 액티비티가 시작되면 버튼과 상태 텍스트뷰가 표시되어야 함
        activityRule.scenario.onActivity { activity ->
            // 메인 스레드에서 실행
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
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
        }
    }

    @Test
    fun testTTSInitializationStates() {
        activityRule.scenario.onActivity { activity ->
            // 메인 스레드에서 실행
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                // 초기 상태 확인
                onView(withId(R.id.statusTextView))
                    .check(matches(anyOf(
                        withText("TTS 초기화 중..."),
                        withText("준비 완료"),
                        withText("오류: 한국어 지원되지 않음")
                    )))

                // TTS 초기화 실패 상태 설정
                activity.onInit(TextToSpeech.ERROR)

                // 실패 상태 확인
                onView(withId(R.id.statusTextView))
                    .check(matches(withText("오류: TTS 초기화 실패")))
            }
        }
    }

    @Test
    fun testTimeAnnouncementButton() {
        activityRule.scenario.onActivity { activity ->
            // 메인 스레드에서 실행
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                // 초기 상태에서 버튼 상태 확인
                onView(withId(R.id.startButton))
                    .check(matches(isDisplayed()))
                    .check(matches(isEnabled()))

                // TTS 준비 상태로 설정
                activity.isTtsReady = true
                
                // 버튼 클릭
                activity.findViewById<android.widget.Button>(R.id.startButton).performClick()

                // 상태 텍스트에 "현재 시각:" 포함되어 있는지 확인
                onView(withId(R.id.statusTextView))
                    .check(matches(withText(containsString("현재 시각:"))))
            }
        }
    }
} 
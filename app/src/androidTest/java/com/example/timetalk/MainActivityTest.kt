package com.example.timetalk

import android.speech.tts.TextToSpeech
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            .check(matches(withText("TTS 초기화 중...")))
    }

    @Test
    fun testTimeAnnouncementButton() {
        // 액티비티 실행
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // 버튼 클릭
        onView(withId(R.id.startButton)).perform(click())

        // TTS가 초기화되지 않은 상태에서는 에러 메시지가 표시되어야 함
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))

        // TTS 초기화 상태 설정
        scenario.onActivity { activity ->
            activity.isTtsReady = true
        }

        // 버튼 다시 클릭
        onView(withId(R.id.startButton)).perform(click())

        // 현재 시각이 표시되는지 확인
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        onView(withId(R.id.statusTextView))
            .check(matches(withText("현재 시각: ${hour}시 ${minute}분")))
    }

    @Test
    fun testTTSInitializationStates() {
        // 액티비티 실행
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // 초기 상태 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText("TTS 초기화 중...")))

        // TTS 초기화 성공 상태 설정
        scenario.onActivity { activity ->
            activity.onInit(TextToSpeech.SUCCESS)
        }

        // 성공 상태 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText("준비 완료")))

        // 새로운 액티비티 시작
        scenario.recreate()

        // TTS 초기화 실패 상태 설정
        scenario.onActivity { activity ->
            activity.onInit(TextToSpeech.ERROR)
        }

        // 실패 상태 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText("오류: TTS 초기화 실패")))
    }
} 
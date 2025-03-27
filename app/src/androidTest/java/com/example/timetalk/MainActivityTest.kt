package com.example.timetalk

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Test
    fun testUIComponents() {
        // 액티비티 실행
        ActivityScenario.launch(MainActivity::class.java)

        // 모든 버튼이 화면에 표시되는지 확인
        onView(withId(R.id.startButton))
            .check(matches(isDisplayed()))
            .check(matches(withText("시간 알림 시작")))

        onView(withId(R.id.stopButton))
            .check(matches(isDisplayed()))
            .check(matches(withText("시간 알림 중지")))

        onView(withId(R.id.backgroundModeButton))
            .check(matches(isDisplayed()))
            .check(matches(withText("백그라운드 모드 시작")))

        onView(withId(R.id.exitButton))
            .check(matches(isDisplayed()))
            .check(matches(withText("앱 종료")))
    }

    @Test
    fun testStartButtonClick() {
        // 액티비티 실행
        ActivityScenario.launch(MainActivity::class.java)

        // 시작 버튼 클릭
        onView(withId(R.id.startButton)).perform(click())

        // 상태 텍스트가 업데이트되었는지 확인
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testStopButtonClick() {
        // 액티비티 실행
        ActivityScenario.launch(MainActivity::class.java)

        // 중지 버튼 클릭
        onView(withId(R.id.stopButton)).perform(click())

        // 상태 텍스트가 업데이트되었는지 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText(containsString("중지"))))
    }

    @Test
    fun testBackgroundModeButton() {
        // 액티비티 실행
        ActivityScenario.launch(MainActivity::class.java)

        // 백그라운드 모드 버튼 클릭
        onView(withId(R.id.backgroundModeButton)).perform(click())

        // 상태 텍스트가 업데이트되었는지 확인
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
    }
} 
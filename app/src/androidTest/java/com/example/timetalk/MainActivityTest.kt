package com.example.timetalk

import android.speech.tts.TextToSpeech
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.anyOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val latch = CountDownLatch(1)

    @Before
    fun setup() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @After
    fun cleanup() {
        scenario.close()
    }

    @Test
    fun testInitialState() {
        scenario.onActivity { activity ->
            // 초기 상태에서 버튼이 보이고 활성화되어 있어야 함
            onView(withId(R.id.startButton))
                .check(matches(isDisplayed()))
                .check(matches(isEnabled()))

            // 초기 상태 텍스트 확인
            onView(withId(R.id.statusTextView))
                .check(matches(isDisplayed()))
                .check(matches(withText("TTS 초기화 중...")))
        }
    }

    @Test
    fun testTTSInitializationSuccess() {
        scenario.onActivity { activity ->
            // TTS 초기화 성공 상태로 설정
            activity.runOnUiThread {
                activity.isTtsReady = true
                activity.updateStatus("준비 완료")
            }
        }

        // 상태가 업데이트될 때까지 잠시 대기
        Thread.sleep(1000)

        // 상태 텍스트가 업데이트되었는지 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText("준비 완료")))
    }

    @Test
    fun testTTSInitializationFailure() {
        var activity: MainActivity? = null
        
        // 먼저 현재 Activity 인스턴스를 가져옵니다
        scenario.onActivity { 
            activity = it 
        }
        
        // TTS 객체를 초기화하고 실패 상태로 설정
        activity?.let { mainActivity ->
            mainActivity.runOnUiThread {
                // 기존 TTS 객체 정리
                mainActivity.tts?.shutdown()
                mainActivity.tts = null
                
                // 상태 초기화
                mainActivity.isTtsReady = false
                
                // 실패 상태 설정
                mainActivity.onInit(TextToSpeech.ERROR)
                
                // 상태가 업데이트될 때까지 대기하도록 지연
                mainActivity.postDelayed({
                    latch.countDown()
                }, 1000)
            }
        }
        
        // 상태 업데이트 대기
        latch.await(2, TimeUnit.SECONDS)
        
        // 실패 상태 텍스트 확인
        onView(withId(R.id.statusTextView))
            .check(matches(withText("오류: TTS 초기화 실패")))
    }
} 
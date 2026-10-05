package com.logiclinear.reading.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class AppPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun 모델_기본값은_Haiku이고_선택은_재생성_후에도_유지된다() {
        val prefs = SharedPrefsAppPreferences(context)
        assertEquals(AiModel.HAIKU, prefs.model.value)
        assertEquals("claude-haiku-4-5", AiModel.DEFAULT.id)

        prefs.setModel(AiModel.SONNET)

        assertEquals(AiModel.SONNET, SharedPrefsAppPreferences(context).model.value)
        assertEquals(AiModel.HAIKU, AiModel.fromId("unknown"))
    }

    @Test
    fun 호출_횟수는_달마다_따로_세고_달이_바뀌면_0부터() = runTest {
        val prefs = SharedPrefsAppPreferences(context)
        val october = YearMonth.of(2026, 10)
        val november = YearMonth.of(2026, 11)

        assertEquals(1, prefs.recordCall(october))
        assertEquals(2, prefs.recordCall(october))
        assertEquals(2, prefs.observeCallsIn(october).first())
        assertEquals(0, prefs.callsIn(november))
        assertEquals(1, prefs.recordCall(november))
        assertEquals(2, SharedPrefsAppPreferences(context).callsIn(october))
    }
}

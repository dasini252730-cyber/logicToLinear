package com.logiclinear.reading.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.time.YearMonth

/**
 * Claude 모델 선택(요구사항 "비용 통제": 기본 Haiku, 설정에서 Sonnet). ID는 T-003 조사 결과.
 * 모델 ID·단가는 구현 시점에 공식 문서로 재확인한다(CLAUDE.md).
 */
enum class AiModel(val id: String) {
    HAIKU("claude-haiku-4-5"),
    SONNET("claude-sonnet-5");

    companion object {
        val DEFAULT = HAIKU

        fun fromId(id: String?): AiModel = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** 비밀이 아닌 설정과 월별 AI 호출 횟수. 비밀(API 키)은 [com.logiclinear.reading.data.secret.SecretStore]에 둔다. */
interface AppPreferences {
    val model: StateFlow<AiModel>

    fun setModel(model: AiModel)

    /** 이번 달 호출 횟수(요구사항 "비용 통제": 단순 카운트). 달이 바뀌면 0부터. */
    fun observeCallsIn(month: YearMonth): Flow<Int>

    fun callsIn(month: YearMonth): Int

    /** 호출 1회 기록. 호출이 있던 달의 횟수를 돌려준다. */
    fun recordCall(month: YearMonth): Int
}

class SharedPrefsAppPreferences(context: Context) : AppPreferences {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _model = MutableStateFlow(AiModel.fromId(prefs.getString(KEY_MODEL, null)))
    override val model: StateFlow<AiModel> = _model

    /** 호출 횟수 변경을 알리기 위한 틱. 값 자체는 prefs에서 읽는다. */
    private val callsTick = MutableStateFlow(0)

    override fun setModel(model: AiModel) {
        prefs.edit { putString(KEY_MODEL, model.id) }
        _model.value = model
    }

    override fun observeCallsIn(month: YearMonth): Flow<Int> = callsTick.map { callsIn(month) }

    override fun callsIn(month: YearMonth): Int = prefs.getInt(callsKey(month), 0)

    override fun recordCall(month: YearMonth): Int {
        val next = callsIn(month) + 1
        prefs.edit { putInt(callsKey(month), next) }
        callsTick.value++
        return next
    }

    private fun callsKey(month: YearMonth) = "$KEY_CALLS_PREFIX$month"

    private companion object {
        const val FILE_NAME = "app_prefs"
        const val KEY_MODEL = "ai_model"
        const val KEY_CALLS_PREFIX = "ai_calls_"
    }
}

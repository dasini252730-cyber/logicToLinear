package com.logiclinear.reading.ui.ai

import androidx.annotation.StringRes
import com.logiclinear.reading.R
import com.logiclinear.reading.data.remote.anthropic.AiResult

/**
 * AI 호출 실패 → 사용자 문구와 다음 동작(요구사항 "비용 통제": 네트워크 오류·키 오류는 보여주고 재시도 버튼;
 * "예외 처리": 키 미입력은 설정 화면으로 안내). 분석(T-609)과 토론(T-703) 화면이 같은 매핑을 쓴다.
 */
enum class AiErrorAction { RETRY, OPEN_SETTINGS, NONE }

@StringRes
fun AiResult.Failure.messageRes(): Int = when (this) {
    AiResult.NoKey -> R.string.ai_error_no_key
    AiResult.InvalidKey -> R.string.ai_error_invalid_key
    AiResult.RateLimited -> R.string.ai_error_rate_limited
    is AiResult.ServerError -> R.string.ai_error_server
    is AiResult.Network -> R.string.ai_error_network
    is AiResult.Rejected -> R.string.ai_error_rejected
    is AiResult.Incomplete -> R.string.ai_error_incomplete
}

fun AiResult.Failure.action(): AiErrorAction = when (this) {
    AiResult.NoKey, AiResult.InvalidKey -> AiErrorAction.OPEN_SETTINGS
    AiResult.RateLimited, is AiResult.ServerError, is AiResult.Network, is AiResult.Incomplete -> AiErrorAction.RETRY
    is AiResult.Rejected -> AiErrorAction.NONE
}

/** 버튼 문구. NONE이면 null(버튼 없음). */
@StringRes
fun AiErrorAction.labelRes(): Int? = when (this) {
    AiErrorAction.RETRY -> R.string.ai_retry
    AiErrorAction.OPEN_SETTINGS -> R.string.ai_open_settings
    AiErrorAction.NONE -> null
}

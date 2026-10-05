package com.logiclinear.reading.domain

import kotlinx.coroutines.CancellationException

/**
 * [runCatching]과 같지만 코루틴 취소([CancellationException])는 삼키지 않고 다시 던진다.
 * ViewModel이 사라지며 취소된 작업이 "실패"로 표시되는 것을 막는다.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

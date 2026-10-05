package com.logiclinear.reading.ocr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 촬영 → 문장 선택 화면 사이에 OCR 결과를 넘기는 메모리 보관소. 비트맵은 넣지 않는다(이미 폐기됨).
 * 선택 화면이 저장하거나 떠나면 [clear]한다. 프로세스가 죽으면 사라지는 것이 의도다: 사진 재촬영이 더 빠르다.
 */
class CaptureStore {
    private val _current = MutableStateFlow<PendingCapture?>(null)
    val current: StateFlow<PendingCapture?> = _current

    fun put(bookId: Long, result: OcrResult) {
        _current.value = PendingCapture(bookId, result)
    }

    fun clear() {
        _current.value = null
    }
}

/** 어느 책에 대한 촬영인지와 OCR 결과. */
data class PendingCapture(val bookId: Long, val result: OcrResult)

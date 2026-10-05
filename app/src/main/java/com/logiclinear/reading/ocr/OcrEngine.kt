package com.logiclinear.reading.ocr

import android.graphics.Bitmap

/**
 * OCR 엔진 추상화. 실제 구현은 [OcrRecognizer](ML Kit). 테스트는 가짜 구현을 넣는다.
 * 인스턴스는 AppContainer가 앱 프로세스 수명 동안 하나만 들고 있으므로 close 같은 수명 관리 메서드를 두지 않는다.
 * ML Kit 인식기는 프로세스 종료와 함께 정리된다.
 */
interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): OcrResult
}

package com.logiclinear.reading.ocr

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit 한국어 텍스트 인식 래퍼. 한국어 인식기는 라틴 문자도 함께 읽는다(T-004).
 * 비트맵은 호출자가 소유하며, 인식이 끝나면 호출자가 폐기한다(요구사항 "사진 원본 저장하지 않음").
 */
class OcrRecognizer(
    private val recognizer: TextRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
) : OcrEngine {
    /**
     * @param rotationDegrees 촬영 시 센서 회전(0/90/180/270). ML Kit이 이 값으로 이미지를 세워서 읽고,
     *   경계 상자도 세운 좌표로 돌려준다. 그래서 90·270이면 가로세로를 바꿔 이미지 크기를 기록한다.
     */
    override suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): OcrResult {
        val started = SystemClock.elapsedRealtime()
        val text = recognizer.process(InputImage.fromBitmap(bitmap, rotationDegrees)).awaitResult()
        val upright = rotationDegrees % 180 != 0
        val width = if (upright) bitmap.height else bitmap.width
        val height = if (upright) bitmap.width else bitmap.height
        val lines = toOcrLines(text.toRawLines(), height)
        val durationMs = SystemClock.elapsedRealtime() - started
        Log.d(TAG, "ocr: ${lines.size} lines in ${durationMs}ms (${width}x$height)")
        return OcrResult(lines, width, height, durationMs)
    }

    private companion object {
        const val TAG = "OcrRecognizer"
    }
}

private fun Text.toRawLines(): List<RawLine> = textBlocks.flatMap { block ->
    block.lines.mapNotNull { line ->
        val box = line.boundingBox ?: return@mapNotNull null
        RawLine(line.text, box.left, box.top, box.right, box.bottom)
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

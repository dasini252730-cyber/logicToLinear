package com.logiclinear.reading.ocr

/**
 * OCR 결과 한 줄. ML Kit `Text.TextBlock > Line`을 그대로 옮긴다(요구사항 "글귀 OCR과 문장 선택": Line 단위로 나열).
 * 좌표는 회전이 적용된(세운) 이미지 기준 픽셀. [topRatio]·[bottomRatio]는 이미지 높이 대비 0.0~1.0으로,
 * 페이지 번호 후보 판정(상단·하단 15%, T-207)에 쓴다.
 */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val topRatio: Float,
    val bottomRatio: Float,
)

/** 한 번의 촬영에 대한 OCR 결과. [lines]는 위에서 아래 순서. */
data class OcrResult(
    val lines: List<OcrLine>,
    /** 세운 이미지의 크기. */
    val imageWidth: Int,
    val imageHeight: Int,
    val durationMs: Long,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/** ML Kit 의존 없이 테스트할 수 있도록 뽑아낸 원시 줄. */
data class RawLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * 원시 줄을 [OcrLine]으로 바꾼다. 빈 텍스트는 버리고, 위에서 아래(top), 같은 높이면 왼쪽부터 정렬한다.
 * 비율은 0~1로 자른다(경계 상자가 이미지 밖으로 조금 나가는 경우가 있다).
 */
fun toOcrLines(raw: List<RawLine>, imageHeight: Int): List<OcrLine> {
    if (imageHeight <= 0) return emptyList()
    return raw
        .filter { it.text.isNotBlank() }
        .sortedWith(compareBy({ it.top }, { it.left }))
        .map {
            OcrLine(
                text = it.text.trim(),
                left = it.left,
                top = it.top,
                right = it.right,
                bottom = it.bottom,
                topRatio = (it.top.toFloat() / imageHeight).coerceIn(0f, 1f),
                bottomRatio = (it.bottom.toFloat() / imageHeight).coerceIn(0f, 1f),
            )
        }
}

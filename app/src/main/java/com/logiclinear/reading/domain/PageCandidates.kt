package com.logiclinear.reading.domain

import com.logiclinear.reading.ocr.OcrLine

/** 이미지 위·아래 몇 %를 페이지 번호 영역으로 보는지. 요구사항 "페이지 번호 자동 인식": 상단 15% 또는 하단 15%. */
const val PAGE_EDGE_RATIO = 0.15f

/**
 * 페이지 번호 후보. 상단·하단 가장자리 영역에 있고 숫자만 1~4자리인 줄을 위에서 아래 순서로, 중복 없이 돌려준다.
 * "- 123 -"처럼 장식 기호로 감싼 번호도 숫자만 남기고 본다. 본문 중간의 숫자는 영역 밖이라 제외된다.
 */
fun extractPageCandidates(lines: List<OcrLine>): List<Int> =
    lines
        .filter { it.topRatio <= PAGE_EDGE_RATIO || it.bottomRatio >= 1f - PAGE_EDGE_RATIO }
        .mapNotNull { parsePageNumber(it.text) }
        .distinct()

/** 숫자만 1~4자리면 그 값, 아니면 null. 양쪽의 하이픈·점·공백·가운뎃점 장식은 벗긴다. */
fun parsePageNumber(text: String): Int? {
    val core = text.trim().trim { it in DECORATION }
    if (!PAGE_NUMBER.matches(core)) return null
    return core.toInt().takeIf { it > 0 }
}

private val PAGE_NUMBER = Regex("^\\d{1,4}$")
private val DECORATION = setOf('-', '—', '–', '·', '•', '.', ' ', '|', '[', ']', '(', ')')

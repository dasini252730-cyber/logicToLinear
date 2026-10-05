package com.logiclinear.reading.domain

/**
 * 선택한 OCR 줄을 하나의 글귀로 합친다(요구사항 "글귀 OCR과 문장 선택").
 * - 줄 끝이 문장 부호면 공백 하나로 잇는다.
 * - 그 외(한국어 음절 중간에서 끊긴 줄바꿈)는 공백 없이 붙인다. 숫자·라틴 문자로 끝나도 같다:
 *   "1945년 8월 15" + "일에" → "1945년 8월 15일에", "ATP" + "를" → "ATP를".
 * - 줄 끝이 하이픈이면(영문 단어 분철) 하이픈을 떼고 붙인다.
 * 줄 순서는 호출자가 위에서 아래로 정렬해 넘긴다.
 */
fun joinLines(lines: List<String>): String {
    val parts = lines.map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return ""
    val out = StringBuilder(parts.first())
    for (next in parts.drop(1)) {
        when (out.last()) {
            '-' -> {
                out.setLength(out.length - 1)
                out.append(next)
            }
            in SENTENCE_PUNCTUATION -> out.append(' ').append(next)
            else -> out.append(next)
        }
    }
    return out.toString()
}

/**
 * 연속으로 선택한 줄 묶음(run)끼리는 [joinLines]로 합치고, 떨어진 묶음 사이는 줄바꿈으로 나눈다
 * (요구사항 "연속 선택 시 하나의 문장으로 합침"). [selected]는 0부터 시작하는 줄 인덱스.
 */
fun joinSelectedLines(lines: List<String>, selected: Set<Int>): String {
    val runs = mutableListOf<MutableList<String>>()
    var previous: Int? = null
    for (index in selected.filter { it in lines.indices }.sorted()) {
        if (previous == null || index != previous + 1) runs.add(mutableListOf())
        runs.last().add(lines[index])
        previous = index
    }
    return runs.map(::joinLines).filter { it.isNotEmpty() }.joinToString("\n")
}

/** 닫는 부호만 넣는다. 직선 따옴표(" ')는 여는지 닫는지 알 수 없어 넣지 않는다. */
private val SENTENCE_PUNCTUATION = setOf('.', ',', '!', '?', ';', ':', '。', '、', '”', '’', ')', '』', '」', '…')

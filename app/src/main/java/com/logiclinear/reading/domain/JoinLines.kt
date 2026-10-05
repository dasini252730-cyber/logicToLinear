package com.logiclinear.reading.domain

/**
 * 선택한 OCR 줄을 하나의 글귀로 합친다(요구사항 "글귀 OCR과 문장 선택").
 * - 줄 끝이 문장 부호면 공백 하나로 잇는다.
 * - 줄 끝이 라틴 문자·숫자면 단어 사이로 보고 공백 하나로 잇는다(영문 책).
 * - 줄 끝이 라틴 단어의 하이픈이면 하이픈을 떼고 붙인다(영문 단어 분철).
 * - 그 외(한국어 음절 중간에서 끊긴 줄바꿈)는 공백 없이 붙인다.
 * 줄 순서는 호출자가 위에서 아래로 정렬해 넘긴다.
 */
fun joinLines(lines: List<String>): String {
    val parts = lines.map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return ""
    val out = StringBuilder(parts.first())
    for (next in parts.drop(1)) {
        val last = out.last()
        when {
            last == '-' && out.length >= 2 && out[out.length - 2].isLatinLetter() -> {
                out.setLength(out.length - 1)
                out.append(next)
            }
            last in SENTENCE_PUNCTUATION || last.isLatinLetter() || last.isDigit() -> out.append(' ').append(next)
            else -> out.append(next)
        }
    }
    return out.toString()
}

private val SENTENCE_PUNCTUATION = setOf('.', ',', '!', '?', ';', ':', '。', '、', '"', '\'', '”', '’', ')', '』', '」', '…')

private fun Char.isLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

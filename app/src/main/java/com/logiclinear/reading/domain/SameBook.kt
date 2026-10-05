package com.logiclinear.reading.domain

/**
 * 동일 책 판정에 필요한 최소 정보. Room 엔티티와 백업 모델 양쪽에서 만든다.
 */
data class BookIdentity(val title: String, val author: String?, val isbn13: String?)

/**
 * 요구사항 "백업 > 가져오기": "isbn13 또는 제목+저자가 같은 책을 같은 책으로 보고".
 * - 양쪽에 isbn13이 있으면 isbn13만으로 판정한다(같은 제목의 다른 판본은 다른 책).
 * - 한쪽이라도 isbn13이 없으면 제목+저자로 판정한다. 앞뒤 공백·연속 공백·대소문자 차이는 무시한다.
 * - 저자가 비어 있으면 둘 다 비어 있을 때만 같다.
 */
fun isSameBook(a: BookIdentity, b: BookIdentity): Boolean {
    val isbnA = normalizeIsbn(a.isbn13)
    val isbnB = normalizeIsbn(b.isbn13)
    if (isbnA != null && isbnB != null) return isbnA == isbnB
    val titleA = normalizeText(a.title)
    if (titleA.isEmpty()) return false // 제목 없는 책끼리는 같은 책으로 묶지 않는다(손으로 고친 백업 대비)
    return titleA == normalizeText(b.title) && normalizeText(a.author) == normalizeText(b.author)
}

/** 숫자만 남긴다. 하이픈 표기(978-89-…)와 붙여 쓴 표기를 같게 본다. 숫자가 없으면 null. */
fun normalizeIsbn(isbn: String?): String? = isbn?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }

/** 앞뒤 공백 제거, 연속 공백 하나로, 소문자. null은 빈 문자열. */
fun normalizeText(text: String?): String = text.orEmpty().trim().replace(WHITESPACE, " ").lowercase()

private val WHITESPACE = Regex("\\s+")

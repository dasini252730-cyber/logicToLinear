package com.logiclinear.reading.domain

/** 요구사항 "소감": 별점은 1~5 정수, 한 줄은 100자 제한. 둘 다 필수는 아니다. */
const val RATING_MIN = 1
const val RATING_MAX = 5
const val ONE_LINER_MAX = 100

/** 완독 처리 입력을 저장 형태로 정리한다. 범위 밖 별점은 null, 한 줄은 공백 정리 후 100자에서 자른다. */
data class Review(val rating: Int?, val oneLiner: String?)

fun normalizeReview(rating: Int?, oneLiner: String?): Review {
    val validRating = rating?.takeIf { it in RATING_MIN..RATING_MAX }
    val trimmed = oneLiner?.trim()?.take(ONE_LINER_MAX)?.ifEmpty { null }
    return Review(validRating, trimmed)
}

/** 별점과 한 줄이 모두 비면 분석 품질 안내를 보여 준다(요구사항 "소감"). */
fun Review.isEmpty(): Boolean = rating == null && oneLiner == null

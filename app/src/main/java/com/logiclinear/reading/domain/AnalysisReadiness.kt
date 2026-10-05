package com.logiclinear.reading.domain

/** 요구사항 "확정된 결정": 분석·추천은 DONE 책 3권 이상 + 글귀 10개 이상일 때만 활성. */
const val ANALYSIS_MIN_BOOKS = 3
const val ANALYSIS_MIN_QUOTES = 10

/** 활성 조건 판정. 화면은 [missingBooks]·[missingQuotes]로 "책 N권, 글귀 M개 더 필요해요"를 만든다. */
data class AnalysisReadiness(val doneBooks: Int, val quotes: Int) {
    val missingBooks: Int get() = (ANALYSIS_MIN_BOOKS - doneBooks).coerceAtLeast(0)
    val missingQuotes: Int get() = (ANALYSIS_MIN_QUOTES - quotes).coerceAtLeast(0)
    val ready: Boolean get() = missingBooks == 0 && missingQuotes == 0
}

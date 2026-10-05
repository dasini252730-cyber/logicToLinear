package com.logiclinear.reading.domain

import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.Quote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 분석 호출 입력(요구사항 "취향 분석·추천 > 입력"). [text]가 user 메시지 본문이 되고,
 * [bookCount]·[quoteCount]는 Analysis.inputBookCount/inputQuoteCount에 쓴다.
 */
data class AnalysisInput(val text: String, val bookCount: Int, val quoteCount: Int)

/** 토론 한 회에서 뽑은 사용자 발언. */
data class DiscussionExcerpt(val bookTitle: String, val lastUserMessages: List<String>)

/** 각 토론에서 마지막 사용자 발언 3개(요구사항). */
const val DISCUSSION_LAST_USER_MESSAGES = 3

/**
 * 전체를 보낸다(요구사항 "확정된 결정 > 토큰 절약": 증분·샘플링 없음).
 * [allBooks]는 추천 제외 목록(WANT 포함, 요구사항 "이미 서재에 있는 제목은 제외")에 쓴다.
 */
fun buildAnalysisInput(
    allBooks: List<Book>,
    quotes: List<Quote>,
    discussions: List<DiscussionExcerpt> = emptyList(),
): AnalysisInput {
    val doneBooks = allBooks.filter { it.status == BookStatus.DONE }
    val titleById = allBooks.associate { it.id to it.title }
    val sb = StringBuilder()

    sb.appendLine("## 다 읽은 책 (${doneBooks.size}권)")
    if (doneBooks.isEmpty()) sb.appendLine("- (없음)")
    doneBooks.forEach { book ->
        val parts = buildList {
            add(book.title)
            add(book.author ?: "저자 미상")
            book.category?.let { add(it) }
            add(book.rating?.let { "별점 $it/5" } ?: "별점 없음")
            book.oneLiner?.let { add("한 줄: $it") }
            book.finishedAt?.let { add("완독 $it") }
        }
        sb.appendLine("- " + parts.joinToString(" | "))
    }

    sb.appendLine()
    sb.appendLine("## 저장한 글귀 (${quotes.size}개)")
    if (quotes.isEmpty()) sb.appendLine("- (없음)")
    quotes.forEach { quote ->
        val page = quote.page?.let { " p.$it" } ?: ""
        sb.appendLine("- [${titleById[quote.bookId] ?: "알 수 없는 책"}]$page ${quote.text.replace('\n', ' ')}")
    }

    val spoken = discussions.filter { it.lastUserMessages.isNotEmpty() }
    if (spoken.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("## 최근 토론에서 사용자가 한 말")
        spoken.forEach { d -> sb.appendLine("- [${d.bookTitle}] " + d.lastUserMessages.joinToString(" / ")) }
    }

    sb.appendLine()
    sb.appendLine("## 이미 서재에 있는 책 (추천에서 제외, ${allBooks.size}권)")
    if (allBooks.isEmpty()) sb.appendLine("- (없음)")
    allBooks.forEach { sb.appendLine("- ${it.title}" + (it.author?.let { a -> " ($a)" } ?: "")) }

    return AnalysisInput(text = sb.toString().trimEnd(), bookCount = doneBooks.size, quoteCount = quotes.size)
}

/**
 * Discussion.messagesJson(`[{role, content, at}]`, T-702)에서 마지막 사용자 발언 [limit]개를 시간 순으로.
 * 깨진 JSON이면 빈 목록. 분석이 토론 하나 때문에 실패하지 않게 한다.
 */
fun extractLastUserMessages(messagesJson: String, limit: Int = DISCUSSION_LAST_USER_MESSAGES): List<String> = runCatching {
    Json.parseToJsonElement(messagesJson).jsonArray
        .mapNotNull { element ->
            val obj = element.jsonObject
            val role = obj["role"]?.jsonPrimitive?.content
            val content = obj["content"]?.jsonPrimitive?.content
            if (role == "user" && !content.isNullOrBlank()) content.trim() else null
        }
        .takeLast(limit)
}.getOrDefault(emptyList())

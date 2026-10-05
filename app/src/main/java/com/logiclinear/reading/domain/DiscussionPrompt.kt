package com.logiclinear.reading.domain

import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.Quote
import com.logiclinear.reading.data.remote.anthropic.CacheControl
import com.logiclinear.reading.data.remote.anthropic.SystemBlock

/** 요구사항: 토론 max_tokens 400. */
const val DISCUSSION_MAX_TOKENS = 400

/**
 * 토론 system 블록 두 개(rules/ai-api.md): [역할 프롬프트, 책 컨텍스트]. 책 컨텍스트에 `cache_control: ephemeral`.
 * 역할 프롬프트는 res/raw/prompt_discussion_role.txt 에서 읽어 넘긴다(프롬프트는 리소스 파일).
 * T-003: Haiku 4.5는 접두부 4,096 토큰 미만이면 캐시되지 않는다. 글귀가 적은 책은 캐시 효과가 없어도 비용은 작다.
 */
fun buildDiscussionSystem(rolePrompt: String, book: Book, quotes: List<Quote>): List<SystemBlock> = listOf(
    SystemBlock(text = rolePrompt),
    SystemBlock(text = buildBookContext(book, quotes), cacheControl = CacheControl()),
)

/** 요구사항 "완독 후 토론": 제목·저자·소개글·별점·한 줄·글귀 전체. */
fun buildBookContext(book: Book, quotes: List<Quote>): String = buildString {
    appendLine("## 책")
    appendLine("- 제목: ${book.title}")
    appendLine("- 저자: ${book.author ?: "저자 미상"}")
    book.publisher?.let { appendLine("- 출판사: $it") }
    book.category?.let { appendLine("- 분류: $it") }
    book.description?.let { appendLine("- 소개글: ${it.replace('\n', ' ')}") }
    appendLine()
    appendLine("## 이 사람의 기록")
    appendLine("- 별점: ${book.rating?.let { "$it/5" } ?: "없음"}")
    appendLine("- 한 줄 소감: ${book.oneLiner ?: "없음"}")
    book.finishedAt?.let { appendLine("- 완독일: $it") }
    appendLine()
    appendLine("## 밑줄 치고 저장한 글귀 (${quotes.size}개, 저장 순)")
    if (quotes.isEmpty()) appendLine("- (없음)")
    quotes.sortedBy { it.createdAt }.forEach { q ->
        val page = q.page?.let { "p.$it " } ?: ""
        appendLine("- $page${q.text.replace('\n', ' ')}")
    }
}.trimEnd()

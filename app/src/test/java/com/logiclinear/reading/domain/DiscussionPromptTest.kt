package com.logiclinear.reading.domain

import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.Quote
import com.logiclinear.reading.data.remote.anthropic.CacheControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DiscussionPromptTest {
    private val book = Book(id = 1, title = "채식주의자", author = "한강", description = "소개\n둘째 줄", status = BookStatus.DONE, rating = 4, oneLiner = "서늘했다", finishedAt = LocalDate.of(2026, 9, 30))
    private val quotes = listOf(
        Quote(bookId = 1, text = "나중 글귀", page = 50, createdAt = Instant.ofEpochMilli(200)),
        Quote(bookId = 1, text = "먼저 글귀\n둘째 줄", page = null, createdAt = Instant.ofEpochMilli(100)),
    )

    @Test
    fun system_블록은_역할과_책_컨텍스트_둘이고_컨텍스트에만_cache_control이_붙는다() {
        val blocks = buildDiscussionSystem("역할", book, quotes)
        assertEquals(2, blocks.size)
        assertEquals("역할", blocks[0].text)
        assertNull(blocks[0].cacheControl)
        assertEquals(CacheControl("ephemeral"), blocks[1].cacheControl)
        assertTrue(blocks[1].text.startsWith("## 책"))
    }

    @Test
    fun 책_컨텍스트에_제목_저자_소개글_별점_한_줄_글귀_전체가_저장_순으로_들어간다() {
        val text = buildBookContext(book, quotes)
        assertTrue(text.contains("- 제목: 채식주의자"))
        assertTrue(text.contains("- 저자: 한강"))
        assertTrue(text.contains("- 소개글: 소개 둘째 줄"))
        assertTrue(text.contains("- 별점: 4/5"))
        assertTrue(text.contains("- 한 줄 소감: 서늘했다"))
        assertTrue(text.contains("- 완독일: 2026-09-30"))
        assertTrue(text.contains("글귀 (2개, 저장 순)"))
        assertTrue(text.indexOf("- 먼저 글귀 둘째 줄") < text.indexOf("- p.50 나중 글귀"))
    }

    @Test
    fun 비어_있는_값은_없음으로_표시된다() {
        val text = buildBookContext(Book(title = "제목만", status = BookStatus.DONE), emptyList())
        assertTrue(text.contains("- 저자: 저자 미상"))
        assertTrue(text.contains("- 별점: 없음"))
        assertTrue(text.contains("- 한 줄 소감: 없음"))
        assertTrue(text.contains("글귀 (0개, 저장 순)\n- (없음)"))
        assertEquals(400, DISCUSSION_MAX_TOKENS)
    }
}

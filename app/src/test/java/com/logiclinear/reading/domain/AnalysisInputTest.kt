package com.logiclinear.reading.domain

import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalysisInputTest {
    private val done = Book(id = 1, title = "채식주의자", author = "한강", category = "국내도서>소설", status = BookStatus.DONE, rating = 5, oneLiner = "서늘했다", finishedAt = LocalDate.of(2026, 9, 30))
    private val doneNoReview = Book(id = 2, title = "소년이 온다", author = null, status = BookStatus.DONE)
    private val want = Book(id = 3, title = "작별하지 않는다", author = "한강", status = BookStatus.WANT)
    private val reading = Book(id = 4, title = "흰", author = "한강", status = BookStatus.READING)
    private val quotes = listOf(
        Quote(id = 1, bookId = 1, text = "첫 줄\n둘째 줄", page = 12),
        Quote(id = 2, bookId = 4, text = "읽는 중 책의 글귀", page = null),
    )

    @Test
    fun DONE_책만_세고_글귀는_전체를_책_제목과_페이지와_함께_넣는다() {
        val input = buildAnalysisInput(listOf(done, doneNoReview, want, reading), quotes)

        assertEquals(2, input.bookCount)
        assertEquals(2, input.quoteCount)
        assertTrue(input.text.contains("## 다 읽은 책 (2권)"))
        assertTrue(input.text.contains("- 채식주의자 | 한강 | 국내도서>소설 | 별점 5/5 | 한 줄: 서늘했다 | 완독 2026-09-30"))
        assertTrue(input.text.contains("- 소년이 온다 | 저자 미상 | 별점 없음"))
        assertTrue(input.text.contains("- [채식주의자] p.12 첫 줄 둘째 줄")) // 줄바꿈은 공백으로
        assertTrue(input.text.contains("- [흰] 읽는 중 책의 글귀")) // 페이지 없으면 생략
        assertFalse(input.text.contains("## 최근 토론")) // 토론이 없으면 절 자체가 없다
    }

    @Test
    fun 서재_전체가_WANT_포함_제외_목록으로_들어간다() {
        val input = buildAnalysisInput(listOf(done, want, reading), emptyList())

        val exclusion = input.text.substringAfter("## 이미 서재에 있는 책 (추천에서 제외, 3권)")
        assertTrue(exclusion.contains("- 작별하지 않는다 (한강)"))
        assertTrue(exclusion.contains("- 흰 (한강)"))
        assertTrue(input.text.contains("## 저장한 글귀 (0개)\n- (없음)"))
    }

    @Test
    fun 빈_서재는_없음으로_표시되고_수는_0() {
        val input = buildAnalysisInput(emptyList(), emptyList())
        assertEquals(0, input.bookCount)
        assertEquals(0, input.quoteCount)
        assertTrue(input.text.contains("## 다 읽은 책 (0권)\n- (없음)"))
    }

    @Test
    fun 토론_발언은_책별로_한_줄씩_붙고_빈_토론은_건너뛴다() {
        val input = buildAnalysisInput(
            listOf(done), emptyList(),
            listOf(DiscussionExcerpt("채식주의자", listOf("무서웠어요", "그래도 끝까지 읽었어요")), DiscussionExcerpt("흰", emptyList())),
        )
        assertTrue(input.text.contains("- [채식주의자] 무서웠어요 / 그래도 끝까지 읽었어요"))
        assertFalse(input.text.contains("[흰]"))
    }

    @Test
    fun messagesJson에서_마지막_사용자_발언_3개를_순서대로_뽑고_깨진_JSON은_빈_목록() {
        val json = """[
            {"role":"assistant","content":"첫 질문","at":1},
            {"role":"user","content":"하나","at":2},
            {"role":"assistant","content":"꼬리 질문","at":3},
            {"role":"user","content":"둘","at":4},
            {"role":"user","content":"  ","at":5},
            {"role":"user","content":"셋","at":6},
            {"role":"user","content":"넷","at":7}
        ]"""
        assertEquals(listOf("둘", "셋", "넷"), extractLastUserMessages(json))
        assertEquals(listOf("넷"), extractLastUserMessages(json, limit = 1))
        assertEquals(emptyList<String>(), extractLastUserMessages("[]"))
        assertEquals(emptyList<String>(), extractLastUserMessages("깨진 json"))
    }
}

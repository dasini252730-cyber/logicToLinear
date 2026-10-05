package com.logiclinear.reading.data.ai

import com.logiclinear.reading.data.remote.books.BookSearchItem
import com.logiclinear.reading.data.remote.books.BookSearchResult
import com.logiclinear.reading.data.remote.books.BookSearch
import com.logiclinear.reading.domain.Recommendation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class RecommendationEnricherTest {
    private fun item(title: String, author: String, isbn: String, cover: String = "https://c/$isbn.jpg") =
        BookSearchItem(title = title, author = author, isbn13 = isbn, cover = cover, link = "https://a/$isbn")

    private class FakeBookSearch(private val answers: Map<String, BookSearchResult>, private val throwOn: Set<String> = emptySet()) : BookSearch {
        val queries = mutableListOf<String>()

        override suspend fun searchByTitle(title: String): BookSearchResult {
            queries += title
            if (title in throwOn) throw IOException("끊김")
            return answers[title] ?: BookSearchResult.Empty
        }
    }

    @Test
    fun 검색에_있는_책은_isbn_표지_링크가_붙고_없는_책은_그대로다() = runTest {
        val search = FakeBookSearch(
            mapOf(
                "채식주의자" to BookSearchResult.Found(listOf(item("채식주의자 (리마스터판)", "한강 (지은이)", "9788936434595"), item("채식주의자", "한강 (지은이)", "9788936433598"))),
                "없는 책" to BookSearchResult.Empty,
                "오류 책" to BookSearchResult.ApiError("429", "한도 초과"),
            ),
            throwOn = setOf("예외 책"),
        )
        val input = listOf(
            Recommendation("채식주의자", "한강", "이유1"),
            Recommendation("없는 책", "아무", "이유2"),
            Recommendation("오류 책", null, "이유3"),
            Recommendation("예외 책", null, "이유4"),
        )

        val out = RecommendationEnricher(search).enrich(input)

        assertEquals("9788936434595", out[0].isbn13) // 저자 일치 첫 항목
        assertEquals("https://c/9788936434595.jpg", out[0].coverUrl)
        assertEquals("https://a/9788936434595", out[0].storeUrl)
        assertEquals("이유1", out[0].reason)
        assertEquals(input[1], out[1])
        assertEquals(input[2], out[2])
        assertEquals(input[3], out[3]) // 예외가 전체를 실패시키지 않는다
        assertEquals(4, search.queries.size)
    }

    @Test
    fun 저자가_다르면_제목이_같은_항목_그것도_없으면_첫_항목() {
        val items = listOf(item("다른 책", "김훈", "1"), item("흰", "한강 (지은이)", "2"), item("흰 - 특별판", "한강", "3"))
        assertEquals("2", pickMatch(items, Recommendation("흰", "한강"))?.isbn13)
        assertEquals("2", pickMatch(items, Recommendation("흰", "모르는 저자"))?.isbn13) // 저자 불일치 → 제목으로
        assertNull(pickMatch(items, Recommendation("전혀 다른 제목", null))) // 저자도 제목도 안 맞으면 붙이지 않는다
        assertNull(pickMatch(emptyList(), Recommendation("흰", null)))
    }

    @Test
    fun 이미_isbn이_있으면_조회하지_않고_키_없음은_그대로_둔다() = runTest {
        val search = FakeBookSearch(mapOf("흰" to BookSearchResult.NoKey))
        val filled = Recommendation("채식주의자", "한강", "r", isbn13 = "9788936433598")

        val out = RecommendationEnricher(search).enrich(listOf(filled, Recommendation("흰", "한강", "r2")))

        assertEquals(filled, out[0])
        assertNull(out[1].isbn13)
        assertEquals(listOf("흰"), search.queries)
    }
}

package com.logiclinear.reading.data.ai

import com.logiclinear.reading.data.remote.books.BookSearchItem
import com.logiclinear.reading.data.remote.books.BookSearchResult
import com.logiclinear.reading.data.remote.books.BookSearch
import com.logiclinear.reading.domain.Recommendation
import com.logiclinear.reading.domain.normalizeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 추천 후처리(요구사항 "취향 분석·추천 > 후처리"): 각 추천을 카카오 책 검색(제목)로 찾아 isbn13·표지·링크를 붙인다.
 * 못 찾거나 검색이 실패하면 그 항목은 그대로 둔다(표지 없이 제목·저자·이유만 표시). 분석 전체는 실패하지 않는다.
 * 5건을 병렬로 조회한다. 카카오 검색 한도 대비 분석 1회의 5건은 문제가 되지 않는다.
 */
class RecommendationEnricher(private val search: BookSearch) {
    suspend fun enrich(recommendations: List<Recommendation>): List<Recommendation> = coroutineScope {
        recommendations.map { rec -> async { enrichOne(rec) } }.map { it.await() }
    }

    private suspend fun enrichOne(rec: Recommendation): Recommendation {
        if (rec.isbn13 != null) return rec // 이미 채워져 있으면 다시 조회하지 않는다
        val result = try {
            search.searchByTitle(rec.title)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return rec
        }
        val items = (result as? BookSearchResult.Found)?.items ?: return rec
        val match = pickMatch(items, rec) ?: return rec
        return rec.copy(
            isbn13 = match.isbn13.takeIf { it.isNotBlank() },
            coverUrl = match.cover.takeIf { it.isNotBlank() },
            storeUrl = match.link.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * 검색 결과에서 추천과 맞는 항목을 고른다. 저자가 있으면 저자 이름이 포함된 첫 항목, 없으면 제목이 같은 첫 항목.
 * 둘 다 안 맞으면 null — 남의 책 isbn13·표지가 붙어 서재에 저장되는 일을 막는다(요구사항 "못 찾으면 표지 없이").
 * 저자 표기와 제목 표기("채식주의자 - 개정판")의 차이는 느슨하게 본다.
 */
internal fun pickMatch(items: List<BookSearchItem>, rec: Recommendation): BookSearchItem? {
    if (items.isEmpty()) return null
    val author = rec.author?.let(::normalizeText)?.takeIf { it.isNotEmpty() }
    if (author != null) {
        items.firstOrNull { normalizeText(it.author).contains(author) }?.let { return it }
    }
    val title = normalizeText(rec.title)
    if (title.isEmpty()) return null
    return items.firstOrNull { normalizeText(it.title).startsWith(title) }
}

package com.logiclinear.reading.data.repo

import com.logiclinear.reading.data.db.BookDao
import com.logiclinear.reading.data.db.Quote
import com.logiclinear.reading.data.db.QuoteDao
import kotlinx.coroutines.flow.Flow

/**
 * 글귀 데이터 접근. [add]는 책의 lastQuoteAt도 함께 갱신한다(카메라 홈 기본 책 선택 기준).
 */
class QuoteRepository(
    private val quoteDao: QuoteDao,
    private val bookDao: BookDao,
) {
    fun observeByBook(bookId: Long): Flow<List<Quote>> = quoteDao.observeByBook(bookId)

    /** 분석 활성 조건(10개 이상)과 설정 표시용 전체 글귀 수. */
    fun observeCount(): Flow<Int> = quoteDao.countAll()

    /** 글귀를 저장하고 책의 lastQuoteAt을 글귀 시각으로 맞춘다. 새 글귀 id를 돌려준다. */
    suspend fun add(quote: Quote): Long {
        val id = quoteDao.insert(quote)
        bookDao.updateLastQuoteAt(quote.bookId, quote.createdAt)
        return id
    }

    suspend fun delete(quote: Quote) = quoteDao.delete(quote)

    /**
     * 삭제 되돌리기. 지웠던 Quote를 같은 id로 다시 넣는다.
     * 되돌리기 스낵바가 떠 있는 동안 책이 지워졌으면 외래키가 없어 넣을 수 없다. 그때는 false를 돌려주고 조용히 끝낸다.
     */
    suspend fun restore(quote: Quote): Boolean {
        if (bookDao.getById(quote.bookId) == null) return false
        quoteDao.insert(quote)
        return true
    }
}

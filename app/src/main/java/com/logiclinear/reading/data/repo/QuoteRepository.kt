package com.logiclinear.reading.data.repo

import com.logiclinear.reading.data.db.Quote
import com.logiclinear.reading.data.db.QuoteDao
import kotlinx.coroutines.flow.Flow

/**
 * 글귀 데이터 접근. 글귀 저장 시 책의 lastQuoteAt 갱신은 T-206에서 추가한다.
 */
class QuoteRepository(private val quoteDao: QuoteDao) {
    fun observeByBook(bookId: Long): Flow<List<Quote>> = quoteDao.observeByBook(bookId)

    /** 분석 활성 조건(10개 이상)과 설정 표시용 전체 글귀 수. */
    fun observeCount(): Flow<Int> = quoteDao.countAll()

    suspend fun add(quote: Quote): Long = quoteDao.insert(quote)

    suspend fun delete(quote: Quote) = quoteDao.delete(quote)

    /** 삭제 되돌리기. 지웠던 Quote를 같은 id로 다시 넣는다. */
    suspend fun restore(quote: Quote) {
        quoteDao.insert(quote)
    }
}

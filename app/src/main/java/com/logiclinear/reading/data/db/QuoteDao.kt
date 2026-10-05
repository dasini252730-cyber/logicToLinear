package com.logiclinear.reading.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface QuoteDao {
    /**
     * 새 글귀의 id를 돌려준다. 되돌리기(삭제 취소)는 지웠던 Quote를 id 그대로 다시 insert한다.
     */
    @Insert
    suspend fun insert(quote: Quote): Long

    @Query("UPDATE book SET lastQuoteAt = :at WHERE id = :bookId")
    suspend fun touchBookLastQuoteAt(bookId: Long, at: Instant)

    /**
     * 글귀 저장과 책의 lastQuoteAt 갱신을 한 트랜잭션으로 묶는다. 둘 중 하나만 반영되는 상태를 막는다
     * (카메라 홈 기본 책 선택 기준이 lastQuoteAt이다).
     */
    @Transaction
    suspend fun insertAndTouchBook(quote: Quote): Long {
        val id = insert(quote)
        touchBookLastQuoteAt(quote.bookId, quote.createdAt)
        return id
    }

    @Delete
    suspend fun delete(quote: Quote)

    /** 책 상세의 글귀 목록. 최신순. */
    @Query("SELECT * FROM quote WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeByBook(bookId: Long): Flow<List<Quote>>

    /** 분석 활성 조건(글귀 10개 이상)과 설정 화면 표시용. */
    @Query("SELECT COUNT(*) FROM quote")
    fun countAll(): Flow<Int>
}

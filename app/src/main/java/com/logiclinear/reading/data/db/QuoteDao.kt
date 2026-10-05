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

    /** 책의 lastQuoteAt을 남은 글귀 중 최신 시각으로 다시 계산한다. 글귀가 없으면 null. */
    @Query("UPDATE book SET lastQuoteAt = (SELECT MAX(createdAt) FROM quote WHERE bookId = :bookId) WHERE id = :bookId")
    suspend fun recomputeBookLastQuoteAt(bookId: Long)

    /**
     * 글귀 저장과 책의 lastQuoteAt 갱신을 한 트랜잭션으로 묶는다. 둘 중 하나만 반영되는 상태를 막는다
     * (카메라 홈 기본 책 선택 기준이 lastQuoteAt이다).
     */
    @Transaction
    suspend fun insertAndTouchBook(quote: Quote): Long {
        val id = insert(quote)
        recomputeBookLastQuoteAt(quote.bookId)
        return id
    }

    @Delete
    suspend fun delete(quote: Quote)

    /** 삭제 뒤 lastQuoteAt도 되돌린다. 가장 최근 글귀를 지우면 그 전 글귀 시각이 된다. */
    @Transaction
    suspend fun deleteAndTouchBook(quote: Quote) {
        delete(quote)
        recomputeBookLastQuoteAt(quote.bookId)
    }

    /** 책 상세의 글귀 목록. 최신순. */
    @Query("SELECT * FROM quote WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeByBook(bookId: Long): Flow<List<Quote>>

    /** 분석 활성 조건(글귀 10개 이상)과 설정 화면 표시용. */
    @Query("SELECT COUNT(*) FROM quote")
    fun countAll(): Flow<Int>

    /** 백업 내보내기(T-502). */
    @Query("SELECT * FROM quote ORDER BY id")
    suspend fun getAll(): List<Quote>

    /** 백업 합치기 중복 판정(T-506): 같은 책에 같은 글귀·시각이 이미 있는지. */
    @Query("SELECT COUNT(*) FROM quote WHERE bookId = :bookId AND text = :text AND createdAt = :createdAt")
    suspend fun countSame(bookId: Long, text: String, createdAt: Instant): Int
}

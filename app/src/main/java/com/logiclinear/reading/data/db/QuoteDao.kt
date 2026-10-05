package com.logiclinear.reading.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    /**
     * 새 글귀의 id를 돌려준다. 되돌리기(삭제 취소)는 지웠던 Quote를 id 그대로 다시 insert한다.
     */
    @Insert
    suspend fun insert(quote: Quote): Long

    @Delete
    suspend fun delete(quote: Quote)

    /** 책 상세의 글귀 목록. 최신순. */
    @Query("SELECT * FROM quote WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeByBook(bookId: Long): Flow<List<Quote>>

    /** 분석 활성 조건(글귀 10개 이상)과 설정 화면 표시용. */
    @Query("SELECT COUNT(*) FROM quote")
    fun countAll(): Flow<Int>
}

package com.logiclinear.reading.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscussionDao {
    /** 새 토론이면 insert, 진행 중 토론이면 메시지 갱신. 매 턴 호출된다. */
    @Upsert
    suspend fun upsert(discussion: Discussion): Long

    /** 책 상세의 토론 목록. 최신 시작 순. */
    @Query("SELECT * FROM discussion WHERE bookId = :bookId ORDER BY startedAt DESC")
    fun observeByBook(bookId: Long): Flow<List<Discussion>>

    @Query("SELECT * FROM discussion WHERE id = :id")
    suspend fun getById(id: Long): Discussion?
}

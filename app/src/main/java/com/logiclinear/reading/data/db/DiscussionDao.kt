package com.logiclinear.reading.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface DiscussionDao {
    /** 새 토론이면 insert, 진행 중 토론이면 메시지 갱신. 매 턴 호출된다. */
    @Upsert
    suspend fun upsert(discussion: Discussion): Long

    /** 책 상세의 토론 목록. 최신 시작 순. */
    @Query("SELECT * FROM discussion WHERE bookId = :bookId ORDER BY startedAt DESC")
    fun observeByBook(bookId: Long): Flow<List<Discussion>>

    /** 백업 내보내기(T-502). */
    @Query("SELECT * FROM discussion ORDER BY id")
    suspend fun getAll(): List<Discussion>

    /** 백업 합치기 중복 판정(T-506). */
    @Query("SELECT COUNT(*) FROM discussion WHERE bookId = :bookId AND startedAt = :startedAt")
    suspend fun countSame(bookId: Long, startedAt: Instant): Int
}

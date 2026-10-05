package com.logiclinear.reading.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface AnalysisDao {
    @Insert
    suspend fun insert(analysis: Analysis): Long

    /** 분석 화면: 첫 번째가 최신 결과, 나머지는 접힌 이전 기록. */
    @Query("SELECT * FROM analysis ORDER BY runAt DESC, id DESC")
    fun observeAllDesc(): Flow<List<Analysis>>

    /** 백업 내보내기(T-502). */
    @Query("SELECT * FROM analysis ORDER BY id")
    suspend fun getAll(): List<Analysis>

    /** 백업 덮어쓰기(T-505). Analysis는 외래키가 없어 따로 지운다. */
    @Query("DELETE FROM analysis")
    suspend fun deleteAll()

    /** 백업 합치기 중복 판정(T-506). */
    @Query("SELECT COUNT(*) FROM analysis WHERE runAt = :runAt")
    suspend fun countSame(runAt: Instant): Int
}

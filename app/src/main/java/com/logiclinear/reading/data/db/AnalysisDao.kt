package com.logiclinear.reading.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {
    @Insert
    suspend fun insert(analysis: Analysis): Long

    /** 분석 화면: 첫 번째가 최신 결과, 나머지는 접힌 이전 기록. */
    @Query("SELECT * FROM analysis ORDER BY runAt DESC")
    fun observeAllDesc(): Flow<List<Analysis>>
}

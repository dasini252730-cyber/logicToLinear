package com.logiclinear.reading.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * 취향 분석·추천 실행 이력 한 건. 책과 무관하게 쌓인다.
 */
@Entity(tableName = "analysis")
data class Analysis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runAt: Instant = nowMillis(),
    /** AI가 쓴 취향 해석 글. JSON 파싱에 실패하면 응답 원문이 그대로 들어간다. */
    val tasteText: String,
    /** [{title, author, reason, isbn13?, coverUrl?, storeUrl?}] JSON 배열. 파싱 실패 시 "[]". */
    val recommendationsJson: String = "[]",
    /** 실행 당시 DONE 책 수. */
    val inputBookCount: Int,
    /** 실행 당시 글귀 수. */
    val inputQuoteCount: Int,
)

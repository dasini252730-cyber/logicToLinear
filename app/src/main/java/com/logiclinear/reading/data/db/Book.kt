package com.logiclinear.reading.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * 책 한 권. 요구사항 "데이터 모델 > Book" 표의 14개 필드와 1:1.
 * rating·oneLiner·finishedAt는 DONE일 때만 의미가 있고, 다시 읽기(DONE→READING) 때도 지우지 않는다.
 */
@Entity(
    tableName = "book",
    indices = [Index("status"), Index("isbn13")],
)
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 필수. 알라딘 검색 또는 직접 입력. */
    val title: String,
    val author: String? = null,
    val publisher: String? = null,
    /** 알라딘 등록 시에만 채워진다. 직접 입력 책은 null. */
    val isbn13: String? = null,
    /** 알라딘 표지 URL. 오프라인 표시는 이미지 캐시가 담당한다. */
    val coverUrl: String? = null,
    /** 알라딘 categoryName. 예: 국내도서>소설/시/희곡>한국소설 */
    val category: String? = null,
    /** 알라딘 소개글. 분석·토론 프롬프트에 쓴다. */
    val description: String? = null,
    val status: BookStatus = BookStatus.READING,
    /** 1~5. DONE일 때만. */
    val rating: Int? = null,
    /** 한 줄 소감, 100자 제한. DONE일 때만. */
    val oneLiner: String? = null,
    /** 완독일. DONE일 때만. 읽은 시기는 이 값 하나만 기록한다. */
    val finishedAt: LocalDate? = null,
    val createdAt: Instant = nowMillis(),
    /** 마지막 글귀 저장 시각. 카메라 홈의 기본 책 선택 기준. */
    val lastQuoteAt: Instant? = null,
)

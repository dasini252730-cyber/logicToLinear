package com.logiclinear.reading.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * 글귀 한 건. 한 번 촬영에서 선택한 줄 전체가 Quote 1건이다.
 * 책이 지워지면 함께 지워진다(요구사항 "데이터 모델 > Quote", "삭제").
 */
@Entity(
    tableName = "quote",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class Quote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** 사용자가 선택하고 편집한 문장(들). */
    val text: String,
    /** OCR 인식 또는 직접 입력. 건너뛰면 null. */
    val page: Int? = null,
    val createdAt: Instant = nowMillis(),
)

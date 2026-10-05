package com.logiclinear.reading.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * 완독 후 AI 토론 한 회. 책당 여러 개 가능. 매 턴 저장되어 강제 종료 뒤에도 이어갈 수 있다.
 * 책이 지워지면 함께 지워진다.
 */
@Entity(
    tableName = "discussion",
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
data class Discussion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** [{role, content, at}] JSON 배열. 사용자 20턴 = 최대 40개. 직렬화는 T-702에서. */
    val messagesJson: String = "[]",
    val startedAt: Instant = nowMillis(),
    /** 20턴 마무리 또는 종료 시각. null이면 진행 중. */
    val endedAt: Instant? = null,
)

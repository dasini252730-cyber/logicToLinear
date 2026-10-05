package com.logiclinear.reading.data.backup

import com.logiclinear.reading.data.db.Analysis
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.DB_SCHEMA_VERSION
import com.logiclinear.reading.data.db.Discussion
import com.logiclinear.reading.data.db.Quote
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/**
 * 백업 JSON(요구사항 "백업"). 최상위 `schemaVersion`은 Room 버전과 같은 상수 [DB_SCHEMA_VERSION]에서 나온다.
 * API 키는 넣지 않는다. 표지는 URL만. 시각은 epoch 밀리초, 날짜는 ISO 문자열(Room 저장 형식과 같다).
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int = DB_SCHEMA_VERSION,
    val exportedAt: Long,
    val books: List<BookBackup>,
    val quotes: List<QuoteBackup>,
    val discussions: List<DiscussionBackup>,
    val analyses: List<AnalysisBackup>,
)

@Serializable
data class BookBackup(
    val id: Long,
    val title: String,
    val author: String? = null,
    val publisher: String? = null,
    val isbn13: String? = null,
    val coverUrl: String? = null,
    val category: String? = null,
    val description: String? = null,
    val status: String,
    val rating: Int? = null,
    val oneLiner: String? = null,
    val finishedAt: String? = null,
    val createdAt: Long,
    val lastQuoteAt: Long? = null,
)

@Serializable
data class QuoteBackup(val id: Long, val bookId: Long, val text: String, val page: Int? = null, val createdAt: Long)

@Serializable
data class DiscussionBackup(val id: Long, val bookId: Long, val messagesJson: String, val startedAt: Long, val endedAt: Long? = null)

@Serializable
data class AnalysisBackup(
    val id: Long,
    val runAt: Long,
    val tasteText: String,
    val recommendationsJson: String,
    val inputBookCount: Int,
    val inputQuoteCount: Int,
)

/** 백업 파일 직렬화 설정. 모르는 키는 무시해 앞으로의 필드 추가에 견딘다. */
val backupJson: Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

// ---- 엔티티 ↔ 백업 변환 ----

fun Book.toBackup() = BookBackup(
    id = id, title = title, author = author, publisher = publisher, isbn13 = isbn13, coverUrl = coverUrl,
    category = category, description = description, status = status.name, rating = rating, oneLiner = oneLiner,
    finishedAt = finishedAt?.toString(), createdAt = createdAt.toEpochMilli(), lastQuoteAt = lastQuoteAt?.toEpochMilli(),
)

/** 모르는 상태 문자열은 READING으로, 손상된 날짜는 null로 읽는다(손으로 고친 파일에 견디기). */
fun BookBackup.toEntity(id: Long = this.id) = Book(
    id = id, title = title, author = author, publisher = publisher, isbn13 = isbn13, coverUrl = coverUrl,
    category = category, description = description,
    status = runCatching { BookStatus.valueOf(status) }.getOrDefault(BookStatus.READING),
    rating = rating?.takeIf { it in 1..5 }, oneLiner = oneLiner,
    finishedAt = finishedAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    createdAt = Instant.ofEpochMilli(createdAt), lastQuoteAt = lastQuoteAt?.let(Instant::ofEpochMilli),
)

fun Quote.toBackup() = QuoteBackup(id, bookId, text, page, createdAt.toEpochMilli())

fun QuoteBackup.toEntity(bookId: Long = this.bookId, id: Long = this.id) =
    Quote(id = id, bookId = bookId, text = text, page = page, createdAt = Instant.ofEpochMilli(createdAt))

fun Discussion.toBackup() = DiscussionBackup(id, bookId, messagesJson, startedAt.toEpochMilli(), endedAt?.toEpochMilli())

fun DiscussionBackup.toEntity(bookId: Long = this.bookId, id: Long = this.id) = Discussion(
    id = id, bookId = bookId, messagesJson = messagesJson,
    startedAt = Instant.ofEpochMilli(startedAt), endedAt = endedAt?.let(Instant::ofEpochMilli),
)

fun Analysis.toBackup() = AnalysisBackup(id, runAt.toEpochMilli(), tasteText, recommendationsJson, inputBookCount, inputQuoteCount)

fun AnalysisBackup.toEntity(id: Long = this.id) = Analysis(
    id = id, runAt = Instant.ofEpochMilli(runAt), tasteText = tasteText,
    recommendationsJson = recommendationsJson, inputBookCount = inputBookCount, inputQuoteCount = inputQuoteCount,
)

package com.logiclinear.reading.data.backup

import com.logiclinear.reading.data.db.Analysis
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.DB_SCHEMA_VERSION
import com.logiclinear.reading.data.db.Discussion
import com.logiclinear.reading.data.db.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackupFileTest {
    private val book = Book(
        id = 7, title = "책", author = "저자", isbn13 = "9780000000001", status = BookStatus.DONE, rating = 4,
        oneLiner = "좋았다", finishedAt = LocalDate.of(2026, 1, 2), createdAt = Instant.ofEpochMilli(1_000), lastQuoteAt = Instant.ofEpochMilli(2_000),
    )
    private val file = BackupFile(
        exportedAt = 5_000,
        books = listOf(book.toBackup()),
        quotes = listOf(Quote(id = 1, bookId = 7, text = "글귀", page = 12, createdAt = Instant.ofEpochMilli(3_000)).toBackup()),
        discussions = listOf(Discussion(id = 2, bookId = 7, messagesJson = "[]", startedAt = Instant.ofEpochMilli(4_000)).toBackup()),
        analyses = listOf(Analysis(id = 3, runAt = Instant.ofEpochMilli(6_000), tasteText = "t", inputBookCount = 3, inputQuoteCount = 10).toBackup()),
    )

    @Test
    fun 직렬화_역직렬화_라운드트립() {
        val text = backupJson.encodeToString(BackupFile.serializer(), file)
        val back = backupJson.decodeFromString(BackupFile.serializer(), text)

        assertEquals(file, back)
        assertEquals(book, back.books.single().toEntity())
        assertEquals(DB_SCHEMA_VERSION, back.schemaVersion)
        assertFalse(text.contains("api", ignoreCase = true) && text.contains("key", ignoreCase = true))
    }

    @Test
    fun 손상된_상태와_날짜는_안전한_값으로_읽는다() {
        val entity = book.toBackup().copy(status = "ARCHIVED", finishedAt = "어제", rating = 9).toEntity()
        assertEquals(BookStatus.READING, entity.status)
        assertNull(entity.finishedAt)
        assertNull(entity.rating)
    }

    @Test
    fun 모르는_키는_무시한다() {
        val text = """{"schemaVersion":1,"exportedAt":1,"books":[],"quotes":[],"discussions":[],"analyses":[],"future":true}"""
        assertEquals(0, backupJson.decodeFromString(BackupFile.serializer(), text).books.size)
    }
}

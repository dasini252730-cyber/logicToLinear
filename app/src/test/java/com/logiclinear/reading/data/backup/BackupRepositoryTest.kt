package com.logiclinear.reading.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.data.db.Analysis
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.Discussion
import com.logiclinear.reading.data.db.Quote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = BackupRepository(db)
    }

    @After
    fun tearDown() = db.close()

    /** 책 2권(검색 등록 1, 직접 입력 1), 글귀 3, 토론 1, 분석 1이 든 백업. 책 id는 DB와 겹치지 않게 큰 값. */
    private fun sampleBackup() = BackupFile(
        exportedAt = 1_000,
        books = listOf(
            BookBackup(id = 101, title = "채식주의자", author = "한강", isbn13 = "9788936433598", status = "DONE", rating = 5, createdAt = 10),
            BookBackup(id = 102, title = "직접 입력 책", author = null, isbn13 = null, status = "READING", createdAt = 20),
        ),
        quotes = listOf(
            QuoteBackup(id = 1, bookId = 101, text = "첫 글귀", page = 3, createdAt = 100),
            QuoteBackup(id = 2, bookId = 101, text = "둘째 글귀", createdAt = 200),
            QuoteBackup(id = 3, bookId = 102, text = "셋째 글귀", createdAt = 300),
            QuoteBackup(id = 4, bookId = 999, text = "책 없는 글귀", createdAt = 400),
        ),
        discussions = listOf(DiscussionBackup(id = 1, bookId = 101, messagesJson = "[]", startedAt = 500)),
        analyses = listOf(AnalysisBackup(id = 1, runAt = 600, tasteText = "취향", recommendationsJson = "[]", inputBookCount = 1, inputQuoteCount = 2)),
    )

    private suspend fun seedExisting(): Long {
        val id = db.bookDao().insert(Book(title = "기존 책", author = "기존 저자"))
        db.quoteDao().insertAndTouchBook(Quote(bookId = id, text = "기존 글귀", createdAt = Instant.ofEpochMilli(50)))
        db.discussionDao().upsert(Discussion(bookId = id, startedAt = Instant.ofEpochMilli(60)))
        db.analysisDao().insert(Analysis(runAt = Instant.ofEpochMilli(70), tasteText = "기존", inputBookCount = 1, inputQuoteCount = 1))
        return id
    }

    @Test
    fun 내보내기는_4테이블_전체를_담고_키_문자열이_없다() = runTest {
        seedExisting()
        val file = repo.export(Instant.ofEpochMilli(1_234))
        assertEquals(1_234, file.exportedAt)
        assertEquals(listOf(1, 1, 1, 1), listOf(file.books.size, file.quotes.size, file.discussions.size, file.analyses.size))
        assertEquals(file.books.single().id, file.quotes.single().bookId)
        val text = backupJson.encodeToString(BackupFile.serializer(), file)
        assertFalse(text.contains("apiKey") || text.contains("kakao", ignoreCase = true) || text.contains("anthropic", ignoreCase = true))
    }

    @Test
    fun 덮어쓰기는_기존을_지우고_백업_수와_같아지며_bookId를_다시_연결한다() = runTest {
        seedExisting()
        val summary = repo.overwrite(sampleBackup())

        assertEquals(ImportSummary(books = 2, quotes = 3, discussions = 1, analyses = 1), summary)
        val books = db.bookDao().getAll()
        assertEquals(2, books.size)
        assertEquals(3, db.quoteDao().countAll().first())
        assertEquals(1, db.analysisDao().getAll().size)
        val vegetarian = books.single { it.isbn13 == "9788936433598" }
        assertNotEquals(101L, vegetarian.id) // id는 새로 받는다
        assertEquals(BookStatus.DONE, vegetarian.status)
        assertEquals(2, db.quoteDao().observeByBook(vegetarian.id).first().size)
        assertEquals(Instant.ofEpochMilli(200), vegetarian.lastQuoteAt)
        assertEquals(1, db.discussionDao().observeByBook(vegetarian.id).first().size)
    }

    @Test
    fun 합치기는_같은_책에_글귀만_더하고_두_번_합쳐도_늘지_않는다() = runTest {
        val existingId = seedExisting()
        // isbn 없이 제목+저자(공백·대소문자 차이)만 같은 책을 기존에 둔다.
        val manualId = db.bookDao().insert(Book(title = " 직접  입력 책", author = null))

        val first = repo.merge(sampleBackup())
        assertEquals(ImportSummary(books = 1, quotes = 3, discussions = 1, analyses = 1), first)
        assertEquals(3, db.bookDao().getAll().size) // 기존 2 + 새 1(채식주의자)
        assertEquals(1, db.quoteDao().observeByBook(manualId).first().size) // 셋째 글귀가 기존 직접 입력 책에 붙는다
        assertEquals(1, db.quoteDao().observeByBook(existingId).first().size) // 기존 책은 그대로

        val second = repo.merge(sampleBackup())
        assertEquals(ImportSummary(), second)
        assertEquals(3, db.bookDao().getAll().size)
        assertEquals(4, db.quoteDao().countAll().first())
        assertEquals(2, db.analysisDao().getAll().size)
    }

    @Test
    fun 합치기는_isbn이_같으면_제목이_달라도_같은_책이고_다르면_새_책이다() = runTest {
        val sameIsbn = db.bookDao().insert(Book(title = "The Vegetarian", author = "Han Kang", isbn13 = "9788936433598"))
        repo.merge(sampleBackup())

        val books = db.bookDao().getAll()
        assertEquals(2, books.size)
        assertEquals(2, db.quoteDao().observeByBook(sameIsbn).first().size)
        assertTrue(books.any { it.title == "직접 입력 책" })
    }

    @Test
    fun 내보낸_백업을_덮어쓰기로_되살리면_수가_같다() = runTest {
        seedExisting()
        val file = repo.export()
        db.bookDao().deleteAll()
        db.analysisDao().deleteAll()

        repo.overwrite(file)

        assertEquals(1, db.bookDao().getAll().size)
        assertEquals(1, db.quoteDao().countAll().first())
        assertEquals(1, db.analysisDao().getAll().size)
    }
}

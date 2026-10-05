package com.logiclinear.reading.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** Room 스키마·외래키·DAO 쿼리를 JVM(Robolectric SQLite)에서 검증한다. */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun 책_삭제시_글귀와_토론이_함께_삭제된다() = runTest {
        val bookId = db.bookDao().insert(Book(title = "채식주의자", author = "한강"))
        db.quoteDao().insert(Quote(bookId = bookId, text = "첫 문장", page = 12))
        db.quoteDao().insert(Quote(bookId = bookId, text = "둘째 문장"))
        db.discussionDao().upsert(Discussion(bookId = bookId))
        assertEquals(2, db.quoteDao().countAll().first())

        db.bookDao().delete(db.bookDao().getById(bookId)!!)

        assertEquals(0, db.quoteDao().countAll().first())
        assertTrue(db.quoteDao().observeByBook(bookId).first().isEmpty())
        assertTrue(db.discussionDao().observeByBook(bookId).first().isEmpty())
    }

    @Test
    fun 기본값_시각은_저장_전후가_같다() = runTest {
        val book = Book(title = "시각")
        val id = db.bookDao().insert(book)

        val saved = db.bookDao().getById(id)!!

        assertEquals(book.createdAt, saved.createdAt)
        assertEquals(0L, book.createdAt.nano % 1_000_000L)
    }

    @Test
    fun 상태별_조회는_해당_상태만_최근등록순() = runTest {
        val older = db.bookDao().insert(Book(title = "A", status = BookStatus.READING, createdAt = Instant.ofEpochMilli(1_000)))
        val newer = db.bookDao().insert(Book(title = "B", status = BookStatus.READING, createdAt = Instant.ofEpochMilli(2_000)))
        db.bookDao().insert(Book(title = "C", status = BookStatus.WANT))

        val reading = db.bookDao().observeByStatus(BookStatus.READING).first()

        assertEquals(listOf(newer, older), reading.map { it.id })
        assertEquals(listOf("C"), db.bookDao().observeByStatus(BookStatus.WANT).first().map { it.title })
    }

    @Test
    fun 기본_선택_책은_lastQuoteAt이_최근인_READING_책() = runTest {
        db.bookDao().insert(Book(title = "글귀 없음", createdAt = Instant.ofEpochMilli(9_000)))
        val recent = db.bookDao().insert(Book(title = "최근", lastQuoteAt = Instant.ofEpochMilli(5_000)))
        db.bookDao().insert(Book(title = "예전", lastQuoteAt = Instant.ofEpochMilli(1_000)))
        db.bookDao().insert(Book(title = "다 읽음", status = BookStatus.DONE, lastQuoteAt = Instant.ofEpochMilli(8_000)))

        assertEquals(recent, db.bookDao().observeLatestByQuote(BookStatus.READING).first()?.id)
    }

    @Test
    fun 기본_선택_책은_READING이_없으면_null() = runTest {
        db.bookDao().insert(Book(title = "읽고 싶음", status = BookStatus.WANT))
        assertNull(db.bookDao().observeLatestByQuote(BookStatus.READING).first())
    }

    @Test
    fun id로_관찰하면_갱신이_반영되고_없는_id는_null() = runTest {
        val id = db.bookDao().insert(Book(title = "원래"))
        db.bookDao().update(db.bookDao().getById(id)!!.copy(title = "바뀜"))

        assertEquals("바뀜", db.bookDao().observeById(id).first()?.title)
        assertNull(db.bookDao().observeById(id + 999).first())
    }

    @Test
    fun 제목저자_중복찾기는_저자_null도_비교한다() = runTest {
        val noAuthor = db.bookDao().insert(Book(title = "무명", author = null))
        val withAuthor = db.bookDao().insert(Book(title = "무명", author = "김"))

        assertEquals(noAuthor, db.bookDao().findByTitleAndAuthor("무명", null)?.id)
        assertEquals(withAuthor, db.bookDao().findByTitleAndAuthor("무명", "김")?.id)
        assertNull(db.bookDao().findByTitleAndAuthor("무명", "이"))
    }

    @Test
    fun 글귀_되돌리기는_같은_id로_다시_넣을_수_있다() = runTest {
        val bookId = db.bookDao().insert(Book(title = "책"))
        val quote = Quote(bookId = bookId, text = "지울 문장")
        val id = db.quoteDao().insert(quote)
        val saved = db.quoteDao().observeByBook(bookId).first().single()
        db.quoteDao().delete(saved)
        assertTrue(db.quoteDao().observeByBook(bookId).first().isEmpty())

        val restoredId = db.quoteDao().insert(saved)

        assertEquals(id, restoredId)
        assertEquals("지울 문장", db.quoteDao().observeByBook(bookId).first().single().text)
    }

    @Test
    fun 토론_upsert는_같은_id를_갱신한다() = runTest {
        val bookId = db.bookDao().insert(Book(title = "책"))
        val id = db.discussionDao().upsert(Discussion(bookId = bookId, messagesJson = "[]"))
        db.discussionDao().upsert(Discussion(id = id, bookId = bookId, messagesJson = "[{\"role\":\"assistant\"}]"))

        val all = db.discussionDao().observeByBook(bookId).first()
        assertEquals(1, all.size)
        assertEquals("[{\"role\":\"assistant\"}]", all.single().messagesJson)
    }

    @Test
    fun 분석_이력은_최신순() = runTest {
        db.analysisDao().insert(Analysis(runAt = Instant.ofEpochMilli(1_000), tasteText = "old", inputBookCount = 3, inputQuoteCount = 10))
        db.analysisDao().insert(Analysis(runAt = Instant.ofEpochMilli(2_000), tasteText = "new", inputBookCount = 4, inputQuoteCount = 12))

        assertEquals(listOf("new", "old"), db.analysisDao().observeAllDesc().first().map { it.tasteText })
    }
}

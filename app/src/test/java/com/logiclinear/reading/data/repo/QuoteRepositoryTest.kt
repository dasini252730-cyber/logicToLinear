package com.logiclinear.reading.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.Quote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class QuoteRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: QuoteRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = QuoteRepository(db.quoteDao(), db.bookDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun 글귀를_추가하면_책의_lastQuoteAt이_글귀_시각으로_바뀐다() = runTest {
        val bookId = db.bookDao().insert(Book(title = "책"))
        assertNull(db.bookDao().getById(bookId)!!.lastQuoteAt)
        val at = Instant.ofEpochMilli(123_456_000)

        repo.add(Quote(bookId = bookId, text = "문장", createdAt = at))

        assertEquals(at, db.bookDao().getById(bookId)!!.lastQuoteAt)
        assertEquals(1, repo.observeCount().first())
    }

    @Test
    fun 최근_글귀를_지우면_lastQuoteAt이_이전_글귀_시각으로_돌아가고_모두_지우면_null() = runTest {
        val bookId = db.bookDao().insert(Book(title = "책"))
        repo.add(Quote(bookId = bookId, text = "먼저", createdAt = Instant.ofEpochMilli(1_000)))
        repo.add(Quote(bookId = bookId, text = "나중", createdAt = Instant.ofEpochMilli(2_000)))
        val latest = repo.observeByBook(bookId).first().first()

        repo.delete(latest)
        assertEquals(Instant.ofEpochMilli(1_000), db.bookDao().getById(bookId)!!.lastQuoteAt)

        assertTrue(repo.restore(latest))
        assertEquals(Instant.ofEpochMilli(2_000), db.bookDao().getById(bookId)!!.lastQuoteAt)

        repo.observeByBook(bookId).first().forEach { repo.delete(it) }
        assertNull(db.bookDao().getById(bookId)!!.lastQuoteAt)
    }

    @Test
    fun 되돌리기는_같은_id로_복구한다() = runTest {
        val bookId = db.bookDao().insert(Book(title = "책"))
        repo.add(Quote(bookId = bookId, text = "문장"))
        val saved = repo.observeByBook(bookId).first().single()
        repo.delete(saved)

        assertTrue(repo.restore(saved))

        assertEquals(saved.id, repo.observeByBook(bookId).first().single().id)
    }

    @Test
    fun 책이_지워진_뒤_되돌리기는_실패를_알리고_예외를_내지_않는다() = runTest {
        val bookId = db.bookDao().insert(Book(title = "책"))
        repo.add(Quote(bookId = bookId, text = "문장"))
        val saved = repo.observeByBook(bookId).first().single()
        repo.delete(saved)
        db.bookDao().delete(db.bookDao().getById(bookId)!!)

        assertFalse(repo.restore(saved))
        assertEquals(0, repo.observeCount().first())
    }
}

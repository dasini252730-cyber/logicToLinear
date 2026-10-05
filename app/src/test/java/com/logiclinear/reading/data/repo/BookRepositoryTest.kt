package com.logiclinear.reading.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BookRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: BookRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = BookRepository(db.bookDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun 상태_변경은_별점과_소감을_유지한다() = runTest {
        val id = repo.add(
            Book(title = "책", status = BookStatus.DONE, rating = 4, oneLiner = "좋았다", finishedAt = LocalDate.of(2026, 1, 1)),
        )

        repo.setStatus(repo.getById(id)!!, BookStatus.READING)

        val after = repo.getById(id)!!
        assertEquals(BookStatus.READING, after.status)
        assertEquals(4, after.rating)
        assertEquals("좋았다", after.oneLiner)
        assertEquals(LocalDate.of(2026, 1, 1), after.finishedAt)
    }

    @Test
    fun 중복_찾기는_공백을_정리하고_빈_저자는_null로_본다() = runTest {
        repo.add(Book(title = "무명", author = null))

        assertNotNull(repo.findDuplicateByTitleAndAuthor(" 무명 ", ""))
        assertNull(repo.findDuplicateByTitleAndAuthor("무명", "김"))
    }

    @Test
    fun 기본_선택_책은_READING이_없으면_null() = runTest {
        repo.add(Book(title = "읽고 싶음", status = BookStatus.WANT))
        assertNull(repo.observeDefaultReadingBook().first())
    }
}

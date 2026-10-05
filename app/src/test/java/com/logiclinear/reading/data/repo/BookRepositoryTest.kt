package com.logiclinear.reading.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.remote.aladin.AladinItem
import com.logiclinear.reading.domain.Review
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun 완독_처리는_DONE으로_바꾸고_별점_소감_완독일을_저장하며_재완독은_덮어쓴다() = runTest {
        val id = repo.add(Book(title = "책"))

        repo.finishBook(repo.getById(id)!!, Review(rating = 3, oneLiner = "첫 완독"), LocalDate.of(2026, 1, 1))
        val first = repo.getById(id)!!
        assertEquals(BookStatus.DONE, first.status)
        assertEquals(3, first.rating)
        assertEquals("첫 완독", first.oneLiner)
        assertEquals(LocalDate.of(2026, 1, 1), first.finishedAt)

        repo.setStatus(first, BookStatus.READING)
        repo.finishBook(repo.getById(id)!!, Review(rating = 5, oneLiner = null), LocalDate.of(2026, 3, 3))
        val second = repo.getById(id)!!
        assertEquals(5, second.rating)
        assertNull(second.oneLiner)
        assertEquals(LocalDate.of(2026, 3, 3), second.finishedAt)
    }

    @Test
    fun 검색_등록은_isbn13_중복을_막고_빈_필드는_null로_저장한다() = runTest {
        val item = AladinItem(title = " 책 ", author = "", publisher = "출판사", isbn13 = "9780000000001", cover = "", categoryName = "", description = "")

        val first = repo.addFromSearch(item, BookStatus.READING)
        val second = repo.addFromSearch(item.copy(title = "다른 제목"), BookStatus.WANT)

        assertTrue(first is AddResult.Added)
        assertEquals(AddResult.Duplicate((first as AddResult.Added).bookId), second)
        val saved = repo.getById(first.bookId)!!
        assertEquals("책", saved.title)
        assertNull(saved.author)
        assertNull(saved.coverUrl)
        assertEquals("출판사", saved.publisher)
    }

    @Test
    fun isbn13이_없는_검색_결과는_중복_검사_없이_등록된다() = runTest {
        val item = AladinItem(title = "무ISBN", isbn13 = "")
        assertTrue(repo.addFromSearch(item, BookStatus.READING) is AddResult.Added)
        assertTrue(repo.addFromSearch(item, BookStatus.READING) is AddResult.Added)
    }

    @Test
    fun 기본_선택_책은_READING이_없으면_null() = runTest {
        repo.add(Book(title = "읽고 싶음", status = BookStatus.WANT))
        assertNull(repo.observeDefaultReadingBook().first())
    }
}

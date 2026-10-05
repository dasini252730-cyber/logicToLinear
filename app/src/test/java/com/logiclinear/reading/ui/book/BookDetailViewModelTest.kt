package com.logiclinear.reading.ui.book

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.MainDispatcherRule
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.Quote
import com.logiclinear.reading.data.repo.BookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BookDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: BookRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Room suspend 호출이 테스트 디스패처 위에서 돌아야 advanceUntilIdle()로 결정적으로 진행된다.
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.dispatcher)
            .build()
        repo = BookRepository(db.bookDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun 읽기_시작은_WANT를_READING으로_바꾼다() = runTest(mainDispatcherRule.dispatcher) {
        val id = repo.add(Book(title = "책", status = BookStatus.WANT))
        val vm = BookDetailViewModel(repo, id)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.startReading()
        advanceUntilIdle()

        assertEquals(BookStatus.READING, repo.getById(id)!!.status)
        collector.cancel()
    }

    @Test
    fun 다시_읽기는_별점과_소감을_유지한다() = runTest(mainDispatcherRule.dispatcher) {
        val id = repo.add(Book(title = "책", status = BookStatus.DONE, rating = 5, oneLiner = "최고", finishedAt = LocalDate.of(2026, 2, 2)))
        val vm = BookDetailViewModel(repo, id)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.restartReading()
        advanceUntilIdle()

        val after = repo.getById(id)!!
        assertEquals(BookStatus.READING, after.status)
        assertEquals(5, after.rating)
        assertEquals("최고", after.oneLiner)
        assertEquals(LocalDate.of(2026, 2, 2), after.finishedAt)
        collector.cancel()
    }

    @Test
    fun 삭제는_확인_후에만_실행되고_글귀도_함께_지운다() = runTest(mainDispatcherRule.dispatcher) {
        val id = repo.add(Book(title = "책"))
        db.quoteDao().insert(Quote(bookId = id, text = "글귀"))
        val vm = BookDetailViewModel(repo, id)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.requestDelete()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.confirmDelete)
        assertFalse(vm.uiState.value.deleted)

        vm.cancelDelete()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.confirmDelete)
        assertEquals("책", repo.getById(id)!!.title)

        vm.requestDelete()
        vm.confirmDelete()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.deleted)
        assertNull(repo.getById(id))
        assertEquals(0, db.quoteDao().countAll().first())
        collector.cancel()
    }

    @Test
    fun 없는_책은_loaded_true_book_null() = runTest(mainDispatcherRule.dispatcher) {
        val vm = BookDetailViewModel(repo, 999)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.loaded)
        assertNull(vm.uiState.value.book)
        collector.cancel()
    }
}

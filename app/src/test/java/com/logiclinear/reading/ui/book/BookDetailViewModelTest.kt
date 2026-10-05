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
import com.logiclinear.reading.data.repo.QuoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private lateinit var quotes: QuoteRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Room suspend 호출이 테스트 디스패처 위에서 돌아야 advanceUntilIdle()로 결정적으로 진행된다.
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.dispatcher)
            .build()
        repo = BookRepository(db.bookDao())
        quotes = QuoteRepository(db.quoteDao(), db.bookDao())
    }

    @After
    fun tearDown() = db.close()

    private fun viewModel(id: Long) = BookDetailViewModel(repo, quotes, id)

    @Test
    fun 완독_처리는_입력을_정리해_저장하고_토론_제안을_띄운다() = runTest(mainDispatcherRule.dispatcher) {
        val id = repo.add(Book(title = "책"))
        val vm = viewModel(id)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openFinish()
        vm.setRating(4)
        vm.setOneLiner("  " + "가".repeat(120))
        vm.setFinishedAt(LocalDate.of(2026, 5, 5))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.finishOpen)
        assertEquals(100, vm.uiState.value.finishDraft.oneLiner.length)

        vm.confirmFinish()
        advanceUntilIdle()

        val saved = repo.getById(id)!!
        assertEquals(BookStatus.DONE, saved.status)
        assertEquals(4, saved.rating)
        assertEquals(98, saved.oneLiner!!.length)
        assertEquals(LocalDate.of(2026, 5, 5), saved.finishedAt)
        assertFalse(vm.uiState.value.finishOpen)
        assertTrue(vm.uiState.value.proposalOpen)

        vm.dismissProposal()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.proposalOpen)
        collector.cancel()
    }

    @Test
    fun 같은_별을_다시_탭하면_별점이_해제되고_재완독은_기존_값을_초안에_채운다() = runTest(mainDispatcherRule.dispatcher) {
        val id = repo.add(Book(title = "책", status = BookStatus.DONE, rating = 2, oneLiner = "전에"))
        val vm = viewModel(id)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openFinish()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.finishDraft.rating)
        assertEquals("전에", vm.uiState.value.finishDraft.oneLiner)

        vm.setRating(2)
        advanceUntilIdle()
        assertNull(vm.uiState.value.finishDraft.rating)
        collector.cancel()
    }

    @Test
    fun 글귀_목록이_상태에_실린다() = runTest(mainDispatcherRule.dispatcher) {
        val id = repo.add(Book(title = "책"))
        quotes.add(Quote(bookId = id, text = "첫째", page = 1))
        quotes.add(Quote(bookId = id, text = "둘째"))
        val vm = viewModel(id)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.quotes.size)
        collector.cancel()
    }

    @Test
    fun 글귀_삭제는_바로_지우고_되돌리기로_같은_id로_복구한다() = runTest(mainDispatcherRule.dispatcher) {
        val id = repo.add(Book(title = "책"))
        quotes.add(Quote(bookId = id, text = "지울 글귀"))
        val vm = viewModel(id)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val target = vm.uiState.value.quotes.single()

        vm.deleteQuote(target)
        advanceTimeBy(1_000) // 5초 창 안
        runCurrent()
        assertTrue(vm.uiState.value.quotes.isEmpty())
        assertEquals(target, vm.uiState.value.undoCandidate)

        vm.undoDelete()
        advanceUntilIdle()
        assertEquals(target.id, vm.uiState.value.quotes.single().id)
        assertNull(vm.uiState.value.undoCandidate)
        collector.cancel()
    }

    @Test
    fun 되돌리기_시간이_지나면_후보만_비우고_DB에는_남지_않는다() = runTest(mainDispatcherRule.dispatcher) {
        val id = repo.add(Book(title = "책"))
        quotes.add(Quote(bookId = id, text = "지울 글귀"))
        val vm = viewModel(id)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.deleteQuote(vm.uiState.value.quotes.single())
        advanceTimeBy(BookDetailViewModel.UNDO_WINDOW_MS - 1)
        runCurrent()
        assertNotNull(vm.uiState.value.undoCandidate)

        advanceTimeBy(2) // 5초 경과: ViewModel이 후보를 비운다
        runCurrent()
        assertNull(vm.uiState.value.undoCandidate)
        assertEquals(0, db.quoteDao().countAll().first())
        collector.cancel()
    }

    @Test
    fun 읽기_시작은_WANT를_READING으로_바꾼다() = runTest(mainDispatcherRule.dispatcher) {
        val id = repo.add(Book(title = "책", status = BookStatus.WANT))
        val vm = viewModel(id)
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
        val vm = viewModel(id)
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
        val vm = viewModel(id)
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
        val vm = viewModel(999)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.loaded)
        assertNull(vm.uiState.value.book)
        collector.cancel()
    }
}

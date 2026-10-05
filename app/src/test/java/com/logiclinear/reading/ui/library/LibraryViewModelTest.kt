package com.logiclinear.reading.ui.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.MainDispatcherRule
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.repo.BookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var vm: LibraryViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.dispatcher)
            .build()
        vm = LibraryViewModel(BookRepository(db.bookDao()))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun 탭을_바꾸면_해당_상태_책만_보인다() = runTest(mainDispatcherRule.dispatcher) {
        db.bookDao().insert(Book(title = "읽는 중", status = BookStatus.READING))
        db.bookDao().insert(Book(title = "읽고 싶음", status = BookStatus.WANT))
        db.bookDao().insert(Book(title = "다 읽음", status = BookStatus.DONE))
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf("읽는 중"), vm.uiState.value.books.map { it.title })

        vm.selectTab(BookStatus.DONE)
        advanceUntilIdle()

        assertEquals(BookStatus.DONE, vm.uiState.value.tab)
        assertEquals(listOf("다 읽음"), vm.uiState.value.books.map { it.title })
        assertTrue(vm.uiState.value.loaded)
        collector.cancel()
    }

    @Test
    fun 빈_탭은_loaded_true에_빈_목록() = runTest(mainDispatcherRule.dispatcher) {
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectTab(BookStatus.WANT)
        advanceUntilIdle()

        assertEquals(BookStatus.WANT, vm.uiState.value.tab)
        assertTrue(vm.uiState.value.loaded)
        assertTrue(vm.uiState.value.books.isEmpty())
        collector.cancel()
    }
}

package com.logiclinear.reading.ui.book

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.MainDispatcherRule
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.repo.BookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BookFormViewModelTest {
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
    fun 제목이_비면_저장되지_않고_안내가_뜬다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = BookFormViewModel(repo)
        vm.onTitleChange("   ")

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.titleRequired)
        assertNull(vm.uiState.value.savedBookId)
        assertTrue(repo.observeByStatus(BookStatus.READING).first().isEmpty())
    }

    @Test
    fun 기본_상태는_READING이고_공백을_정리해_저장한다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = BookFormViewModel(repo)
        vm.onTitleChange("  채식주의자 ")
        vm.onAuthorChange("")

        vm.save()
        advanceUntilIdle()

        val id = vm.uiState.value.savedBookId
        assertNotNull(id)
        val saved = repo.getById(id!!)!!
        assertEquals("채식주의자", saved.title)
        assertNull(saved.author)
        assertEquals(BookStatus.READING, saved.status)
    }

    @Test
    fun 중복이면_첫_저장은_경고만_두번째_저장은_등록한다() = runTest(mainDispatcherRule.dispatcher) {
        repo.add(Book(title = "무명", author = "김"))
        val vm = BookFormViewModel(repo)
        vm.onTitleChange("무명")
        vm.onAuthorChange("김")

        vm.save()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.duplicateWarning)
        assertNull(vm.uiState.value.savedBookId)
        assertEquals(1, repo.observeByStatus(BookStatus.READING).first().size)

        vm.save()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.savedBookId)
        assertEquals(2, repo.observeByStatus(BookStatus.READING).first().size)
    }

    @Test
    fun 경고_후_제목을_고치면_경고가_사라진다() = runTest(mainDispatcherRule.dispatcher) {
        repo.add(Book(title = "무명"))
        val vm = BookFormViewModel(repo)
        vm.onTitleChange("무명")
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.duplicateWarning)

        vm.onTitleChange("무명 2")

        assertFalse(vm.uiState.value.duplicateWarning)
    }

    @Test
    fun 초기_제목은_검색어로_채워진다() {
        val vm = BookFormViewModel(repo, initialTitle = "검색어")
        assertEquals("검색어", vm.uiState.value.title)
    }
}

package com.logiclinear.reading.ui.search

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.MainDispatcherRule
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.remote.aladin.AladinItem
import com.logiclinear.reading.data.remote.aladin.AladinResult
import com.logiclinear.reading.data.remote.aladin.AladinSearch
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BookSearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: BookRepository

    private val item = AladinItem(
        title = "채식주의자", author = "한강 (지은이)", publisher = "창비", isbn13 = "9788936433598",
        cover = "https://img/cover.jpg", categoryName = "국내도서>소설", description = "소개", link = "https://aladin/1",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.dispatcher)
            .build()
        repo = BookRepository(db.bookDao())
    }

    @After
    fun tearDown() = db.close()

    private fun vm(result: AladinResult, fake: FakeAladin = FakeAladin(result)) =
        BookSearchViewModel(BookRepository(db.bookDao(), fake))

    private class FakeAladin(private val result: AladinResult) : AladinSearch {
        var calls = 0
        override suspend fun searchByTitle(title: String): AladinResult { calls++; return result }
    }

    @Test
    fun 검색_결과를_탭하면_필드_7개와_선택_상태로_등록된다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm(AladinResult.Found(listOf(item)))
        vm.onQueryChange("채식")
        vm.onStatusChange(BookStatus.WANT)
        vm.search()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.results.size)

        vm.register(item)
        advanceUntilIdle()

        val id = vm.uiState.value.registeredBookId
        assertNotNull(id)
        val saved = repo.getById(id!!)!!
        assertEquals("채식주의자", saved.title)
        assertEquals("한강 (지은이)", saved.author)
        assertEquals("창비", saved.publisher)
        assertEquals("9788936433598", saved.isbn13)
        assertEquals("https://img/cover.jpg", saved.coverUrl)
        assertEquals("국내도서>소설", saved.category)
        assertEquals("소개", saved.description)
        assertEquals(BookStatus.WANT, saved.status)
    }

    @Test
    fun 같은_isbn13이_있으면_등록하지_않고_중복_표시() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm(AladinResult.Found(listOf(item)))
        vm.register(item)
        advanceUntilIdle()
        val first = vm.uiState.value.registeredBookId

        val second = vm(AladinResult.Found(listOf(item)))
        second.register(item.copy(title = "채식주의자 (개정판)"))
        advanceUntilIdle()

        assertTrue(second.uiState.value.duplicate)
        assertNull(second.uiState.value.registeredBookId)
        assertEquals(1, repo.observeByStatus(BookStatus.READING).first().size)
        assertNotNull(first)
    }

    @Test
    fun 결과_없음_오류_오프라인은_검색어를_담아_직접_입력으로_전환() = runTest(mainDispatcherRule.dispatcher) {
        for (result in listOf(AladinResult.Empty, AladinResult.ApiError(900, "한도"), AladinResult.Network(IOException("offline")))) {
            val vm = vm(result)
            vm.onQueryChange(" 어떤 책 ")
            vm.search()
            advanceUntilIdle()

            assertEquals("어떤 책", vm.uiState.value.fallbackQuery)
            assertFalse(vm.uiState.value.searching)
            vm.consumeFallback()
            assertNull(vm.uiState.value.fallbackQuery)
        }
    }

    @Test
    fun 키가_없으면_안내_상태가_되고_전환하지_않는다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm(AladinResult.NoKey)
        vm.onQueryChange("책")
        vm.search()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.needsKey)
        assertNull(vm.uiState.value.fallbackQuery)
    }

    @Test
    fun 빈_검색어는_호출하지_않고_직접_입력_버튼은_빈_제목으로_전환() = runTest(mainDispatcherRule.dispatcher) {
        val fake = FakeAladin(AladinResult.Empty)
        val vm = vm(AladinResult.Empty, fake)
        vm.onQueryChange("   ")
        vm.search()
        advanceUntilIdle()
        assertEquals(0, fake.calls)

        vm.requestManualEntry()
        assertEquals("", vm.uiState.value.fallbackQuery)
    }
}

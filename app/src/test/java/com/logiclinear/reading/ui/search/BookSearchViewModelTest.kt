package com.logiclinear.reading.ui.search

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.MainDispatcherRule
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.remote.books.BookSearchItem
import com.logiclinear.reading.data.remote.books.BookSearchResult
import com.logiclinear.reading.data.remote.books.BookSearch
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

    private val item = BookSearchItem(
        title = "채식주의자", author = "한강 (지은이)", publisher = "창비", isbn13 = "9788936433598",
        cover = "https://img/cover.jpg", category = "국내도서>소설", description = "소개", link = "https://search.daum.net/book/1",
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

    private fun vm(result: BookSearchResult, fake: FakeBookSearch = FakeBookSearch(result), handle: SavedStateHandle = SavedStateHandle()) =
        BookSearchViewModel(BookRepository(db.bookDao(), fake), handle)

    private class FakeBookSearch(private val result: BookSearchResult) : BookSearch {
        var calls = 0
        override suspend fun searchByTitle(title: String): BookSearchResult { calls++; return result }
    }

    @Test
    fun 검색_결과를_탭하면_필드_7개와_선택_상태로_등록된다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm(BookSearchResult.Found(listOf(item)))
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
        val vm = vm(BookSearchResult.Found(listOf(item)))
        vm.register(item)
        advanceUntilIdle()
        val first = vm.uiState.value.registeredBookId

        val second = vm(BookSearchResult.Found(listOf(item)))
        second.register(item.copy(title = "채식주의자 (개정판)"))
        advanceUntilIdle()

        assertTrue(second.uiState.value.duplicate)
        assertNull(second.uiState.value.registeredBookId)
        assertEquals(1, repo.observeByStatus(BookStatus.READING).first().size)
        assertNotNull(first)
    }

    @Test
    fun 결과_없음_오류_오프라인은_검색어를_담아_직접_입력으로_전환() = runTest(mainDispatcherRule.dispatcher) {
        for (result in listOf(BookSearchResult.Empty, BookSearchResult.ApiError("429", "한도"), BookSearchResult.Network(IOException("offline")))) {
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
        val vm = vm(BookSearchResult.NoKey)
        vm.onQueryChange("책")
        vm.search()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.needsKey)
        assertNull(vm.uiState.value.fallbackQuery)
    }

    @Test
    fun 키_오류는_폴백이_아니라_설정_안내로_가고_공급자_오류는_폴백으로_간다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm(BookSearchResult.InvalidKey)
        vm.onQueryChange("책")
        vm.search()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.keyInvalid)
        assertNull(vm.uiState.value.fallbackQuery)

        val other = vm(BookSearchResult.ApiError("429", "한도 초과"))
        other.onQueryChange("책")
        other.search()
        advanceUntilIdle()
        assertEquals("책", other.uiState.value.fallbackQuery)
    }

    @Test
    fun 빈_검색어는_호출하지_않고_직접_입력_버튼은_빈_제목으로_전환() = runTest(mainDispatcherRule.dispatcher) {
        val fake = FakeBookSearch(BookSearchResult.Empty)
        val vm = vm(BookSearchResult.Empty, fake)
        vm.onQueryChange("   ")
        vm.search()
        advanceUntilIdle()
        assertEquals(0, fake.calls)

        vm.requestManualEntry()
        assertEquals("", vm.uiState.value.fallbackQuery)
    }

    @Test
    fun 설정에서_돌아오면_키_안내만_지우고_검색어는_남기며_재검색하지_않는다() = runTest(mainDispatcherRule.dispatcher) {
        val fake = FakeBookSearch(BookSearchResult.NoKey)
        val vm = vm(BookSearchResult.NoKey, fake)
        vm.onQueryChange("채식")
        vm.search()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.needsKey)

        vm.clearKeyNotice()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.needsKey)
        assertEquals("채식", vm.uiState.value.query)
        assertEquals(1, fake.calls)
    }

    @Test
    fun 설정에서_돌아오면_키_오류_안내도_지운다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm(BookSearchResult.InvalidKey)
        vm.onQueryChange("채식")
        vm.search()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.keyInvalid)

        vm.clearKeyNotice()
        assertFalse(vm.uiState.value.keyInvalid)
    }

    @Test
    fun 키_안내_상태가_아니면_clearKeyNotice는_상태를_바꾸지_않는다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm(BookSearchResult.Found(listOf(item)))
        vm.onQueryChange("채식")
        vm.search()
        advanceUntilIdle()
        val before = vm.uiState.value

        vm.clearKeyNotice()
        assertEquals(before, vm.uiState.value)
        assertEquals(1, vm.uiState.value.results.size)
    }

    @Test
    fun 프로세스_재시작을_흉내내면_검색어와_선택_상태가_SavedStateHandle에서_복원된다() = runTest(mainDispatcherRule.dispatcher) {
        val handle = SavedStateHandle()
        val first = vm(BookSearchResult.Found(listOf(item)), handle = handle)
        first.onQueryChange("채식")
        first.onStatusChange(BookStatus.WANT)
        first.search()
        advanceUntilIdle()
        assertEquals(1, first.uiState.value.results.size)

        // 같은 handle로 새 ViewModel: 검색어·상태는 남고 결과·안내 플래그는 보관하지 않아 초기화된다
        val fake = FakeBookSearch(BookSearchResult.Found(listOf(item)))
        val restored = vm(BookSearchResult.Found(listOf(item)), fake, handle)
        assertEquals("채식", restored.uiState.value.query)
        assertEquals(BookStatus.WANT, restored.uiState.value.status)
        assertTrue(restored.uiState.value.results.isEmpty())
        assertFalse(restored.uiState.value.needsKey)

        restored.search() // 복원된 검색어로 바로 검색된다
        advanceUntilIdle()
        assertEquals(1, fake.calls)
        assertEquals(1, restored.uiState.value.results.size)
    }

    @Test
    fun 저장된_값이_없거나_깨졌거나_비어_있으면_기본값으로_시작한다() = runTest(mainDispatcherRule.dispatcher) {
        val empty = vm(BookSearchResult.Empty)
        assertEquals("", empty.uiState.value.query)
        assertEquals(BookStatus.READING, empty.uiState.value.status)

        val broken = vm(BookSearchResult.Empty, handle = SavedStateHandle(mapOf("searchStatus" to "NOPE")))
        assertEquals(BookStatus.READING, broken.uiState.value.status)

        val handle = SavedStateHandle()
        vm(BookSearchResult.Empty, handle = handle).onQueryChange("")
        assertEquals("", vm(BookSearchResult.Empty, handle = handle).uiState.value.query)
    }

    @Test
    fun 저장되는_검색어는_상한까지만_잘리고_화면_값은_그대로다() = runTest(mainDispatcherRule.dispatcher) {
        val max = BookSearchViewModel.MAX_SAVED_QUERY
        val handle = SavedStateHandle()
        val first = vm(BookSearchResult.Empty, handle = handle)

        first.onQueryChange("가".repeat(max))
        assertEquals(max, vm(BookSearchResult.Empty, handle = handle).uiState.value.query.length)

        first.onQueryChange("가".repeat(max + 1))
        assertEquals(max + 1, first.uiState.value.query.length)
        assertEquals(max, vm(BookSearchResult.Empty, handle = handle).uiState.value.query.length)
    }
}

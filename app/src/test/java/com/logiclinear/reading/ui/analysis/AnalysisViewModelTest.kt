package com.logiclinear.reading.ui.analysis

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.MainDispatcherRule
import com.logiclinear.reading.data.ai.RecommendationEnricher
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.Quote
import com.logiclinear.reading.data.remote.aladin.AladinResult
import com.logiclinear.reading.data.remote.aladin.AladinSearch
import com.logiclinear.reading.data.remote.anthropic.AiChat
import com.logiclinear.reading.data.remote.anthropic.AiRequest
import com.logiclinear.reading.data.remote.anthropic.AiResult
import com.logiclinear.reading.data.repo.AnalysisRepository
import com.logiclinear.reading.data.repo.BookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
class AnalysisViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private var aiAnswer: AiResult = AiResult.Network(IOException("offline"))
    private var aiCalls = 0
    private val ai = object : AiChat {
        override suspend fun complete(request: AiRequest): AiResult { aiCalls++; return aiAnswer }
    }
    private val noAladin = object : AladinSearch {
        override suspend fun searchByTitle(title: String): AladinResult = AladinResult.Empty
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.dispatcher)
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun vm() = AnalysisViewModel(AnalysisRepository(db, ai, RecommendationEnricher(noAladin)) { "p" }, BookRepository(db.bookDao()))

    private suspend fun seedReady(books: Int = 3, quotes: Int = 10) {
        var first = 0L
        repeat(books) { val id = db.bookDao().insert(Book(title = "책 $it", author = "저자", status = BookStatus.DONE)); if (it == 0) first = id }
        repeat(quotes) { db.quoteDao().insert(Quote(bookId = first, text = "글귀 $it")) }
    }

    @Test
    fun 조건_미달이면_남은_수가_정확하고_실행이_막힌다() = runTest(mainDispatcherRule.dispatcher) {
        seedReady(books = 1, quotes = 4)
        val vm = vm()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.readiness.missingBooks)
        assertEquals(6, vm.uiState.value.readiness.missingQuotes)
        assertFalse(vm.uiState.value.canRun)
        vm.run()
        advanceUntilIdle()
        assertEquals(0, aiCalls)
        collector.cancel()
    }

    @Test
    fun 실행하면_Analysis가_생기고_최신으로_올라오며_이전_것은_기록으로_내려간다() = runTest(mainDispatcherRule.dispatcher) {
        seedReady()
        aiAnswer = AiResult.Success("""{"taste":"첫 분석","recommendations":[{"title":"책 0","author":"저자","reason":"r"},{"title":"새 책","author":"누구","reason":"r2"}]}""", null, null)
        val vm = vm()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canRun)

        vm.run()
        advanceUntilIdle()
        val latest = vm.uiState.value.latest!!
        assertEquals("첫 분석", latest.analysis.tasteText)
        assertTrue(latest.cards[0].inLibrary) // 이미 서재에 있는 "책 0"은 담기 버튼 숨김
        assertFalse(latest.cards[1].inLibrary)
        assertEquals(1, aiCalls)

        aiAnswer = AiResult.Success("""{"taste":"둘째 분석"}""", null, null)
        vm.run()
        advanceUntilIdle()
        assertEquals("둘째 분석", vm.uiState.value.latest!!.analysis.tasteText)
        assertEquals(listOf("첫 분석"), vm.uiState.value.history.map { it.analysis.tasteText })
        assertTrue(vm.uiState.value.latest!!.cards.isEmpty())

        vm.toggleHistory(vm.uiState.value.history.single().analysis.id)
        advanceUntilIdle()
        assertEquals(vm.uiState.value.history.single().analysis.id, vm.uiState.value.expandedId)
        collector.cancel()
    }

    @Test
    fun 실패하면_오류가_남고_재시도로_다시_호출되며_로딩_중에는_중복_호출이_없다() = runTest(mainDispatcherRule.dispatcher) {
        seedReady()
        val vm = vm()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.run()
        vm.run() // 로딩 중 두 번째 탭
        advanceUntilIdle()
        assertEquals(1, aiCalls)
        assertNotNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.latest)

        aiAnswer = AiResult.Success("""{"taste":"복구"}""", null, null)
        vm.run()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertEquals("복구", vm.uiState.value.latest!!.analysis.tasteText)
        collector.cancel()
    }

    @Test
    fun 담기는_WANT로_넣고_같은_책을_두_번_담을_수_없다() = runTest(mainDispatcherRule.dispatcher) {
        seedReady()
        aiAnswer = AiResult.Success("""{"taste":"t","recommendations":[{"title":"새 책","author":"누구","reason":"r"}]}""", null, null)
        val vm = vm()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.run()
        advanceUntilIdle()
        val rec = vm.uiState.value.latest!!.cards.single().rec

        vm.addToWant(rec)
        advanceUntilIdle()
        assertEquals(AnalysisMessage.ADDED, vm.uiState.value.message)
        assertEquals(1, db.bookDao().observeByStatus(BookStatus.WANT).first().size)
        assertTrue(vm.uiState.value.latest!!.cards.single().inLibrary)

        vm.consumeMessage()
        vm.addToWant(rec)
        advanceUntilIdle()
        assertEquals(AnalysisMessage.ALREADY_IN_LIBRARY, vm.uiState.value.message)
        assertEquals(1, db.bookDao().observeByStatus(BookStatus.WANT).first().size)
        collector.cancel()
    }
}

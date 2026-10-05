package com.logiclinear.reading.ui.select

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.MainDispatcherRule
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.repo.QuoteRepository
import com.logiclinear.reading.ocr.CaptureStore
import com.logiclinear.reading.ocr.OcrLine
import com.logiclinear.reading.ocr.OcrResult
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
class SelectViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: QuoteRepository
    private val store = CaptureStore()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.dispatcher)
            .build()
        repo = QuoteRepository(db.quoteDao(), db.bookDao())
    }

    @After
    fun tearDown() = db.close()

    private fun line(text: String, top: Float) = OcrLine(text, 0, (top * 1000).toInt(), 500, (top * 1000).toInt() + 30, top, top + 0.03f)

    private fun result(vararg lines: OcrLine) = OcrResult(lines.toList(), 800, 1000, 50)

    @Test
    fun 후보_1개면_페이지가_자동으로_채워진다() {
        store.put(1, result(line("본문", 0.3f), line("57", 0.95f)))
        val vm = SelectViewModel(repo, store)

        assertEquals("57", vm.uiState.value.pageInput)
        assertTrue(vm.uiState.value.pageAutoFilled)
    }

    @Test
    fun 후보_2개면_빈_칸이고_칩으로_고른다() {
        store.put(1, result(line("12", 0.02f), line("본문", 0.3f), line("13", 0.96f)))
        val vm = SelectViewModel(repo, store)

        assertEquals("", vm.uiState.value.pageInput)
        assertEquals(listOf(12, 13), vm.uiState.value.pageCandidates)
        vm.onPageChange("13")
        assertEquals(13, vm.uiState.value.page)
    }

    @Test
    fun 직접_입력이_후보보다_우선하고_숫자만_4자리까지_받는다() {
        store.put(1, result(line("57", 0.95f)))
        val vm = SelectViewModel(repo, store)

        vm.onPageChange("p.12345")

        assertEquals("1234", vm.uiState.value.pageInput)
        assertFalse(vm.uiState.value.pageAutoFilled)
    }

    @Test
    fun 줄을_선택하면_합쳐진_글귀가_편집_칸에_들어간다() {
        store.put(1, result(line("나는 그날 처음으로 바", 0.3f), line("다를 보았다.", 0.35f), line("딴 줄", 0.5f)))
        val vm = SelectViewModel(repo, store)

        vm.toggleLine(1)
        vm.toggleLine(0)
        assertEquals("나는 그날 처음으로 바다를 보았다.", vm.uiState.value.text)

        vm.toggleLine(1)
        assertEquals("나는 그날 처음으로 바", vm.uiState.value.text)
    }

    @Test
    fun 저장하면_Quote와_lastQuoteAt이_기록되고_보관소가_비워진다() = runTest(mainDispatcherRule.dispatcher) {
        val bookId = db.bookDao().insert(Book(title = "책"))
        store.put(bookId, result(line("문장", 0.3f), line("57", 0.95f)))
        val vm = SelectViewModel(repo, store)
        vm.toggleLine(0)
        vm.onTextChange("문장 (오타 수정)")

        vm.save()
        advanceUntilIdle()

        val quote = db.quoteDao().observeByBook(bookId).first().single()
        assertEquals("문장 (오타 수정)", quote.text)
        assertEquals(57, quote.page)
        assertEquals(quote.createdAt, db.bookDao().getById(bookId)!!.lastQuoteAt)
        assertEquals(57, vm.uiState.value.saved?.page)
        assertNull(store.current.value)
    }

    @Test
    fun 페이지를_비우면_page_null로_저장된다() = runTest(mainDispatcherRule.dispatcher) {
        val bookId = db.bookDao().insert(Book(title = "책"))
        store.put(bookId, result(line("문장", 0.3f)))
        val vm = SelectViewModel(repo, store)
        vm.toggleLine(0)

        vm.save()
        advanceUntilIdle()

        assertNull(db.quoteDao().observeByBook(bookId).first().single().page)
        assertNotNull(vm.uiState.value.saved)
        assertNull(vm.uiState.value.saved?.page)
    }

    @Test
    fun 글귀가_비면_저장할_수_없다() = runTest(mainDispatcherRule.dispatcher) {
        store.put(1, result(line("문장", 0.3f)))
        val vm = SelectViewModel(repo, store)

        assertFalse(vm.uiState.value.canSave)
        vm.save()
        advanceUntilIdle()
        assertNull(vm.uiState.value.saved)
    }

    @Test
    fun 보관된_촬영이_없으면_missingCapture() {
        val vm = SelectViewModel(repo, CaptureStore())
        assertTrue(vm.uiState.value.missingCapture)
    }
}

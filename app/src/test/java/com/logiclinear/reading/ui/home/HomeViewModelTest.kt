package com.logiclinear.reading.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.MainDispatcherRule
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.repo.BookRepository
import android.graphics.Bitmap
import com.logiclinear.reading.ocr.CaptureStore
import com.logiclinear.reading.ocr.OcrEngine
import com.logiclinear.reading.ocr.OcrResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Instant

/** 책 선택·READING 없음 분기만 검증한다. 촬영·OCR은 기기가 필요해 T-212에서 본다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var vm: HomeViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.dispatcher)
            .build()
        vm = HomeViewModel(BookRepository(db.bookDao()), NoOcr, CaptureStore())
    }

    /** 이 테스트는 촬영하지 않으므로 OCR은 호출되지 않는다. */
    private object NoOcr : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): OcrResult = error("호출되지 않아야 한다")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun READING_책이_없으면_셔터_불가_안내_상태() = runTest(mainDispatcherRule.dispatcher) {
        db.bookDao().insert(Book(title = "읽고 싶음", status = BookStatus.WANT))
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.noReadingBook)
        assertFalse(vm.uiState.value.canCapture)
        assertNull(vm.uiState.value.selectedBook)
        collector.cancel()
    }

    @Test
    fun 기본_선택은_lastQuoteAt이_최근인_READING_책이고_선택기로_바꿀_수_있다() = runTest(mainDispatcherRule.dispatcher) {
        val recent = db.bookDao().insert(Book(title = "최근", lastQuoteAt = Instant.ofEpochMilli(5_000)))
        val other = db.bookDao().insert(Book(title = "다른 책"))
        db.bookDao().insert(Book(title = "다 읽음", status = BookStatus.DONE))
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(recent, vm.uiState.value.selectedBook?.id)
        assertEquals(listOf(recent, other), vm.uiState.value.readingBooks.map { it.id }.sorted())
        assertTrue(vm.uiState.value.canCapture)

        vm.openPicker()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.pickerOpen)

        vm.chooseBook(other)
        advanceUntilIdle()

        assertEquals(other, vm.uiState.value.selectedBook?.id)
        assertFalse(vm.uiState.value.pickerOpen)
        collector.cancel()
    }

    @Test
    fun 고른_책이_READING에서_빠지면_기본_선택으로_돌아간다() = runTest(mainDispatcherRule.dispatcher) {
        val a = db.bookDao().insert(Book(title = "A"))
        val b = db.bookDao().insert(Book(title = "B", lastQuoteAt = Instant.ofEpochMilli(1)))
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.chooseBook(a)
        advanceUntilIdle()
        assertEquals(a, vm.uiState.value.selectedBook?.id)

        db.bookDao().update(db.bookDao().getById(a)!!.copy(status = BookStatus.DONE))
        advanceUntilIdle()

        assertEquals(b, vm.uiState.value.selectedBook?.id)
        collector.cancel()
    }
}

package com.logiclinear.reading.ui.capture

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.MainDispatcherRule
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.repo.BookRepository
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

/** 책 고정·READING 여부 분기만 검증한다. 촬영·OCR은 기기가 필요해 T-212에서 본다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CaptureViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.dispatcher)
            .build()
    }

    /** 이 테스트는 촬영하지 않으므로 OCR은 호출되지 않는다. */
    private object NoOcr : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): OcrResult = error("호출되지 않아야 한다")
    }

    @After
    fun tearDown() = db.close()

    private fun vm(bookId: Long) = CaptureViewModel(bookId, BookRepository(db.bookDao()), NoOcr, CaptureStore())

    @Test
    fun READING_책이면_그_책이_고정되고_셔터가_켜진다() = runTest(mainDispatcherRule.dispatcher) {
        val a = db.bookDao().insert(Book(title = "A"))
        db.bookDao().insert(Book(title = "B")) // 다른 READING 책이 있어도 고정 책만 본다
        val vm = vm(a)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(a, vm.uiState.value.book?.id)
        assertTrue(vm.uiState.value.canCapture)
        assertFalse(vm.uiState.value.bookMissing)
        collector.cancel()
    }

    @Test
    fun 고정한_책이_READING에서_빠지면_셔터가_꺼지고_안내_상태가_된다() = runTest(mainDispatcherRule.dispatcher) {
        val a = db.bookDao().insert(Book(title = "A"))
        val vm = vm(a)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(a, vm.uiState.value.book?.id)

        db.bookDao().update(db.bookDao().getById(a)!!.copy(status = BookStatus.DONE))
        advanceUntilIdle()

        assertNull(vm.uiState.value.book) // 다른 책으로 대신하지 않는다
        assertFalse(vm.uiState.value.canCapture)
        assertTrue(vm.uiState.value.bookMissing)
        collector.cancel()
    }

    @Test
    fun 없는_책_id면_로드_뒤_안내_상태이고_셔터를_눌러도_아무_일도_없다() = runTest(mainDispatcherRule.dispatcher) {
        db.bookDao().insert(Book(title = "읽고 싶음", status = BookStatus.WANT))
        val vm = vm(999)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.bookLoaded)
        assertTrue(vm.uiState.value.bookMissing)
        assertFalse(vm.uiState.value.canCapture)

        // 셔터 enabled가 틀려도 READING 아닌 책에 글귀가 저장되지 않는 마지막 방어선: 카메라·OCR을 건드리지 않고 돌아온다.
        vm.capture(ApplicationProvider.getApplicationContext())
        advanceUntilIdle()
        assertFalse(vm.uiState.value.capturing)
        assertFalse(vm.uiState.value.captureFailed)
        collector.cancel()
    }
}

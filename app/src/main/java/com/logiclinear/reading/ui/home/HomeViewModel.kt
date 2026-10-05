package com.logiclinear.reading.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.logiclinear.reading.appContainer
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.repo.BookRepository
import com.logiclinear.reading.ocr.CaptureStore
import com.logiclinear.reading.ocr.OcrEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 카메라 홈 상태. [selectedBook]은 사용자가 고른 책, 없으면 lastQuoteAt이 최근인 READING 책.
 * READING 책이 없으면 [selectedBook]이 null이고 셔터는 비활성(요구사항 "예외 처리").
 */
data class HomeUiState(
    val selectedBook: Book? = null,
    /** 책 선택기에 보이는 READING 책들(요구사항 "책 상태 전이": READING만). */
    val readingBooks: List<Book> = emptyList(),
    val booksLoaded: Boolean = false,
    val pickerOpen: Boolean = false,
    val capturing: Boolean = false,
    /** OCR이 글자를 하나도 못 읽었을 때. */
    val ocrEmpty: Boolean = false,
    /** 카메라 자체 오류. 예외 문구는 로그에만 남기고 사용자에게는 다시 찍기만 안내한다. */
    val captureFailed: Boolean = false,
    /** 인식 완료. 화면은 이 값이 true가 되면 문장 선택으로 간다. */
    val recognized: Boolean = false,
) {
    val canCapture: Boolean get() = selectedBook != null && !capturing
    val noReadingBook: Boolean get() = booksLoaded && readingBooks.isEmpty()
}

private data class LocalState(
    val chosenBookId: Long? = null,
    val pickerOpen: Boolean = false,
    val capturing: Boolean = false,
    val ocrEmpty: Boolean = false,
    val captureFailed: Boolean = false,
    val recognized: Boolean = false,
)

class HomeViewModel(
    private val bookRepository: BookRepository,
    /** AppContainer가 소유한다. 공유 인스턴스라 이 ViewModel이 close하지 않는다. */
    private val ocrRecognizer: OcrEngine,
    private val captureStore: CaptureStore,
    val camera: CameraController = CameraController(),
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<HomeUiState> = combine(
        bookRepository.observeDefaultReadingBook(),
        bookRepository.observeByStatus(BookStatus.READING),
        local,
    ) { default, reading, state ->
        HomeUiState(
            selectedBook = reading.firstOrNull { it.id == state.chosenBookId } ?: default,
            readingBooks = reading,
            booksLoaded = true,
            pickerOpen = state.pickerOpen,
            capturing = state.capturing,
            ocrEmpty = state.ocrEmpty,
            captureFailed = state.captureFailed,
            recognized = state.recognized,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    suspend fun bindCamera(context: Context, lifecycleOwner: LifecycleOwner) = camera.bind(context, lifecycleOwner)

    fun openPicker() = local.update { it.copy(pickerOpen = true) }

    fun closePicker() = local.update { it.copy(pickerOpen = false) }

    /** 선택기에서 책을 고른다. 글귀를 저장하면 그 책의 lastQuoteAt이 최신이 되어 다음 실행의 기본값이 된다. */
    fun chooseBook(bookId: Long) = local.update { it.copy(chosenBookId = bookId, pickerOpen = false) }

    /** 셔터. 촬영 → OCR → 결과를 [CaptureStore]에 넣고 비트맵 폐기. 요구사항 "흐름 1" 2~3단계. */
    fun capture(context: Context) {
        val book = uiState.value.selectedBook ?: return
        if (local.value.capturing) return
        local.update { it.copy(capturing = true, ocrEmpty = false, captureFailed = false) }
        viewModelScope.launch {
            try {
                val image = camera.capture(context)
                // ML Kit은 비트맵을 복사하지 않는다. 인식 도중 화면을 떠나 스코프가 취소돼도 인식이 끝난 뒤에 폐기한다.
                val result = withContext(NonCancellable) {
                    try {
                        ocrRecognizer.recognize(image.bitmap, image.rotationDegrees)
                    } finally {
                        image.bitmap.recycle()
                    }
                }
                if (result.isEmpty) {
                    local.update { it.copy(capturing = false, ocrEmpty = true) }
                } else {
                    captureStore.put(book.id, result)
                    local.update { it.copy(capturing = false, recognized = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "capture failed", e)
                local.update { it.copy(capturing = false, captureFailed = true) }
            }
        }
    }

    /** 문장 선택 화면으로 이동한 뒤 호출. 다음 촬영을 받을 수 있게 한다. */
    fun consumeRecognized() = local.update { it.copy(recognized = false) }

    fun dismissError() = local.update { it.copy(ocrEmpty = false, captureFailed = false) }

    companion object {
        private const val TAG = "HomeViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(container.bookRepository, container.ocrRecognizer, container.captureStore)
            }
        }
    }
}

package com.logiclinear.reading.ui.home

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.logiclinear.reading.appContainer
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.repo.BookRepository
import com.logiclinear.reading.ocr.CaptureStore
import com.logiclinear.reading.ocr.OcrRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 카메라 홈 상태. 책 선택기(T-209)와 READING 없음 처리(T-210)는 뒤 task에서 채운다. */
data class HomeUiState(
    val selectedBook: Book? = null,
    val booksLoaded: Boolean = false,
    val capturing: Boolean = false,
    /** OCR이 글자를 하나도 못 읽었을 때(요구사항 "예외 처리"). */
    val ocrEmpty: Boolean = false,
    val captureError: String? = null,
    /** 인식 완료. 화면은 이 값이 true가 되면 문장 선택으로 간다(T-204). */
    val recognized: Boolean = false,
)

class HomeViewModel(
    private val bookRepository: BookRepository,
    private val ocrRecognizer: OcrRecognizer,
    private val captureStore: CaptureStore,
    val camera: CameraController = CameraController(),
) : ViewModel() {
    private val local = MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> = combine(bookRepository.observeDefaultReadingBook(), local) { book, state ->
        state.copy(selectedBook = book, booksLoaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    suspend fun bindCamera(context: Context, lifecycleOwner: LifecycleOwner) = camera.bind(context, lifecycleOwner)

    /**
     * 셔터. 촬영 → OCR → 결과를 [CaptureStore]에 넣고 비트맵 폐기. 요구사항 "흐름 1" 2~3단계.
     */
    fun capture(context: Context) {
        val book = uiState.value.selectedBook ?: return
        if (local.value.capturing) return
        local.value = local.value.copy(capturing = true, ocrEmpty = false, captureError = null)
        viewModelScope.launch {
            try {
                val image = camera.capture(context)
                val result = try {
                    ocrRecognizer.recognize(image.bitmap, image.rotationDegrees)
                } finally {
                    image.bitmap.recycle()
                }
                if (result.isEmpty) {
                    local.value = local.value.copy(capturing = false, ocrEmpty = true)
                } else {
                    captureStore.put(book.id, result)
                    local.value = local.value.copy(capturing = false, recognized = true)
                }
            } catch (e: Exception) {
                local.value = local.value.copy(capturing = false, captureError = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** 문장 선택 화면으로 이동한 뒤 호출. 다음 촬영을 받을 수 있게 한다. */
    fun consumeRecognized() {
        local.value = local.value.copy(recognized = false)
    }

    fun dismissOcrEmpty() {
        local.value = local.value.copy(ocrEmpty = false, captureError = null)
    }

    override fun onCleared() {
        ocrRecognizer.close()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(container.bookRepository, container.ocrRecognizer, container.captureStore)
            }
        }
    }
}

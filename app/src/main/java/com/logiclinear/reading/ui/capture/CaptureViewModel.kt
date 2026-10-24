package com.logiclinear.reading.ui.capture

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.logiclinear.reading.appContainer
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.repo.BookRepository
import com.logiclinear.reading.ocr.CaptureStore
import com.logiclinear.reading.ocr.OcrEngine
import com.logiclinear.reading.ui.navigation.CaptureRoute
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
 * 책 전용 카메라 상태(T-806·T-808). [book]은 라우트로 받은 책이 READING일 때만 채워진다.
 * READING이 아니거나 삭제됐으면 null이고 셔터는 비활성, 배너가 "읽는 중인 책을 먼저 등록하세요"로 상세 복귀를 안내한다.
 */
data class CaptureUiState(
    val book: Book? = null,
    val bookLoaded: Boolean = false,
    val capturing: Boolean = false,
    /** OCR이 글자를 하나도 못 읽었을 때. */
    val ocrEmpty: Boolean = false,
    /** 카메라 자체 오류. 예외 문구는 로그에만 남기고 사용자에게는 다시 찍기만 안내한다. */
    val captureFailed: Boolean = false,
    /** 인식 완료. 화면은 이 값이 true가 되면 문장 선택으로 간다. */
    val recognized: Boolean = false,
) {
    val canCapture: Boolean get() = book != null && !capturing
    val bookMissing: Boolean get() = bookLoaded && book == null
}

private data class LocalState(
    val capturing: Boolean = false,
    val ocrEmpty: Boolean = false,
    val captureFailed: Boolean = false,
    val recognized: Boolean = false,
)

class CaptureViewModel(
    private val bookId: Long,
    bookRepository: BookRepository,
    /** AppContainer가 소유한다. 공유 인스턴스라 이 ViewModel이 close하지 않는다. */
    private val ocrRecognizer: OcrEngine,
    private val captureStore: CaptureStore,
    val camera: CameraController = CameraController(),
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<CaptureUiState> = combine(bookRepository.observeById(bookId), local) { book, state ->
        CaptureUiState(
            book = book?.takeIf { it.status == BookStatus.READING },
            bookLoaded = true,
            capturing = state.capturing,
            ocrEmpty = state.ocrEmpty,
            captureFailed = state.captureFailed,
            recognized = state.recognized,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureUiState())

    suspend fun bindCamera(context: Context, lifecycleOwner: LifecycleOwner) = camera.bind(context, lifecycleOwner)

    /** 셔터. 촬영 → OCR → 결과를 [CaptureStore]에 넣고 비트맵 폐기. 요구사항 "흐름 1" 2~3단계. */
    fun capture(context: Context) {
        val book = uiState.value.book ?: return
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
        private const val TAG = "CaptureViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val route = createSavedStateHandle().toRoute<CaptureRoute>()
                CaptureViewModel(route.bookId, container.bookRepository, container.ocrRecognizer, container.captureStore)
            }
        }
    }
}

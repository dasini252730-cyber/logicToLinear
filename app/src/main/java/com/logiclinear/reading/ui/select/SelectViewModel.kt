package com.logiclinear.reading.ui.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.logiclinear.reading.appContainer
import com.logiclinear.reading.data.db.Quote
import com.logiclinear.reading.data.repo.QuoteRepository
import com.logiclinear.reading.domain.extractPageCandidates
import com.logiclinear.reading.domain.joinSelectedLines
import com.logiclinear.reading.ocr.CaptureStore
import com.logiclinear.reading.ocr.OcrLine
import com.logiclinear.reading.ocr.PendingCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 문장 선택 화면 상태(요구사항 "흐름 1" 3~5단계).
 * [text]는 편집 칸 내용, [autoText]는 선택으로 만들어진 원본. 둘이 다르면 사용자가 고친 것이라 선택이 바뀌어도 덮어쓰지 않는다.
 * [pageInput]은 사용자가 보는 문자열. 직접 입력이 후보보다 우선한다. 비워 두면 page 없이 저장(건너뛰기).
 */
data class SelectUiState(
    val lines: List<OcrLine> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val text: String = "",
    val autoText: String = "",
    val pageCandidates: List<Int> = emptyList(),
    val pageInput: String = "",
    /** 후보 1개를 자동으로 채웠을 때 true. 사용자가 고치면 false. */
    val pageAutoFilled: Boolean = false,
    val saving: Boolean = false,
    val saveError: Boolean = false,
    /** 저장 완료. 저장된 페이지(없으면 null)를 담아 화면이 토스트를 띄우고 카메라로 돌아간다. */
    val saved: SavedQuote? = null,
    /** 보관된 촬영이 없으면 true. 화면은 바로 돌아간다. */
    val missingCapture: Boolean = false,
) {
    val canSave: Boolean get() = text.isNotBlank() && !saving
    val userEdited: Boolean get() = text != autoText
    val page: Int? get() = pageInput.trim().toIntOrNull()?.takeIf { it > 0 }
}

data class SavedQuote(val page: Int?)

class SelectViewModel(
    private val quoteRepository: QuoteRepository,
    private val captureStore: CaptureStore,
) : ViewModel() {
    private val capture: PendingCapture? = captureStore.current.value

    private val _uiState = MutableStateFlow(initialState(capture))
    val uiState: StateFlow<SelectUiState> = _uiState

    /** 줄 선택 토글. 사용자가 편집 칸을 고친 뒤라면 고친 글은 두고 원본만 갱신한다. */
    fun toggleLine(index: Int) {
        _uiState.update { state ->
            val selected = if (index in state.selected) state.selected - index else state.selected + index
            val auto = joinSelectedLines(state.lines.map { it.text }, selected)
            state.copy(selected = selected, autoText = auto, text = if (state.userEdited) state.text else auto)
        }
    }

    /** 저장 전 오타 수정용(요구사항 "선택 결과를 저장 전에 편집 가능한 텍스트 칸으로"). */
    fun onTextChange(value: String) = _uiState.update { it.copy(text = value) }

    /** 숫자 키패드 입력 또는 후보 칩 탭. 숫자 이외는 버리고 4자리까지만 받는다. */
    fun onPageChange(value: String) = _uiState.update {
        it.copy(pageInput = value.filter(Char::isDigit).take(4), pageAutoFilled = false)
    }

    fun save() {
        val state = _uiState.value
        val pending = capture ?: return
        if (!state.canSave) return
        _uiState.update { it.copy(saving = true, saveError = false) }
        viewModelScope.launch {
            try {
                quoteRepository.add(Quote(bookId = pending.bookId, text = state.text.trim(), page = state.page))
                captureStore.clear()
                _uiState.update { it.copy(saving = false, saved = SavedQuote(state.page)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, saveError = true) }
            }
        }
    }

    fun dismissSaveError() = _uiState.update { it.copy(saveError = false) }

    /** 저장하지 않고 떠날 때. 보관된 촬영을 비워 다음 촬영과 섞이지 않게 한다. */
    fun discard() = captureStore.clear()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SelectViewModel(container.quoteRepository, container.captureStore)
            }
        }

        internal fun initialState(capture: PendingCapture?): SelectUiState {
            if (capture == null) return SelectUiState(missingCapture = true)
            val candidates = extractPageCandidates(capture.result.lines)
            val auto = candidates.singleOrNull()
            return SelectUiState(
                lines = capture.result.lines,
                pageCandidates = candidates,
                pageInput = auto?.toString().orEmpty(),
                pageAutoFilled = auto != null,
            )
        }
    }
}

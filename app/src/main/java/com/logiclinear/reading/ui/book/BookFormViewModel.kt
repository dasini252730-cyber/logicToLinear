package com.logiclinear.reading.ui.book

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
import com.logiclinear.reading.ui.navigation.BookFormRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 직접 입력 등록 화면 상태. 요구사항 "흐름 2": 제목·저자만 받고, 상태 기본 READING. */
data class BookFormUiState(
    val title: String = "",
    val author: String = "",
    val status: BookStatus = BookStatus.READING,
    /** 제목이 비어 있는데 저장을 눌렀을 때. */
    val titleRequired: Boolean = false,
    /** 같은 제목+저자가 있을 때 경고만 보여 주고, 다시 저장을 누르면 등록한다. */
    val duplicateWarning: Boolean = false,
    val saving: Boolean = false,
    /** 저장된 책 id. 화면은 이 값이 생기면 뒤로 간다. */
    val savedBookId: Long? = null,
) {
    /** 제목이 비어 있어도 버튼은 살려 두고, 누르면 "제목을 입력하세요" 안내를 띄운다. 저장 중에만 막는다. */
    val canSave: Boolean get() = !saving
}

/** 상태 선택지 순서: 요구사항 "서재" 탭 순서와 같다. */
val BOOK_STATUS_CHOICES: List<BookStatus> = listOf(BookStatus.READING, BookStatus.WANT, BookStatus.DONE)

class BookFormViewModel(
    private val bookRepository: BookRepository,
    initialTitle: String? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookFormUiState(title = initialTitle.orEmpty()))
    val uiState: StateFlow<BookFormUiState> = _uiState

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, titleRequired = false, duplicateWarning = false) }

    fun onAuthorChange(value: String) = _uiState.update { it.copy(author = value, duplicateWarning = false) }

    fun onStatusChange(status: BookStatus) = _uiState.update { it.copy(status = status) }

    /**
     * 첫 저장 시 중복이면 경고만 띄우고 멈춘다(요구사항 "책 검색": 직접 입력 책은 제목+저자가 같으면 경고만).
     * 경고가 떠 있는 상태에서 다시 누르면 그대로 등록한다.
     */
    fun save() {
        val state = _uiState.value
        val title = state.title.trim()
        if (title.isEmpty()) {
            _uiState.update { it.copy(titleRequired = true) }
            return
        }
        if (state.saving) return
        val author = state.author.trim().ifEmpty { null }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            val duplicate = !state.duplicateWarning && bookRepository.findDuplicateByTitleAndAuthor(title, author) != null
            if (duplicate) {
                _uiState.update { it.copy(saving = false, duplicateWarning = true) }
                return@launch
            }
            val id = bookRepository.add(Book(title = title, author = author, status = state.status))
            _uiState.update { it.copy(saving = false, savedBookId = id) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<BookFormRoute>()
                BookFormViewModel(appContainer().bookRepository, route.initialTitle)
            }
        }
    }
}

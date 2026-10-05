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
import com.logiclinear.reading.ui.navigation.BookDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 책 상세 상태. [book]이 null이면 아직 로딩 중이거나 삭제됐다. [deleted]가 true면 화면은 서재로 돌아간다.
 */
data class BookDetailUiState(
    val book: Book? = null,
    val loaded: Boolean = false,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
)

class BookDetailViewModel(
    private val bookRepository: BookRepository,
    private val bookId: Long,
) : ViewModel() {
    private val local = MutableStateFlow(BookDetailUiState())

    val uiState: StateFlow<BookDetailUiState> = combine(bookRepository.observeById(bookId), local) { book, state ->
        state.copy(book = book, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailUiState())

    /** WANT → READING ("읽기 시작"). */
    fun startReading() = changeStatus(BookStatus.READING)

    /** DONE → READING ("다시 읽기"). 별점·소감은 유지된다(Repository.setStatus). */
    fun restartReading() = changeStatus(BookStatus.READING)

    private fun changeStatus(status: BookStatus) {
        val book = uiState.value.book ?: return
        viewModelScope.launch { bookRepository.setStatus(book, status) }
    }

    fun requestDelete() = local.update { it.copy(confirmDelete = true) }

    fun cancelDelete() = local.update { it.copy(confirmDelete = false) }

    /** 확인 다이얼로그 뒤에만 호출된다. 글귀·토론은 CASCADE로 함께 지워진다. */
    fun confirmDelete() {
        val book = uiState.value.book ?: return
        viewModelScope.launch {
            bookRepository.delete(book)
            local.update { it.copy(confirmDelete = false, deleted = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<BookDetailRoute>()
                BookDetailViewModel(appContainer().bookRepository, route.bookId)
            }
        }
    }
}

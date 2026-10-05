package com.logiclinear.reading.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.logiclinear.reading.appContainer
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.Quote
import com.logiclinear.reading.data.repo.BookRepository
import com.logiclinear.reading.data.repo.QuoteRepository
import com.logiclinear.reading.domain.ONE_LINER_MAX
import com.logiclinear.reading.domain.normalizeReview
import com.logiclinear.reading.ui.navigation.BookDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 책 상세. 상태 모델은 [BookDetailState.kt]. */
class BookDetailViewModel(
    private val bookRepository: BookRepository,
    private val quoteRepository: QuoteRepository,
    private val bookId: Long,
) : ViewModel() {
    private val local = MutableStateFlow(BookDetailLocalState())

    val uiState: StateFlow<BookDetailUiState> = combine(
        bookRepository.observeById(bookId),
        quoteRepository.observeByBook(bookId),
        local,
    ) { book, quotes, state -> state.toUiState(book, quotes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailUiState())

    // ---- 글귀 삭제·되돌리기 (T-404) ----

    /** 길게 눌러 삭제. DB에서 바로 지우고 되돌리기 후보로 둔다(요구사항 "삭제": 되돌리기 스낵바 5초). */
    fun deleteQuote(quote: Quote) {
        viewModelScope.launch {
            quoteRepository.delete(quote)
            local.update { it.copy(undoCandidate = quote) }
        }
    }

    /** 스낵바 "되돌리기". 책이 그 사이 지워졌으면 복구할 수 없어 조용히 끝낸다(T-404 인계 메모). */
    fun undoDelete() {
        val quote = local.value.undoCandidate ?: return
        local.update { it.copy(undoCandidate = null) }
        viewModelScope.launch { quoteRepository.restore(quote) }
    }

    /** 스낵바가 5초 뒤 사라지면 호출. 이미 DB에서 지워져 있으므로 할 일은 후보 비우기뿐이다. */
    fun clearUndo() = local.update { it.copy(undoCandidate = null) }

    /** WANT → READING ("읽기 시작"). */
    fun startReading() = changeStatus(BookStatus.READING)

    /** DONE → READING ("다시 읽기"). 별점·소감은 유지된다(Repository.setStatus). */
    fun restartReading() = changeStatus(BookStatus.READING)

    private fun changeStatus(status: BookStatus) {
        val book = uiState.value.book ?: return
        viewModelScope.launch { bookRepository.setStatus(book, status) }
    }

    // ---- 완독 처리 (T-401·T-402) ----

    /** "다 읽었어요" → 시트를 연다. 재완독이면 기존 별점·소감을 초안에 미리 채운다. */
    fun openFinish() = local.update {
        val book = uiState.value.book
        it.copy(
            finishOpen = true,
            finishDraft = FinishDraft(rating = book?.rating, oneLiner = book?.oneLiner.orEmpty(), finishedAt = LocalDate.now()),
        )
    }

    fun closeFinish() = local.update { it.copy(finishOpen = false) }

    /** 같은 별을 다시 탭하면 해제한다. */
    fun setRating(rating: Int) = local.update {
        it.copy(finishDraft = it.finishDraft.copy(rating = if (it.finishDraft.rating == rating) null else rating))
    }

    fun setOneLiner(value: String) = local.update {
        it.copy(finishDraft = it.finishDraft.copy(oneLiner = value.take(ONE_LINER_MAX)))
    }

    fun setFinishedAt(date: LocalDate) = local.update { it.copy(finishDraft = it.finishDraft.copy(finishedAt = date)) }

    /** 저장 → DONE. 저장 직후 토론 제안을 띄운다(요구사항 "흐름 3" 3단계). */
    fun confirmFinish() {
        val book = uiState.value.book ?: return
        val draft = local.value.finishDraft
        viewModelScope.launch {
            bookRepository.finishBook(book, normalizeReview(draft.rating, draft.oneLiner), draft.finishedAt)
            local.update { it.copy(finishOpen = false, proposalOpen = true) }
        }
    }

    /** "나중에" 또는 바깥 탭. 토론 시작은 T-707에서 연결된다. */
    fun dismissProposal() = local.update { it.copy(proposalOpen = false) }

    // ---- 삭제 (T-112) ----

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
                val container = appContainer()
                BookDetailViewModel(container.bookRepository, container.quoteRepository, route.bookId)
            }
        }
    }
}

package com.logiclinear.reading.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.logiclinear.reading.appContainer
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.remote.aladin.AladinItem
import com.logiclinear.reading.data.remote.aladin.AladinResult
import com.logiclinear.reading.data.repo.AddResult
import com.logiclinear.reading.data.repo.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 책 검색 화면 상태(요구사항 "흐름 2"). 검색 실패·오프라인·한도 초과·결과 없음은 모두 [fallbackQuery]로
 * 직접 입력 폼에 검색어를 넘긴다(요구사항 "예외 처리"). TTB 키가 없으면 [needsKey].
 */
data class BookSearchUiState(
    val query: String = "",
    val status: BookStatus = BookStatus.READING,
    val searching: Boolean = false,
    val results: List<AladinItem> = emptyList(),
    val searched: Boolean = false,
    val needsKey: Boolean = false,
    /** 직접 입력 폼으로 전환. 화면이 이 값을 보고 이동한 뒤 [consumeFallback]. */
    val fallbackQuery: String? = null,
    /** 같은 isbn13이 이미 있을 때. 문구는 요구사항 원문 "이미 서재에 있어요". */
    val duplicate: Boolean = false,
    val registering: Boolean = false,
    /** 등록된 책 id. 화면은 서재로 돌아간다. */
    val registeredBookId: Long? = null,
)

class BookSearchViewModel(
    private val bookRepository: BookRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookSearchUiState())
    val uiState: StateFlow<BookSearchUiState> = _uiState

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value, duplicate = false) }

    fun onStatusChange(status: BookStatus) = _uiState.update { it.copy(status = status) }

    /** 검색. 결과 없음·오류는 직접 입력 폼으로 전환한다. */
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty() || _uiState.value.searching) return
        _uiState.update { it.copy(searching = true, needsKey = false, duplicate = false) }
        viewModelScope.launch {
            when (val result = bookRepository.searchAladin(query)) {
                is AladinResult.Found -> _uiState.update { it.copy(searching = false, searched = true, results = result.items) }
                AladinResult.NoKey -> _uiState.update { it.copy(searching = false, needsKey = true) }
                AladinResult.Empty, is AladinResult.ApiError, is AladinResult.Network ->
                    _uiState.update { it.copy(searching = false, searched = true, results = emptyList(), fallbackQuery = query) }
            }
        }
    }

    /** "직접 입력" 버튼. 검색어를 제목에 미리 채운다. */
    fun requestManualEntry() = _uiState.update { it.copy(fallbackQuery = it.query.trim()) }

    fun consumeFallback() = _uiState.update { it.copy(fallbackQuery = null) }

    /** 결과를 탭하면 등록(요구사항 "흐름 2" 2단계). 같은 isbn13이 있으면 막는다. */
    fun register(item: AladinItem) {
        if (_uiState.value.registering) return
        _uiState.update { it.copy(registering = true, duplicate = false) }
        viewModelScope.launch {
            when (val result = bookRepository.addFromSearch(item, _uiState.value.status)) {
                is AddResult.Added -> _uiState.update { it.copy(registering = false, registeredBookId = result.bookId) }
                is AddResult.Duplicate -> _uiState.update { it.copy(registering = false, duplicate = true) }
            }
        }
    }

    fun dismissDuplicate() = _uiState.update { it.copy(duplicate = false) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BookSearchViewModel(appContainer().bookRepository) }
        }
    }
}

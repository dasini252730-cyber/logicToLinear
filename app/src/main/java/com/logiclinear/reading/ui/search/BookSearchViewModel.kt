package com.logiclinear.reading.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.logiclinear.reading.appContainer
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.remote.books.BookSearchItem
import com.logiclinear.reading.data.remote.books.BookSearchResult
import com.logiclinear.reading.data.repo.AddResult
import com.logiclinear.reading.data.repo.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 책 검색 화면 상태(요구사항 "흐름 2"). 검색 실패·오프라인·한도 초과·결과 없음은 모두 [fallbackQuery]로
 * 직접 입력 폼에 검색어를 넘긴다(요구사항 "예외 처리"). 검색 키가 없으면 [needsKey].
 */
data class BookSearchUiState(
    val query: String = "",
    val status: BookStatus = BookStatus.READING,
    val searching: Boolean = false,
    val results: List<BookSearchItem> = emptyList(),
    /** 국립중앙도서관 인증키가 설정에 없다. */
    val needsKey: Boolean = false,
    /** 검색 공급자가 키 오류(401·403)를 돌려줬다. 폴백이 아니라 설정 안내로 보낸다. */
    val keyInvalid: Boolean = false,
    /** 직접 입력 폼으로 전환. 화면이 이 값을 보고 이동한 뒤 [consumeFallback]. */
    val fallbackQuery: String? = null,
    /** 같은 isbn13이 이미 있을 때. 문구는 요구사항 원문 "이미 서재에 있어요". */
    val duplicate: Boolean = false,
    val registering: Boolean = false,
    /** 등록된 책 id. 화면은 서재로 돌아간다. */
    val registeredBookId: Long? = null,
)

/**
 * [savedState]에 검색어와 선택 상태를 두어 프로세스 재시작(설정 위에서 키를 복사하러 브라우저로 갔다가 종료) 뒤에도
 * 남긴다(T-811). 결과 목록·안내 플래그는 다시 검색하면 되므로 보관하지 않는다.
 */
class BookSearchViewModel(
    private val bookRepository: BookRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        BookSearchUiState(
            query = savedState[KEY_QUERY] ?: "",
            status = savedState.get<String>(KEY_STATUS)?.let { name -> BookStatus.entries.firstOrNull { it.name == name } }
                ?: BookStatus.READING,
        ),
    )
    val uiState: StateFlow<BookSearchUiState> = _uiState

    fun onQueryChange(value: String) {
        // 저장만 자른다(붙여넣은 긴 텍스트가 saved instance state 번들 한도를 넘지 않게). 화면 값은 그대로.
        savedState[KEY_QUERY] = value.take(MAX_SAVED_QUERY)
        _uiState.update { it.copy(query = value, duplicate = false) }
    }

    fun onStatusChange(status: BookStatus) {
        savedState[KEY_STATUS] = status.name
        _uiState.update { it.copy(status = status) }
    }

    /** 검색. 결과 없음·오류는 직접 입력 폼으로 전환한다. */
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty() || _uiState.value.searching) return
        _uiState.update { it.copy(searching = true, needsKey = false, keyInvalid = false, duplicate = false) }
        viewModelScope.launch {
            when (val result = bookRepository.searchBooks(query)) {
                is BookSearchResult.Found -> _uiState.update { it.copy(searching = false, results = result.items) }
                BookSearchResult.NoKey -> _uiState.update { it.copy(searching = false, needsKey = true) }
                BookSearchResult.InvalidKey -> _uiState.update { it.copy(searching = false, results = emptyList(), keyInvalid = true) }
                BookSearchResult.Empty, is BookSearchResult.ApiError, is BookSearchResult.Network ->
                    _uiState.update { it.copy(searching = false, results = emptyList(), fallbackQuery = query) }
            }
        }
    }

    /**
     * 설정에서 돌아왔을 때(T-809). 키 없음·키 오류 안내를 지워 "키를 넣고 왔는데 안내가 그대로"를 막는다.
     * 검색어는 남기고 재검색은 하지 않는다(사용자 동작 없는 네트워크 호출·폴백 이동을 피함). 돋보기를 누르면 새 키로 검색한다.
     */
    fun clearKeyNotice() = _uiState.update {
        if (it.needsKey || it.keyInvalid) it.copy(needsKey = false, keyInvalid = false) else it
    }

    /** "직접 입력" 버튼. 검색어를 제목에 미리 채운다. */
    fun requestManualEntry() = _uiState.update { it.copy(fallbackQuery = it.query.trim()) }

    fun consumeFallback() = _uiState.update { it.copy(fallbackQuery = null) }

    /** 결과를 탭하면 등록(요구사항 "흐름 2" 2단계). 같은 isbn13이 있으면 막는다. */
    fun register(item: BookSearchItem) {
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
        private const val KEY_QUERY = "searchQuery"
        private const val KEY_STATUS = "searchStatus"
        /** 책 제목 검색어로 충분한 길이. 복원 시 이 길이까지만 돌아온다. */
        const val MAX_SAVED_QUERY = 200

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BookSearchViewModel(appContainer().bookRepository, createSavedStateHandle()) }
        }
    }
}

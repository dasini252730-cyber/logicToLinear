package com.logiclinear.reading.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.logiclinear.reading.appContainer
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.repo.BookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 서재 화면 상태. 탭 순서는 요구사항 "서재": 읽는 중 / 읽고 싶음 / 다 읽음. */
data class LibraryUiState(
    val tab: BookStatus = BookStatus.READING,
    val books: List<Book> = emptyList(),
    val loaded: Boolean = false,
)

val LIBRARY_TABS: List<BookStatus> = listOf(BookStatus.READING, BookStatus.WANT, BookStatus.DONE)

class LibraryViewModel(private val bookRepository: BookRepository) : ViewModel() {
    private val selectedTab = MutableStateFlow(BookStatus.READING)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = selectedTab
        .flatMapLatest { tab ->
            bookRepository.observeByStatus(tab).map { books -> LibraryUiState(tab, books, loaded = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun selectTab(tab: BookStatus) {
        selectedTab.value = tab
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(appContainer().bookRepository) }
        }
    }
}

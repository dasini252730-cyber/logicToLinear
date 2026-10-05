package com.logiclinear.reading.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logiclinear.reading.R
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.ui.theme.ReadingLogTheme

/** 네비게이션 진입점. ViewModel을 만들고 상태를 [LibraryScreen]에 넘긴다. */
@Composable
fun LibraryEntry(
    onBookClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(state = state, onTabSelect = viewModel::selectTab, onBookClick = onBookClick, onAddClick = onAddClick)
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onTabSelect: (BookStatus) -> Unit,
    onBookClick: (Long) -> Unit,
    onAddClick: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.library_add_book))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = LIBRARY_TABS.indexOf(state.tab)) {
                LIBRARY_TABS.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        onClick = { onTabSelect(tab) },
                        text = { Text(stringResource(tab.tabLabelRes())) },
                    )
                }
            }
            if (state.loaded && state.books.isEmpty()) {
                EmptyLibrary(state.tab)
            } else {
                BookList(state.books, onBookClick)
            }
        }
    }
}

@Composable
private fun BookList(books: List<Book>, onBookClick: (Long) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(books, key = { it.id }) { book -> BookRow(book) { onBookClick(book.id) } }
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverPlaceholder(book.title)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = book.author ?: stringResource(R.string.library_author_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 표지가 없을 때 제목 첫 글자. 표지 이미지 로딩은 T-306에서 이 자리를 대체한다. */
@Composable
private fun CoverPlaceholder(title: String) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 60.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.trim().take(1),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun EmptyLibrary(tab: BookStatus) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(tab.emptyLabelRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun BookStatus.tabLabelRes(): Int = when (this) {
    BookStatus.READING -> R.string.library_tab_reading
    BookStatus.WANT -> R.string.library_tab_want
    BookStatus.DONE -> R.string.library_tab_done
}

private fun BookStatus.emptyLabelRes(): Int = when (this) {
    BookStatus.READING -> R.string.library_empty_reading
    BookStatus.WANT -> R.string.library_empty_want
    BookStatus.DONE -> R.string.library_empty_done
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    ReadingLogTheme {
        LibraryScreen(
            state = LibraryUiState(
                tab = BookStatus.READING,
                books = listOf(Book(id = 1, title = "채식주의자", author = "한강"), Book(id = 2, title = "제목만 있는 책")),
                loaded = true,
            ),
            onTabSelect = {},
            onBookClick = {},
            onAddClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenEmptyPreview() {
    ReadingLogTheme {
        LibraryScreen(state = LibraryUiState(loaded = true), onTabSelect = {}, onBookClick = {}, onAddClick = {})
    }
}

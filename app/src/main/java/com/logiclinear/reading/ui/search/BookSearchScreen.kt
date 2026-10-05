package com.logiclinear.reading.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logiclinear.reading.R
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.remote.aladin.AladinItem
import com.logiclinear.reading.ui.book.BOOK_STATUS_CHOICES
import com.logiclinear.reading.ui.components.BookCover
import com.logiclinear.reading.ui.components.labelRes
import com.logiclinear.reading.ui.theme.ReadingLogTheme

/** 네비게이션 진입점. 등록되면 서재로, 검색 실패·직접 입력이면 직접 입력 폼으로 간다. */
@Composable
fun BookSearchEntry(
    onBack: () -> Unit,
    onRegistered: () -> Unit,
    onManualEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: BookSearchViewModel = viewModel(factory = BookSearchViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.registeredBookId) { if (state.registeredBookId != null) onRegistered() }
    LaunchedEffect(state.fallbackQuery) {
        val query = state.fallbackQuery ?: return@LaunchedEffect
        viewModel.consumeFallback()
        onManualEntry(query)
    }
    BookSearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onStatusChange = viewModel::onStatusChange,
        onSearch = viewModel::search,
        onPick = viewModel::register,
        onManualEntry = viewModel::requestManualEntry,
        onDismissDuplicate = viewModel::dismissDuplicate,
        onOpenSettings = onOpenSettings,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSearchScreen(
    state: BookSearchUiState,
    onQueryChange: (String) -> Unit,
    onStatusChange: (BookStatus) -> Unit,
    onSearch: () -> Unit,
    onPick: (AladinItem) -> Unit,
    onManualEntry: () -> Unit,
    onDismissDuplicate: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val duplicateMessage = stringResource(R.string.search_duplicate)
    LaunchedEffect(state.duplicate) {
        if (state.duplicate) {
            snackbar.showSnackbar(duplicateMessage)
            onDismissDuplicate()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = { TextButton(onClick = onManualEntry) { Text(stringResource(R.string.search_manual_entry)) } },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.search_query_label)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onSearch, enabled = !state.searching) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_action))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            StatusChoice(state.status, onStatusChange)
            Spacer(Modifier.height(8.dp))
            when {
                state.needsKey || state.keyInvalid -> NeedsKey(invalid = state.keyInvalid, onOpenSettings, onManualEntry)
                state.searching -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> ResultList(state.results, enabled = !state.registering, onPick = onPick)
            }
        }
    }
}

/** 등록 시 적용할 상태. 기본 READING(요구사항 "흐름 2": 상태는 기본 READING, 선택 가능). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusChoice(selected: BookStatus, onSelect: (BookStatus) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        BOOK_STATUS_CHOICES.forEachIndexed { index, status ->
            SegmentedButton(
                selected = status == selected,
                onClick = { onSelect(status) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = BOOK_STATUS_CHOICES.size),
                label = { Text(stringResource(status.labelRes())) },
            )
        }
    }
}

@Composable
private fun ResultList(items: List<AladinItem>, enabled: Boolean, onPick: (AladinItem) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 알라딘이 같은 isbn13을 두 번 주거나 isbn·link가 모두 빈 동명 항목이 있어도 키가 겹치지 않게 index를 섞는다.
        itemsIndexed(items, key = { index, it -> "$index-${it.isbn13}" }) { _, item ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onPick(item) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(title = item.title, coverUrl = item.cover.ifEmpty { null })
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = listOf(item.author, item.publisher).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NeedsKey(invalid: Boolean, onOpenSettings: () -> Unit, onManualEntry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(stringResource(if (invalid) R.string.search_key_invalid else R.string.search_needs_key), style = MaterialTheme.typography.bodyLarge)
        Row {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.search_open_settings)) }
            TextButton(onClick = onManualEntry) { Text(stringResource(R.string.search_manual_entry)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BookSearchScreenPreview() {
    ReadingLogTheme {
        BookSearchScreen(
            state = BookSearchUiState(
                query = "채식주의자",
                results = listOf(AladinItem(title = "채식주의자", author = "한강 (지은이)", publisher = "창비", isbn13 = "9788936433598")),
            ),
            onQueryChange = {}, onStatusChange = {}, onSearch = {}, onPick = {}, onManualEntry = {}, onDismissDuplicate = {}, onOpenSettings = {}, onBack = {},
        )
    }
}

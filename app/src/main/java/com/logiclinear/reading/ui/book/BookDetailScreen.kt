package com.logiclinear.reading.ui.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logiclinear.reading.R
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.ui.components.BookCover
import com.logiclinear.reading.ui.components.labelRes
import com.logiclinear.reading.ui.theme.ReadingLogTheme

/** 상세 화면 이벤트. 완독 처리는 T-401, 글귀 목록은 T-403에서 추가된다. */
data class BookDetailActions(
    val onBack: () -> Unit,
    val onStartReading: () -> Unit,
    val onRestartReading: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
)

@Composable
fun BookDetailEntry(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: BookDetailViewModel = viewModel(factory = BookDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    BookDetailScreen(
        state = state,
        actions = BookDetailActions(
            onBack = onBack,
            onStartReading = viewModel::startReading,
            onRestartReading = viewModel::restartReading,
            onRequestDelete = viewModel::requestDelete,
            onCancelDelete = viewModel::cancelDelete,
            onConfirmDelete = viewModel::confirmDelete,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(state: BookDetailUiState, actions: BookDetailActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.book?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = { if (state.book != null) OverflowMenu(onDelete = actions.onRequestDelete) },
            )
        },
    ) { innerPadding ->
        val book = state.book
        if (book != null) {
            BookInfo(book, actions, modifier = Modifier.padding(innerPadding))
        } else if (state.loaded && !state.deleted) {
            Text(stringResource(R.string.book_detail_not_found), modifier = Modifier.padding(innerPadding).padding(16.dp))
        }
    }
    if (state.confirmDelete) DeleteDialog(onConfirm = actions.onConfirmDelete, onDismiss = actions.onCancelDelete)
}

@Composable
private fun BookInfo(book: Book, actions: BookDetailActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            BookCover(title = book.title, coverUrl = book.coverUrl, width = 96.dp, height = 132.dp)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(book.title, style = MaterialTheme.typography.headlineSmall)
                Text(book.author ?: stringResource(R.string.library_author_unknown), style = MaterialTheme.typography.bodyLarge)
                book.publisher?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                book.category?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Spacer(Modifier.height(12.dp))
        AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(book.status.labelRes())) })
        Spacer(Modifier.height(24.dp))
        StatusActions(book.status, actions)
    }
}

/** 상태별 전이 버튼(요구사항 "책 상태 전이"). READING의 완독 버튼은 T-401 전까지 비활성 자리만 둔다. */
@Composable
private fun StatusActions(status: BookStatus, actions: BookDetailActions) {
    when (status) {
        BookStatus.WANT -> Button(onClick = actions.onStartReading, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.book_action_start_reading))
        }
        BookStatus.READING -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.book_action_finish))
        }
        BookStatus.DONE -> OutlinedButton(onClick = actions.onRestartReading, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.book_action_restart_reading))
        }
    }
}

@Composable
private fun OverflowMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.book_action_delete)) },
            onClick = { open = false; onDelete() },
        )
    }
}

/** 요구사항 "삭제": 확인 다이얼로그 후 삭제, 글귀·토론도 함께 삭제됨을 알린다. */
@Composable
private fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.book_delete_title)) },
        text = { Text(stringResource(R.string.book_delete_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.book_action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun BookDetailScreenPreview() {
    val noop = BookDetailActions({}, {}, {}, {}, {}, {})
    ReadingLogTheme {
        BookDetailScreen(
            state = BookDetailUiState(
                book = Book(id = 1, title = "채식주의자", author = "한강", publisher = "창비", category = "국내도서>소설", status = BookStatus.DONE),
                loaded = true,
            ),
            actions = noop,
        )
    }
}

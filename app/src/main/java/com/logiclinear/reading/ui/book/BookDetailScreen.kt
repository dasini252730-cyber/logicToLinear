package com.logiclinear.reading.ui.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.logiclinear.reading.data.db.Quote
import com.logiclinear.reading.ui.components.BookCover
import com.logiclinear.reading.ui.components.labelRes
import com.logiclinear.reading.ui.theme.ReadingLogTheme

/** 상세 화면 이벤트. 글귀 삭제는 T-404에서 추가된다. */
data class BookDetailActions(
    val onBack: () -> Unit,
    val onStartReading: () -> Unit,
    val onRestartReading: () -> Unit,
    val onOpenFinish: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissProposal: () -> Unit,
    val onDeleteQuote: (Quote) -> Unit,
    val onUndoDelete: () -> Unit,
    /** 스낵바를 밀어서 치웠을 때. 5초 만료는 ViewModel이 센다. */
    val onUndoDismissed: () -> Unit,
    val finish: FinishActions,
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
            onOpenFinish = viewModel::openFinish,
            onRequestDelete = viewModel::requestDelete,
            onCancelDelete = viewModel::cancelDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onDismissProposal = viewModel::dismissProposal,
            onDeleteQuote = viewModel::deleteQuote,
            onUndoDelete = viewModel::undoDelete,
            onUndoDismissed = viewModel::clearUndo,
            finish = FinishActions(
                onRating = viewModel::setRating,
                onOneLiner = viewModel::setOneLiner,
                onFinishedAt = viewModel::setFinishedAt,
                onSave = viewModel::confirmFinish,
                onDismiss = viewModel::closeFinish,
            ),
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(state: BookDetailUiState, actions: BookDetailActions) {
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.quote_undo)
    val deletedMessage = stringResource(R.string.quote_deleted)
    // 요구사항 "삭제": 되돌리기 스낵바 5초. 후보가 있는 동안만 떠 있고, 5초 만료는 ViewModel이 후보를 비워서 알린다.
    LaunchedEffect(state.undoCandidate) {
        if (state.undoCandidate == null) {
            snackbar.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        val result = snackbar.showSnackbar(deletedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete() else actions.onUndoDismissed()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
            BookBody(book, state, actions, modifier = Modifier.padding(innerPadding))
        } else if (state.loaded && !state.deleted) {
            Text(stringResource(R.string.book_detail_not_found), modifier = Modifier.padding(innerPadding).padding(16.dp))
        }
    }
    if (state.confirmDelete) DeleteDialog(onConfirm = actions.onConfirmDelete, onDismiss = actions.onCancelDelete)
    if (state.finishOpen) FinishBookSheet(state.finishDraft, actions.finish)
    if (state.proposalOpen) DiscussionProposalDialog(onDismiss = actions.onDismissProposal)
}

@Composable
private fun BookBody(book: Book, state: BookDetailUiState, actions: BookDetailActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
        // 다시 읽기(DONE→READING) 뒤에도 이전 별점·한 줄·완독일을 그대로 보여준다(T-402 완료 조건).
        if (book.hasReview()) {
            Spacer(Modifier.height(12.dp))
            ReviewSection(book)
        }
        Spacer(Modifier.height(20.dp))
        StatusActions(book.status, actions)
        Spacer(Modifier.height(24.dp))
        QuotesSection(state.quotes, onLongPress = actions.onDeleteQuote)
    }
}

private fun Book.hasReview() = rating != null || oneLiner != null || finishedAt != null

@Preview(showBackground = true)
@Composable
private fun BookDetailScreenPreview() {
    val noop = BookDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, FinishActions({}, {}, {}, {}, {}))
    ReadingLogTheme {
        BookDetailScreen(
            state = BookDetailUiState(
                book = Book(id = 1, title = "채식주의자", author = "한강", publisher = "창비", category = "국내도서>소설", status = BookStatus.DONE, rating = 4, oneLiner = "서늘했다"),
                loaded = true,
            ),
            actions = noop,
        )
    }
}

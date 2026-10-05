package com.logiclinear.reading.ui.book

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.logiclinear.reading.R
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.Quote
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 책 상세·완독 시트가 같은 날짜 표기를 쓴다. */
internal val BOOK_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val DATE_FORMAT: DateTimeFormatter get() = BOOK_DATE_FORMAT

/** 별점·한 줄·완독일(T-403). 값이 없으면 그 줄은 비운다. 다시 읽기 뒤에도 보인다. */
@Composable
fun ReviewSection(book: Book) {
    Column {
        val ratingLabel = book.rating?.let { stringResource(R.string.finish_rating_star, it) }
        // 별 아이콘 n개 대신 "별점 n" 하나로 읽힌다.
        Row(modifier = if (ratingLabel != null) Modifier.semantics(mergeDescendants = true) { contentDescription = ratingLabel } else Modifier) {
            repeat(book.rating ?: 0) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            if (book.rating == null) {
                Text(stringResource(R.string.review_no_rating), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        book.oneLiner?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp)) }
        book.finishedAt?.let {
            Text(
                text = stringResource(R.string.review_finished_at, it.format(DATE_FORMAT)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 글귀 목록 헤더 + 항목들(T-403). 최신순은 DAO가 보장한다. 길게 누르면 삭제(T-404). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuotesSection(quotes: List<Quote>, onLongPress: (Quote) -> Unit) {
    Column {
        Text(stringResource(R.string.quotes_header, quotes.size), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (quotes.isEmpty()) {
            Text(stringResource(R.string.quotes_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val deleteLabel = stringResource(R.string.book_action_delete)
        quotes.forEachIndexed { index, quote ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            QuoteItem(
                quote = quote,
                // 스크린리더가 "삭제" 동작을 찾을 수 있게 라벨을 둔다. 탭 자체는 아무 일도 하지 않는다.
                modifier = Modifier.combinedClickable(onClick = {}, onLongClickLabel = deleteLabel, onLongClick = { onLongPress(quote) }),
            )
        }
    }
}

@Composable
private fun QuoteItem(quote: Quote, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(quote.text, style = MaterialTheme.typography.bodyLarge)
        val meta = buildList {
            quote.page?.let { add(stringResource(R.string.quote_page, it)) }
            add(quote.createdAt.atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT))
        }.joinToString(" · ")
        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 상태별 전이 버튼(요구사항 "책 상태 전이"). READING은 완독 처리 시트를 연다(T-401). */
@Composable
fun StatusActions(status: BookStatus, actions: BookDetailActions) {
    when (status) {
        BookStatus.WANT -> Button(onClick = actions.onStartReading, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.book_action_start_reading))
        }
        BookStatus.READING -> Button(onClick = actions.onOpenFinish, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.book_action_finish))
        }
        BookStatus.DONE -> OutlinedButton(onClick = actions.onRestartReading, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.book_action_restart_reading))
        }
    }
}

@Composable
fun OverflowMenu(onDelete: () -> Unit) {
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
fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.book_delete_title)) },
        text = { Text(stringResource(R.string.book_delete_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.book_action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * 완독 저장 직후 "이 책에 대해 AI와 이야기해볼까요?"(요구사항 "흐름 3" 3단계, T-405).
 * 토론 기능(T-707) 전까지 시작 버튼은 비활성 자리다.
 */
@Composable
fun DiscussionProposalDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.proposal_title)) },
        text = { Text(stringResource(R.string.proposal_not_ready)) },
        confirmButton = { Button(onClick = {}, enabled = false) { Text(stringResource(R.string.proposal_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.proposal_later)) } },
    )
}

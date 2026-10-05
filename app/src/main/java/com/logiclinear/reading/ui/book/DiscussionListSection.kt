package com.logiclinear.reading.ui.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.logiclinear.reading.R
import com.logiclinear.reading.data.db.Discussion
import com.logiclinear.reading.domain.decodeMessages
import java.time.ZoneId

/**
 * 책 상세의 토론 목록(T-707): 시작일, 진행 중/종료, 메시지 수. 탭하면 토론 화면으로.
 * DONE 책에는 "새 토론" 버튼을 둔다(한 책에 토론 여러 개). 토론이 없고 DONE도 아니면 절 자체를 숨긴다.
 */
@Composable
fun DiscussionListSection(
    discussions: List<Discussion>,
    canStart: Boolean,
    onStart: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    if (discussions.isEmpty() && !canStart) return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.discussion_list_header, discussions.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (canStart) TextButton(onClick = onStart) { Text(stringResource(R.string.discussion_new)) }
        }
        Spacer(Modifier.height(4.dp))
        discussions.forEachIndexed { index, d ->
            if (index > 0) HorizontalDivider()
            DiscussionRow(d, onClick = { onOpen(d.id) })
        }
    }
}

@Composable
private fun DiscussionRow(discussion: Discussion, onClick: () -> Unit) {
    val count = decodeMessages(discussion.messagesJson).size
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(discussion.startedAt.atZone(ZoneId.systemDefault()).toLocalDate().format(BOOK_DATE_FORMAT), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.discussion_message_count, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = stringResource(if (discussion.endedAt == null) R.string.discussion_in_progress else R.string.discussion_ended_label),
            style = MaterialTheme.typography.labelMedium,
            color = if (discussion.endedAt == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

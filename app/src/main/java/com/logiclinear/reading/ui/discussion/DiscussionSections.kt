package com.logiclinear.reading.ui.discussion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.logiclinear.reading.R
import com.logiclinear.reading.domain.DiscussionMessage

/** 말풍선. AI는 왼쪽, 사용자는 오른쪽. 전송 실패한 사용자 메시지는 "전송 안 됨" + 재전송(T-706). */
@Composable
internal fun MessageBubble(message: DiscussionMessage, onResend: () -> Unit, resendEnabled: Boolean) {
    val user = message.isUser
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = if (user) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = when {
                message.failed -> MaterialTheme.colorScheme.errorContainer
                user -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(message.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        if (message.failed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.discussion_not_sent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onResend, enabled = resendEnabled) { Text(stringResource(R.string.discussion_resend)) }
            }
        }
    }
}

/** 응답 대기 표시. */
@Composable
internal fun TypingBubble() {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.discussion_waiting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** 하단 입력창 + 전송. 응답 대기 중·실패 메시지가 있을 때는 전송 비활성(T-703). */
@Composable
internal fun InputBar(state: DiscussionUiState, actions: DiscussionActions) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = actions.onInputChange,
                placeholder = { Text(stringResource(R.string.discussion_input_hint)) },
                enabled = !state.sending,
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = actions.onSend, enabled = state.canSend) { Text(stringResource(R.string.discussion_send)) }
        }
    }
}

/** 첫 질문을 못 받은 토론: 입력 대신 "첫 질문 받기"(T-702 실패 경로). */
@Composable
internal fun AskFirstBar(onAsk: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.discussion_first_question_failed), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAsk, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.discussion_ask_first)) }
        }
    }
}

/** 20턴 마무리 뒤: 입력창을 닫는다(요구사항). */
@Composable
internal fun EndedBar() {
    Surface(tonalElevation = 2.dp) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.discussion_ended), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package com.logiclinear.reading.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.logiclinear.reading.R
import com.logiclinear.reading.data.remote.anthropic.AiResult

/**
 * AI 호출 실패 카드(요구사항 "비용 통제": 오류를 보여주고 재시도 버튼). 분석(T-609)과 토론(T-703)이 같은 컴포저블을 쓴다.
 * 다음 동작은 [AiResult.Failure.action]이 정한다: 재시도 / 설정으로 / 없음.
 */
@Composable
fun AiErrorCard(
    failure: AiResult.Failure,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    retryEnabled: Boolean = true,
) {
    val text = when (failure) {
        is AiResult.Rejected -> stringResource(failure.messageRes(), failure.code)
        else -> stringResource(failure.messageRes())
    }
    val action = failure.action()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (action) {
                    AiErrorAction.RETRY -> TextButton(onClick = onRetry, enabled = retryEnabled) { Text(stringResource(R.string.ai_retry)) }
                    AiErrorAction.OPEN_SETTINGS -> TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.ai_open_settings)) }
                    AiErrorAction.NONE -> Unit
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            }
        }
    }
}

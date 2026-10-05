package com.logiclinear.reading.ui.discussion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logiclinear.reading.R
import com.logiclinear.reading.data.remote.anthropic.ChatMessage
import com.logiclinear.reading.domain.DiscussionMessage
import com.logiclinear.reading.ui.ai.AiErrorCard
import com.logiclinear.reading.ui.theme.ReadingLogTheme

data class DiscussionActions(
    val onBack: () -> Unit = {},
    val onInputChange: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onResend: () -> Unit = {},
    val onAskFirst: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@Composable
fun DiscussionEntry(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DiscussionViewModel = viewModel(factory = DiscussionViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DiscussionScreen(
        state = state,
        actions = DiscussionActions(
            onBack = onBack,
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::send,
            onResend = viewModel::resend,
            onAskFirst = viewModel::requestFirstQuestion,
            onDismissError = viewModel::dismissError,
            onOpenSettings = onOpenSettings,
        ),
    )
}

/** 메시지 목록(역할별 좌우 구분) + 하단 입력창. 끝난 토론은 입력창 대신 마무리 안내(T-705). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionScreen(state: DiscussionUiState, actions: DiscussionActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.sending) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size + 1)
    }
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.bookTitle.ifBlank { stringResource(R.string.discussion_title) }, style = MaterialTheme.typography.titleMedium)
                        if (state.loaded && !state.ended) {
                            Text(stringResource(R.string.discussion_turns_left, state.remainingTurns), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            when {
                !state.loaded -> Unit
                state.ended -> EndedBar()
                state.needsFirstQuestion -> AskFirstBar(onAsk = actions.onAskFirst)
                else -> InputBar(state, actions)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            itemsIndexed(state.messages, key = { i, m -> "${m.at}-$i" }) { _, message ->
                MessageBubble(message, onResend = actions.onResend, resendEnabled = !state.sending)
            }
            if (state.sending) item(key = "typing") { TypingBubble() }
            state.error?.let { failure ->
                item(key = "error") {
                    AiErrorCard(
                        failure = failure,
                        onRetry = if (state.hasFailed) actions.onResend else actions.onAskFirst,
                        onOpenSettings = actions.onOpenSettings,
                        onDismiss = actions.onDismissError,
                        retryEnabled = !state.sending,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscussionScreenPreview() {
    ReadingLogTheme {
        DiscussionScreen(
            state = DiscussionUiState(
                loaded = true, bookTitle = "채식주의자", remainingTurns = 18,
                messages = listOf(
                    DiscussionMessage(ChatMessage.ASSISTANT, "별점은 4점인데 저장한 글귀는 모두 뒷부분에 몰려 있어요. 앞부분은 어땠나요?", 1),
                    DiscussionMessage(ChatMessage.USER, "앞은 좀 힘들었어요.", 2),
                    DiscussionMessage(ChatMessage.USER, "그래도 끝까지 읽었어요.", 3, failed = true),
                ),
            ),
            actions = DiscussionActions(),
        )
    }
}

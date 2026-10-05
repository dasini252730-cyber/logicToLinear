package com.logiclinear.reading.ui.analysis

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logiclinear.reading.R
import com.logiclinear.reading.data.db.Analysis
import com.logiclinear.reading.domain.AnalysisReadiness
import com.logiclinear.reading.domain.Recommendation
import com.logiclinear.reading.ui.ai.AiErrorCard
import com.logiclinear.reading.ui.theme.ReadingLogTheme

data class AnalysisActions(
    val onRun: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onToggleHistory: (Long) -> Unit = {},
    val onAddToWant: (Recommendation) -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
)

/** 네비게이션 진입점. 담기 결과 스낵바와 책 정보 링크 열기를 여기서 처리한다. */
@Composable
fun AnalysisEntry(onOpenSettings: () -> Unit, viewModel: AnalysisViewModel = viewModel(factory = AnalysisViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val added = stringResource(R.string.analysis_added)
    val duplicate = stringResource(R.string.search_duplicate)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(if (message == AnalysisMessage.ADDED) added else duplicate)
        viewModel.consumeMessage()
    }
    AnalysisScreen(
        state = state,
        snackbar = snackbar,
        actions = AnalysisActions(
            onRun = viewModel::run,
            onDismissError = viewModel::dismissError,
            onOpenSettings = onOpenSettings,
            onToggleHistory = viewModel::toggleHistory,
            onAddToWant = viewModel::addToWant,
            onOpenLink = { runCatching { uriHandler.openUri(it) } },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    state: AnalysisUiState,
    actions: AnalysisActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_analysis)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item(key = "header") { ReadinessHeader(state.readiness, running = state.running, canRun = state.canRun, onRun = actions.onRun) }
            state.error?.let { failure ->
                item(key = "error") {
                    AiErrorCard(
                        failure = failure,
                        onRetry = actions.onRun,
                        onOpenSettings = actions.onOpenSettings,
                        onDismiss = actions.onDismissError,
                        retryEnabled = state.canRun,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            val latest = state.latest
            if (latest == null) {
                if (state.loaded) item(key = "empty") { EmptyAnalysis() }
            } else {
                item(key = "latest-${latest.analysis.id}") {
                    AnalysisResult(latest, title = stringResource(R.string.analysis_latest), onAddToWant = actions.onAddToWant, onOpenLink = actions.onOpenLink)
                }
            }
            if (state.history.isNotEmpty()) {
                item(key = "history-header") { HistoryHeader(state.history.size) }
                items(state.history, key = { "history-${it.analysis.id}" }) { item ->
                    HistoryRow(
                        item = item,
                        expanded = state.expandedId == item.analysis.id,
                        onToggle = { actions.onToggleHistory(item.analysis.id) },
                        onAddToWant = actions.onAddToWant,
                        onOpenLink = actions.onOpenLink,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalysisScreenPreview() {
    val rec = Recommendation("흰", "한강", "당신이 저장한 '서늘했다' 글귀와 닿아 있어서")
    val item = AnalysisItem(
        Analysis(id = 1, tasteText = "문장의 리듬에 오래 머무는 편이에요.\n\n짧고 단정한 문장 앞에서 손이 멈춥니다.", inputBookCount = 4, inputQuoteCount = 23),
        listOf(RecommendationCard(rec, inLibrary = false), RecommendationCard(rec.copy(title = "소년이 온다"), inLibrary = true)),
    )
    ReadingLogTheme {
        AnalysisScreen(
            state = AnalysisUiState(readiness = AnalysisReadiness(4, 23), loaded = true, latest = item, history = listOf(item.copy(analysis = item.analysis.copy(id = 2)))),
            actions = AnalysisActions(),
        )
    }
}

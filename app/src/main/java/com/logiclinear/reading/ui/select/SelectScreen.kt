package com.logiclinear.reading.ui.select

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logiclinear.reading.R
import com.logiclinear.reading.ocr.OcrLine
import com.logiclinear.reading.ui.theme.ReadingLogTheme

/** 네비게이션 진입점. 저장되면 토스트 "저장됨 (p.123)"를 띄우고 카메라로 돌아간다. */
@Composable
fun SelectEntry(
    onDone: () -> Unit,
    viewModel: SelectViewModel = viewModel(factory = SelectViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.missingCapture) { if (state.missingCapture) onDone() }
    // 요구사항 "흐름 1" 5단계: 토스트 "저장됨 (p.123)". 문구는 구성 변경을 따라가도록 컴포지션에서 읽는다.
    val savedMessage = state.saved?.let { saved ->
        if (saved.page != null) stringResource(R.string.quote_saved_with_page, saved.page) else stringResource(R.string.quote_saved)
    }
    LaunchedEffect(savedMessage) {
        if (savedMessage != null) {
            Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }
    SelectScreen(
        state = state,
        onToggleLine = viewModel::toggleLine,
        onTextChange = viewModel::onTextChange,
        onPageChange = viewModel::onPageChange,
        onSave = viewModel::save,
        onBack = { viewModel.discard(); onDone() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectScreen(
    state: SelectUiState,
    onToggleLine: (Int) -> Unit,
    onTextChange: (String) -> Unit,
    onPageChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            LineList(state.lines, state.selected, onToggleLine, modifier = Modifier.weight(1f))
            QuoteEditor(state, onTextChange, onPageChange, onSave)
        }
    }
}

/** OCR 줄을 그대로 나열한다. 자동 필터링은 하지 않는다(요구사항 "예외 처리": 세로쓰기·표가 섞여도 사용자가 고른다). */
@Composable
private fun LineList(lines: List<OcrLine>, selected: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(lines) { index, line ->
            val isSelected = index in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(index) }
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectScreenPreview() {
    val lines = listOf("나는 그날 처음으로 바", "다를 보았다.", "그리고 울었다.", "123").mapIndexed { i, t ->
        OcrLine(t, 0, i * 100, 500, i * 100 + 40, if (i == 3) 0.95f else 0.2f + i * 0.1f, if (i == 3) 0.97f else 0.22f + i * 0.1f)
    }
    ReadingLogTheme {
        SelectScreen(
            state = SelectUiState(lines = lines, selected = setOf(0, 1), text = "나는 그날 처음으로 바다를 보았다.", pageCandidates = listOf(123), pageInput = "123", pageAutoFilled = true),
            onToggleLine = {}, onTextChange = {}, onPageChange = {}, onSave = {}, onBack = {},
        )
    }
}

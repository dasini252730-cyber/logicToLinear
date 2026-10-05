package com.logiclinear.reading.ui.select

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.logiclinear.reading.R

/**
 * 화면 하단: 합쳐진 글귀 편집 칸 + 페이지 번호 칸 + 저장.
 * 페이지(요구사항 "페이지 번호 자동 인식"): 후보 1개면 자동 채움, 2개 이상이면 빈 칸 + 칩, 직접 입력이 우선, 비워 두고 저장 가능.
 */
@Composable
fun QuoteEditor(
    state: SelectUiState,
    onTextChange: (String) -> Unit,
    onPageChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                label = { Text(stringResource(R.string.select_quote_label)) },
                placeholder = { Text(stringResource(R.string.select_quote_placeholder)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.pageInput,
                onValueChange = onPageChange,
                label = { Text(stringResource(R.string.select_page_label)) },
                supportingText = { if (state.pageAutoFilled) Text(stringResource(R.string.select_page_auto)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp),
            )
            PageChips(state.pageCandidates, state.pageInput, onPageChange)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.select_save))
            }
        }
    }
}

/**
 * OCR이 잡은 페이지 후보 칩. 후보가 하나라도 있으면 보여서, 자동 채운 값을 지웠다가 되돌릴 수 있다.
 * 후보가 많으면 가로로 스크롤한다(표·세로쓰기 페이지에서 숫자 줄이 여럿 잡힐 수 있다).
 */
@Composable
private fun PageChips(candidates: List<Int>, current: String, onPick: (String) -> Unit) {
    if (candidates.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        candidates.forEach { page ->
            AssistChip(
                onClick = { onPick(page.toString()) },
                label = { Text(page.toString()) },
                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                    containerColor = if (current == page.toString()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

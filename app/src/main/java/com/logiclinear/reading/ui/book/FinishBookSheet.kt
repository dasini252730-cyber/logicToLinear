package com.logiclinear.reading.ui.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.logiclinear.reading.R
import com.logiclinear.reading.domain.ONE_LINER_MAX
import com.logiclinear.reading.domain.RATING_MAX
import com.logiclinear.reading.domain.normalizeReview
import com.logiclinear.reading.domain.isEmpty
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 완독 처리 시트 이벤트. */
data class FinishActions(
    val onRating: (Int) -> Unit,
    val onOneLiner: (String) -> Unit,
    val onFinishedAt: (LocalDate) -> Unit,
    val onSave: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * 요구사항 "흐름 3" 2단계: 한 화면에서 별점(1~5 별 탭), 한 줄 소감(100자), 완독일(기본 오늘, 변경 가능) → 저장.
 * 둘 다 비워도 저장되지만 "비우면 분석 품질이 떨어진다" 안내 한 줄을 보여 준다(요구사항 "소감").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishBookSheet(draft: FinishDraft, actions: FinishActions) {
    ModalBottomSheet(onDismissRequest = actions.onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.finish_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            RatingStars(draft.rating, actions.onRating)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.oneLiner,
                onValueChange = actions.onOneLiner,
                label = { Text(stringResource(R.string.finish_one_liner_label)) },
                supportingText = { Text(stringResource(R.string.finish_one_liner_counter, draft.oneLiner.length, ONE_LINER_MAX)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            FinishedAtField(draft.finishedAt, actions.onFinishedAt)
            if (normalizeReview(draft.rating, draft.oneLiner).isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.finish_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = actions.onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.finish_save)) }
        }
    }
}

@Composable
private fun RatingStars(rating: Int?, onRating: (Int) -> Unit) {
    Row {
        for (star in 1..RATING_MAX) {
            val filled = rating != null && star <= rating
            IconButton(onClick = { onRating(star) }) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(R.string.finish_rating_star, star),
                    tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishedAtField(value: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) {
        Text(stringResource(R.string.finish_date_label, value.toString()))
    }
    if (open) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

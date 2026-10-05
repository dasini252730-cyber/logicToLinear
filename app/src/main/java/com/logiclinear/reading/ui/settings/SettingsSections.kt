package com.logiclinear.reading.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.logiclinear.reading.R
import com.logiclinear.reading.data.prefs.AiModel

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

/**
 * API 키 입력 칸(요구사항 "기술 스택": 설정 화면에서 사용자가 직접 입력). 마스킹하고, 저장된 값은 다시 보여주지 않는다.
 * 저장 여부만 "저장됨 / 미입력"으로 표시한다.
 */
@Composable
internal fun KeyField(label: String, value: String, isSet: Boolean, onChange: (String) -> Unit, onSave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSave, enabled = value.isNotBlank()) { Text(stringResource(R.string.settings_save)) }
        }
        Text(
            text = stringResource(if (isSet) R.string.settings_key_saved else R.string.settings_key_not_set),
            style = MaterialTheme.typography.bodySmall,
            color = if (isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 요구사항 "비용 통제": 모델 기본값은 Haiku 계열. 설정에서 Sonnet으로 바꿀 수 있게 한다. */
@Composable
internal fun ModelChoice(selected: AiModel, onSelect: (AiModel) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        AiModel.entries.forEachIndexed { index, model ->
            SegmentedButton(
                selected = model == selected,
                onClick = { onSelect(model) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = AiModel.entries.size),
            ) {
                Text(stringResource(model.labelRes()))
            }
        }
    }
}

/** 요구사항 "백업": 설정 화면의 버튼 두 개로 끝난다. */
@Composable
internal fun BackupButtons(busy: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_export))
        }
        OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_import))
        }
    }
}

/** 요구사항 "백업 > 가져오기": "기존 데이터를 지우고 덮어쓸까요, 합칠까요?" 선택. */
@Composable
internal fun ImportModeDialog(onPick: (ImportMode) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_import)) },
        text = { Text(stringResource(R.string.settings_import_question)) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onPick(ImportMode.OVERWRITE) }) { Text(stringResource(R.string.settings_import_overwrite)) }
                TextButton(onClick = { onPick(ImportMode.MERGE) }) { Text(stringResource(R.string.settings_import_merge)) }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

internal fun AiModel.labelRes(): Int = when (this) {
    AiModel.HAIKU -> R.string.settings_model_haiku
    AiModel.SONNET -> R.string.settings_model_sonnet
}

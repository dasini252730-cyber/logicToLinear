package com.logiclinear.reading.ui.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.ui.components.labelRes
import com.logiclinear.reading.ui.theme.ReadingLogTheme

/** 네비게이션 진입점. 저장되면 [onSaved]로 뒤로 간다. */
@Composable
fun BookFormEntry(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BookFormViewModel = viewModel(factory = BookFormViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedBookId) { if (state.savedBookId != null) onSaved() }
    BookFormScreen(
        state = state,
        onTitleChange = viewModel::onTitleChange,
        onAuthorChange = viewModel::onAuthorChange,
        onStatusChange = viewModel::onStatusChange,
        onSave = viewModel::save,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFormScreen(
    state: BookFormUiState,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onStatusChange: (BookStatus) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.book_form_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.book_form_label_title)) },
                isError = state.titleRequired,
                supportingText = { if (state.titleRequired) Text(stringResource(R.string.book_form_title_required)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.author,
                onValueChange = onAuthorChange,
                label = { Text(stringResource(R.string.book_form_label_author)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            StatusChoice(selected = state.status, onSelect = onStatusChange)
            if (state.duplicateWarning) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.book_form_duplicate_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.duplicateWarning) R.string.book_form_save_anyway else R.string.book_form_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusChoice(selected: BookStatus, onSelect: (BookStatus) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        BOOK_STATUS_CHOICES.forEachIndexed { index, status ->
            SegmentedButton(
                selected = status == selected,
                onClick = { onSelect(status) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = BOOK_STATUS_CHOICES.size),
                label = { Text(stringResource(status.labelRes())) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BookFormScreenPreview() {
    ReadingLogTheme {
        BookFormScreen(
            state = BookFormUiState(title = "채식주의자", author = "한강", duplicateWarning = true),
            onTitleChange = {}, onAuthorChange = {}, onStatusChange = {}, onSave = {}, onBack = {},
        )
    }
}

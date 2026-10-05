package com.logiclinear.reading.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logiclinear.reading.R
import com.logiclinear.reading.data.prefs.AiModel
import com.logiclinear.reading.data.secret.SecretKey
import com.logiclinear.reading.ui.theme.ReadingLogTheme

/** 설정 화면 이벤트. */
data class SettingsActions(
    val onTtbChange: (String) -> Unit = {},
    val onAnthropicChange: (String) -> Unit = {},
    val onSaveKey: (SecretKey) -> Unit = {},
    val onSelectModel: (AiModel) -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onImportMode: (ImportMode) -> Unit = {},
    val onCancelImport: () -> Unit = {},
)

/** 네비게이션 진입점. SAF 런처(내보내기 위치 선택, 가져오기 파일 선택)와 스낵바 메시지를 여기서 처리한다. */
@Composable
fun SettingsEntry(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) {
        viewModel.exportTo(it)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.importFrom(it) }

    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    SettingsScreen(
        state = state,
        snackbar = snackbar,
        actions = SettingsActions(
            onTtbChange = viewModel::onTtbChange,
            onAnthropicChange = viewModel::onAnthropicChange,
            onSaveKey = viewModel::saveKey,
            onSelectModel = viewModel::setModel,
            onExport = { exportLauncher.launch(viewModel.exportFileName()) },
            // JSON 파일이 text/plain이나 application/octet-stream으로 잡히는 파일 관리자도 있어 넓게 받는다.
            onImport = { importLauncher.launch(arrayOf(BACKUP_MIME, "text/*", "application/octet-stream")) },
            onImportMode = viewModel::applyImport,
            onCancelImport = viewModel::cancelImport,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_keys))
            KeyField(
                label = stringResource(R.string.settings_ttb_label),
                value = state.ttbInput,
                isSet = state.ttbSet,
                onChange = actions.onTtbChange,
                onSave = { actions.onSaveKey(SecretKey.ALADIN_TTB) },
            )
            KeyField(
                label = stringResource(R.string.settings_anthropic_label),
                value = state.anthropicInput,
                isSet = state.anthropicSet,
                onChange = actions.onAnthropicChange,
                onSave = { actions.onSaveKey(SecretKey.ANTHROPIC_API) },
            )
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_ai))
            ModelChoice(selected = state.model, onSelect = actions.onSelectModel)
            Text(stringResource(R.string.settings_calls_this_month, state.callsThisMonth))
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_section_backup))
            BackupButtons(busy = state.busy, onExport = actions.onExport, onImport = actions.onImport)
        }
    }
    if (state.pendingImport != null) {
        ImportModeDialog(onPick = actions.onImportMode, onCancel = actions.onCancelImport)
    }
}

/** 메시지 → 문구. 요구사항 원문이 있는 것은 그대로 쓴다. */
@Composable
private fun messageText(message: SettingsMessage): String = when (message) {
    is SettingsMessage.KeySaved -> stringResource(R.string.settings_key_saved)
    SettingsMessage.ExportDone -> stringResource(R.string.settings_export_done)
    SettingsMessage.ExportFailed -> stringResource(R.string.settings_export_failed)
    is SettingsMessage.ImportDone -> stringResource(R.string.settings_import_done, message.summary.books, message.summary.quotes)
    SettingsMessage.ImportFailed -> stringResource(R.string.settings_import_failed)
    SettingsMessage.ImportTooNew -> stringResource(R.string.settings_import_too_new)
    SettingsMessage.ImportCorrupt -> stringResource(R.string.settings_import_corrupt)
}

private const val BACKUP_MIME = "application/json"

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ReadingLogTheme {
        SettingsScreen(state = SettingsUiState(ttbSet = true, callsThisMonth = 3), actions = SettingsActions())
    }
}

package com.logiclinear.reading.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.logiclinear.reading.appContainer
import com.logiclinear.reading.data.backup.BackupFile
import com.logiclinear.reading.data.backup.BackupIo
import com.logiclinear.reading.data.backup.BackupParseResult
import com.logiclinear.reading.data.backup.BackupRepository
import com.logiclinear.reading.data.backup.ImportSummary
import com.logiclinear.reading.data.backup.backupFileName
import com.logiclinear.reading.data.backup.backupJson
import com.logiclinear.reading.data.backup.parseBackup
import com.logiclinear.reading.data.db.nowMillis
import com.logiclinear.reading.data.prefs.AiModel
import com.logiclinear.reading.data.prefs.AppPreferences
import com.logiclinear.reading.data.secret.SecretKey
import com.logiclinear.reading.data.secret.SecretStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** 설정 화면 상태(요구사항 "설정": API 키 입력, JSON 내보내기/가져오기; "비용 통제": 모델 선택, 이번 달 호출 횟수). */
data class SettingsUiState(
    val ttbInput: String = "",
    val anthropicInput: String = "",
    val ttbSet: Boolean = false,
    val anthropicSet: Boolean = false,
    val model: AiModel = AiModel.DEFAULT,
    val callsThisMonth: Int = 0,
    /** 내보내기·가져오기 진행 중. 버튼을 잠근다. */
    val busy: Boolean = false,
    /** 검증을 통과한 가져오기 파일. "덮어쓸까요, 합칠까요?" 다이얼로그가 떠 있다. */
    val pendingImport: BackupFile? = null,
    val message: SettingsMessage? = null,
)

sealed interface SettingsMessage {
    data class KeySaved(val key: SecretKey) : SettingsMessage
    data object ExportDone : SettingsMessage
    data object ExportFailed : SettingsMessage
    data class ImportDone(val summary: ImportSummary) : SettingsMessage
    data object ImportFailed : SettingsMessage
    data object ImportTooNew : SettingsMessage
    data object ImportCorrupt : SettingsMessage
}

enum class ImportMode { OVERWRITE, MERGE }

private data class LocalState(
    val ttbInput: String = "",
    val anthropicInput: String = "",
    val busy: Boolean = false,
    val pendingImport: BackupFile? = null,
    val message: SettingsMessage? = null,
)

class SettingsViewModel(
    private val secretStore: SecretStore,
    private val prefs: AppPreferences,
    private val backupRepository: BackupRepository,
    private val backupIo: BackupIo,
    private val clock: () -> Instant = ::nowMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** EncryptedSharedPreferences 쓰기는 Keystore를 건드리므로 메인 스레드에서 하지 않는다. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<SettingsUiState> = combine(
        secretStore.observeIsSet(SecretKey.ALADIN_TTB),
        secretStore.observeIsSet(SecretKey.ANTHROPIC_API),
        prefs.model,
        prefs.observeCallsIn(YearMonth.from(clock().atZone(zone))),
        local,
    ) { ttbSet, anthropicSet, model, calls, local ->
        SettingsUiState(
            ttbInput = local.ttbInput, anthropicInput = local.anthropicInput,
            ttbSet = ttbSet, anthropicSet = anthropicSet, model = model, callsThisMonth = calls,
            busy = local.busy, pendingImport = local.pendingImport, message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, SettingsUiState())

    fun onTtbChange(value: String) = local.update { it.copy(ttbInput = value) }

    fun onAnthropicChange(value: String) = local.update { it.copy(anthropicInput = value) }

    /** 저장 버튼. 빈 입력은 저장하지 않는다(실수로 키를 지우는 일을 막는다). 저장 후 입력 칸은 비운다. */
    fun saveKey(key: SecretKey) {
        val value = when (key) {
            SecretKey.ALADIN_TTB -> local.value.ttbInput
            SecretKey.ANTHROPIC_API -> local.value.anthropicInput
        }.trim()
        if (value.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            secretStore.set(key, value)
            local.update {
                when (key) {
                    SecretKey.ALADIN_TTB -> it.copy(ttbInput = "", message = SettingsMessage.KeySaved(key))
                    SecretKey.ANTHROPIC_API -> it.copy(anthropicInput = "", message = SettingsMessage.KeySaved(key))
                }
            }
        }
    }

    fun setModel(model: AiModel) = prefs.setModel(model)

    /** CreateDocument 런처에 넘길 파일명. 요구사항 `reading-backup-YYYYMMDD.json`. */
    fun exportFileName(): String = backupFileName(clock().atZone(zone).toLocalDate())

    /** 위치 선택이 끝나면 호출. 취소(null)면 아무 일도 없다. */
    fun exportTo(uri: Uri?) {
        if (uri == null || local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = runCatching {
                backupIo.write(uri, backupJson.encodeToString(BackupFile.serializer(), backupRepository.export(clock())))
            }.fold(onSuccess = { SettingsMessage.ExportDone }, onFailure = { SettingsMessage.ExportFailed })
            local.update { it.copy(busy = false, message = message) }
        }
    }

    /** 파일 선택이 끝나면 호출. 읽고 검증한 뒤 모드 선택 다이얼로그를 띄운다. */
    fun importFrom(uri: Uri?) {
        if (uri == null || local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val text = runCatching { backupIo.read(uri) }.getOrNull()
            val next = when (val parsed = text?.let(::parseBackup)) {
                null -> LocalState(message = SettingsMessage.ImportFailed)
                is BackupParseResult.Ok -> LocalState(pendingImport = parsed.file)
                is BackupParseResult.TooNew -> LocalState(message = SettingsMessage.ImportTooNew)
                BackupParseResult.Corrupt -> LocalState(message = SettingsMessage.ImportCorrupt)
            }
            local.update { it.copy(busy = false, pendingImport = next.pendingImport, message = next.message) }
        }
    }

    fun applyImport(mode: ImportMode) {
        val file = local.value.pendingImport ?: return
        local.update { it.copy(busy = true, pendingImport = null) }
        viewModelScope.launch {
            val message = runCatching {
                when (mode) {
                    ImportMode.OVERWRITE -> backupRepository.overwrite(file)
                    ImportMode.MERGE -> backupRepository.merge(file)
                }
            }.fold(onSuccess = { SettingsMessage.ImportDone(it) }, onFailure = { SettingsMessage.ImportFailed })
            local.update { it.copy(busy = false, message = message) }
        }
    }

    fun cancelImport() = local.update { it.copy(pendingImport = null) }

    fun consumeMessage() = local.update { it.copy(message = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                SettingsViewModel(c.secretStore, c.appPreferences, c.backupRepository, c.backupIo)
            }
        }
    }
}

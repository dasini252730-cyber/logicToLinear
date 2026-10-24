package com.logiclinear.reading.ui.discussion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.logiclinear.reading.appContainer
import com.logiclinear.reading.data.remote.anthropic.AiResult
import com.logiclinear.reading.data.repo.BookRepository
import com.logiclinear.reading.data.repo.DiscussionRepository
import com.logiclinear.reading.data.repo.TurnResult
import com.logiclinear.reading.domain.DiscussionMessage
import com.logiclinear.reading.domain.decodeMessages
import com.logiclinear.reading.domain.remainingTurns
import com.logiclinear.reading.ui.navigation.DiscussionRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 토론 화면 상태(요구사항 "완독 후 토론"). [ended]면 입력창을 닫는다. [hasFailed]면 전송 대신 재전송을 먼저 하게 한다.
 * [needsFirstQuestion]은 AI 메시지가 하나도 없는 상태(첫 질문 실패) — 버튼으로 다시 요청한다.
 * [missing]은 DB를 읽었는데 토론 행이 없는 상태(T-812: 위에 쌓인 설정에서 백업을 덮어써 CASCADE로 지워진 경우).
 * 로딩 중과 구분해 "토론을 찾을 수 없어요"를 보여 준다. CaptureViewModel의 bookLoaded·bookMissing과 같은 꼴.
 */
data class DiscussionUiState(
    /** DB를 한 번이라도 읽었다. false면 로딩 중. */
    val loaded: Boolean = false,
    /** 읽은 결과에 토론 행이 있었다. 화면 요소는 이 값으로 판단한다. */
    val found: Boolean = false,
    val bookTitle: String = "",
    val messages: List<DiscussionMessage> = emptyList(),
    val ended: Boolean = false,
    val remainingTurns: Int = 0,
    val input: String = "",
    val sending: Boolean = false,
    val error: AiResult.Failure? = null,
) {
    val hasFailed: Boolean get() = messages.any { it.failed }
    /** 읽었는데 없다 = 백업 덮어쓰기 등으로 사라졌다(T-812). */
    val missing: Boolean get() = loaded && !found
    val needsFirstQuestion: Boolean get() = found && messages.none { !it.isUser } && !sending
    val canSend: Boolean get() = found && !ended && !sending && !hasFailed && input.isNotBlank()
}

private data class LocalState(val input: String = "", val sending: Boolean = false, val error: AiResult.Failure? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class DiscussionViewModel(
    private val discussionRepository: DiscussionRepository,
    bookRepository: BookRepository,
    private val discussionId: Long,
    /** 방금 만든 토론이면 첫 질문을 자동 요청한다. 다시 열 때는 false. */
    autoFirstQuestion: Boolean,
    /** 프로세스 재시작으로 라우트가 복원돼도 자동 요청이 다시 나가지 않게 "이미 요청했음"을 기억한다. */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    private val discussionWithBook = discussionRepository.observeById(discussionId).flatMapLatest { d ->
        if (d == null) flowOf(null to null) else bookRepository.observeById(d.bookId).map { d to it }
    }

    val uiState: StateFlow<DiscussionUiState> = combine(discussionWithBook, local) { (discussion, book), local ->
        val messages = discussion?.let { decodeMessages(it.messagesJson) } ?: emptyList()
        // combine은 DB 흐름이 한 번 방출한 뒤에만 도므로, 여기 오면 읽은 것이다.
        DiscussionUiState(
            loaded = true,
            found = discussion != null,
            bookTitle = book?.title ?: "",
            messages = messages,
            ended = discussion?.endedAt != null,
            remainingTurns = remainingTurns(messages),
            input = local.input, sending = local.sending, error = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscussionUiState())

    init {
        // 사용자 동작(토론 시작) 직후 한 번만. 실패해도 다시 자동으로 부르지 않고 "첫 질문 받기" 버튼에 맡긴다(요구사항 "비용 통제").
        if (autoFirstQuestion && savedState.get<Boolean>(KEY_AUTO_ASKED) != true) {
            savedState[KEY_AUTO_ASKED] = true
            requestFirstQuestion()
        }
    }

    fun onInputChange(value: String) = local.update { it.copy(input = value) }

    /** 첫 질문 요청(토론 시작 직후 자동, 실패 뒤에는 버튼). */
    fun requestFirstQuestion() = call { discussionRepository.requestFirstQuestion(discussionId) }

    /** 전송: 사용자 메시지는 Repository가 즉시 저장하므로 목록에 바로 나타난다. */
    fun send() {
        val text = local.value.input.trim()
        if (text.isEmpty() || local.value.sending) return
        local.update { it.copy(input = "") }
        call { discussionRepository.send(discussionId, text) }
    }

    /** "전송 안 됨" 메시지 재전송(요구사항 "예외 처리": 토론 중 네트워크 끊김). */
    fun resend() = call { discussionRepository.resend(discussionId) }

    fun dismissError() = local.update { it.copy(error = null) }

    /** 로딩 중 중복 호출 차단(rules/ai-api.md). */
    private fun call(block: suspend () -> TurnResult) {
        if (local.value.sending) return
        local.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val result = block()
            local.update { it.copy(sending = false, error = (result as? TurnResult.Failed)?.failure) }
        }
    }

    companion object {
        private const val KEY_AUTO_ASKED = "autoFirstQuestionAsked"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                val route = handle.toRoute<DiscussionRoute>()
                val c = appContainer()
                DiscussionViewModel(c.discussionRepository, c.bookRepository, route.discussionId, autoFirstQuestion = route.fresh, savedState = handle)
            }
        }
    }
}

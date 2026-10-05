package com.logiclinear.reading.data.repo

import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Discussion
import com.logiclinear.reading.data.db.nowMillis
import com.logiclinear.reading.data.remote.anthropic.AiChat
import com.logiclinear.reading.data.remote.anthropic.AiRequest
import com.logiclinear.reading.data.remote.anthropic.AiResult
import com.logiclinear.reading.data.remote.anthropic.ChatMessage
import com.logiclinear.reading.domain.DISCUSSION_MAX_TOKENS
import com.logiclinear.reading.domain.DiscussionMessage
import com.logiclinear.reading.domain.buildDiscussionSystem
import com.logiclinear.reading.domain.decodeMessages
import com.logiclinear.reading.domain.encodeMessages
import com.logiclinear.reading.domain.isClosingTurn
import com.logiclinear.reading.domain.toChatMessages
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant

/** 프롬프트 텍스트 공급자. res/raw 파일을 호출 시점에 읽는다(프롬프트는 리소스 파일, rules/ai-api.md). */
data class DiscussionPrompts(val role: () -> String, val start: () -> String, val close: () -> String)

sealed interface TurnResult {
    data object Ok : TurnResult
    data class Failed(val failure: AiResult.Failure) : TurnResult
}

/**
 * 완독 후 토론(요구사항 "AI 기능 > 완독 후 토론"). 매 턴 messagesJson을 즉시 저장해 강제 종료 뒤에도 이어간다.
 * 호출 지점은 [requestFirstQuestion]·[send]·[resend] 셋이고 모두 사용자 동작(토론 시작·전송·재전송)에서만 불린다.
 */
class DiscussionRepository(
    private val db: AppDatabase,
    private val aiChat: AiChat,
    private val prompts: DiscussionPrompts,
    private val clock: () -> Instant = ::nowMillis,
    /** 프롬프트 파일 읽기용. 테스트는 테스트 디스패처를 넣는다. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val dao get() = db.discussionDao()

    fun observeByBook(bookId: Long): Flow<List<Discussion>> = dao.observeByBook(bookId)

    fun observeById(id: Long): Flow<Discussion?> = dao.observeById(id)

    /** 새 토론 행. 첫 질문은 [requestFirstQuestion]이 따로 요청한다(실패해도 행은 남는다). */
    suspend fun start(bookId: Long): Long = dao.upsert(Discussion(bookId = bookId, messagesJson = "[]", startedAt = clock()))

    /** 사용자 입력 없이 첫 질문 요청(요구사항: 첫 메시지는 앱이 자동으로 요청). 이미 AI 메시지가 있으면 다시 묻지 않는다. */
    suspend fun requestFirstQuestion(discussionId: Long): TurnResult {
        val discussion = dao.getById(discussionId) ?: return TurnResult.Ok
        val messages = decodeMessages(discussion.messagesJson)
        if (messages.any { !it.isUser }) return TurnResult.Ok
        return callAndAppend(discussion, messages)
    }

    /** 전송: 사용자 메시지를 즉시 저장한 뒤 호출. 실패하면 그 메시지를 "전송 안 됨"으로 남긴다(요구사항 "예외 처리"). */
    suspend fun send(discussionId: Long, text: String): TurnResult {
        val discussion = dao.getById(discussionId) ?: return TurnResult.Ok
        if (discussion.endedAt != null || text.isBlank()) return TurnResult.Ok
        val messages = decodeMessages(discussion.messagesJson) + DiscussionMessage(ChatMessage.USER, text.trim(), clock().toEpochMilli())
        save(discussion, messages)
        return callAndAppend(discussion, messages)
    }

    /** 재전송: 마지막 AI 메시지 뒤의 실패 메시지만 다시 보낸다(그 앞의 failed는 데이터 오류라 건드리지 않는다). 끝난 토론은 무시. */
    suspend fun resend(discussionId: Long): TurnResult {
        val discussion = dao.getById(discussionId) ?: return TurnResult.Ok
        if (discussion.endedAt != null) return TurnResult.Ok
        val messages = decodeMessages(discussion.messagesJson)
        val lastAi = messages.indexOfLast { !it.isUser }
        if (messages.withIndex().none { (i, m) -> i > lastAi && m.failed }) return TurnResult.Ok
        val retried = messages.mapIndexed { i, m -> if (i > lastAi && m.failed) m.copy(failed = false) else m }
        save(discussion, retried)
        return callAndAppend(discussion, retried)
    }

    /**
     * 호출 → 성공이면 AI 메시지를 붙이고, 20턴째면 endedAt을 기록한다(요구사항 "20턴 도달 시 마무리").
     * 실패면 마지막 사용자 메시지(들)를 failed로 표시해 저장한다.
     */
    private suspend fun callAndAppend(discussion: Discussion, messages: List<DiscussionMessage>): TurnResult {
        val book = db.bookDao().getById(discussion.bookId) ?: return TurnResult.Ok
        val quotes = db.quoteDao().observeByBook(book.id).first()
        val closing = isClosingTurn(messages)
        // 프롬프트 raw 파일 읽기는 블로킹 I/O라 메인 스레드 밖에서.
        val (role, start, close) = withContext(ioDispatcher) { Triple(prompts.role(), prompts.start(), prompts.close()) }
        val request = AiRequest(
            system = buildDiscussionSystem(role, book, quotes),
            messages = toChatMessages(messages, start, if (closing) close else null),
            maxTokens = DISCUSSION_MAX_TOKENS,
        )
        return when (val result = aiChat.complete(request)) {
            is AiResult.Success -> {
                // 캐시 적중 확인용(T-003: Haiku는 접두부 4,096 토큰 미만이면 캐시되지 않는다). 토큰 수만 남기고 본문·키는 쓰지 않는다.
                result.usage?.let { u ->
                    Log.d(TAG, "usage in=${u.inputTokens} out=${u.outputTokens} cacheCreate=${u.cacheCreationInputTokens} cacheRead=${u.cacheReadInputTokens}")
                }
                val reply = DiscussionMessage(ChatMessage.ASSISTANT, result.text.trim(), clock().toEpochMilli())
                save(discussion.copy(endedAt = if (closing) clock() else discussion.endedAt), messages + reply)
                TurnResult.Ok
            }
            is AiResult.Failure -> {
                save(discussion, markTrailingUserFailed(messages))
                TurnResult.Failed(result)
            }
        }
    }

    /** 마지막 AI 메시지 뒤에 있는 사용자 메시지들을 실패로 표시한다. 첫 질문 요청(사용자 메시지 없음)이면 그대로. */
    private fun markTrailingUserFailed(messages: List<DiscussionMessage>): List<DiscussionMessage> {
        val lastAi = messages.indexOfLast { !it.isUser }
        return messages.mapIndexed { i, m -> if (i > lastAi && m.isUser) m.copy(failed = true) else m }
    }

    private suspend fun save(discussion: Discussion, messages: List<DiscussionMessage>) {
        dao.upsert(discussion.copy(messagesJson = encodeMessages(messages)))
    }

    private companion object {
        const val TAG = "Discussion"
    }
}

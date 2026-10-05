package com.logiclinear.reading.domain

import com.logiclinear.reading.data.remote.anthropic.ChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 요구사항 "확정된 결정": 사용자 최대 20턴(메시지 40개). */
const val MAX_USER_TURNS = 20

/**
 * Discussion.messagesJson 원소(요구사항 "데이터 모델 > Discussion": `[{role, content, at}]`).
 * [failed]는 전송 실패해 로컬에만 있는 사용자 메시지(요구사항 "예외 처리": 토론 중 네트워크 끊김 → 로컬 보관, 재전송).
 */
@Serializable
data class DiscussionMessage(
    val role: String,
    val content: String,
    val at: Long,
    val failed: Boolean = false,
) {
    val isUser: Boolean get() = role == ChatMessage.USER
}

private val discussionJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

fun encodeMessages(messages: List<DiscussionMessage>): String =
    discussionJson.encodeToString(ListSerializer(DiscussionMessage.serializer()), messages)

/** 깨진 JSON이면 빈 목록. 토론 하나가 화면을 죽이지 않게 한다. */
fun decodeMessages(json: String): List<DiscussionMessage> =
    runCatching { discussionJson.decodeFromString(ListSerializer(DiscussionMessage.serializer()), json) }.getOrDefault(emptyList())

/** 전송에 성공한 사용자 메시지 수 = 쓴 턴 수. 실패 메시지는 세지 않는다. */
fun userTurnCount(messages: List<DiscussionMessage>): Int = messages.count { it.isUser && !it.failed }

fun remainingTurns(messages: List<DiscussionMessage>): Int = (MAX_USER_TURNS - userTurnCount(messages)).coerceAtLeast(0)

/** 20번째 사용자 메시지가 들어간 상태인가(이번 응답이 마무리여야 한다). */
fun isClosingTurn(messages: List<DiscussionMessage>): Boolean = userTurnCount(messages) >= MAX_USER_TURNS

/**
 * API에 보낼 메시지 배열. 요구사항 "매 턴 메시지 배열 전체를 보내되 캐시된 접두부는 재과금되지 않는다".
 * - 첫 질문은 사용자 입력 없이 앱이 요청하므로 [startInstruction]을 숨은 첫 user 메시지로 둔다(화면·저장에는 없음).
 * - 실패(미전송) 메시지는 빼고 보낸다.
 * - [closingInstruction]이 있으면 마지막 user 메시지 뒤에 붙인다(20턴 마무리, 저장되는 본문에는 없음).
 */
fun toChatMessages(
    messages: List<DiscussionMessage>,
    startInstruction: String,
    closingInstruction: String? = null,
): List<ChatMessage> {
    val body = messages.filterNot { it.failed }.map { ChatMessage(it.role, it.content) }.toMutableList()
    body.add(0, ChatMessage(ChatMessage.USER, startInstruction))
    if (closingInstruction != null) {
        val last = body.lastIndex
        if (body[last].role == ChatMessage.USER) body[last] = body[last].copy(content = body[last].content + "\n\n" + closingInstruction)
    }
    return mergeAdjacentSameRole(body)
}

/** 같은 role이 연속되면 API가 거부하므로 합친다(예: 첫 질문 실패 뒤 사용자가 먼저 쓴 경우). */
private fun mergeAdjacentSameRole(list: List<ChatMessage>): List<ChatMessage> {
    val out = mutableListOf<ChatMessage>()
    list.forEach { m ->
        val prev = out.lastOrNull()
        if (prev != null && prev.role == m.role) out[out.lastIndex] = prev.copy(content = prev.content + "\n\n" + m.content) else out += m
    }
    return out
}

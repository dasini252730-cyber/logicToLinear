package com.logiclinear.reading.data.remote.anthropic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Anthropic Messages API(요구사항 "AI 기능": 앱에서 직접 호출, 중계 서버 없음).
 * 요청·응답 모양은 공식 문서의 Messages API를 따른다. 모델 ID는 [com.logiclinear.reading.data.prefs.AiModel].
 */
interface AnthropicApi {
    @Headers("anthropic-version: $API_VERSION", "content-type: application/json")
    @POST("v1/messages")
    suspend fun createMessage(@Header("x-api-key") apiKey: String, @Body request: MessagesRequest): MessagesResponse

    companion object {
        const val BASE_URL = "https://api.anthropic.com/"
        const val API_VERSION = "2023-06-01"
    }
}

/** system은 블록 배열로 보낸다. 토론은 [역할, 책 컨텍스트(cache_control)] 두 블록(rules/ai-api.md). */
@Serializable
data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: List<SystemBlock>,
    val messages: List<ChatMessage>,
    /** Sonnet 5는 생략하면 adaptive thinking이 켜져 사고 토큰이 과금된다(T-003). Haiku는 null로 생략. */
    val thinking: Thinking? = null,
)

@Serializable
data class SystemBlock(
    val text: String,
    val type: String = "text",
    @SerialName("cache_control") val cacheControl: CacheControl? = null,
)

@Serializable
data class CacheControl(val type: String = "ephemeral")

@Serializable
data class Thinking(val type: String = "disabled")

/** role은 "user" 또는 "assistant". 토론 messagesJson도 이 모양을 쓴다(T-702). */
@Serializable
data class ChatMessage(val role: String, val content: String) {
    companion object {
        const val USER = "user"
        const val ASSISTANT = "assistant"
    }
}

@Serializable
data class MessagesResponse(
    val id: String? = null,
    val model: String? = null,
    val content: List<ContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: Usage? = null,
) {
    /** 텍스트 블록만 이어 붙인다. */
    fun text(): String = content.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("")
}

@Serializable
data class ContentBlock(val type: String, val text: String? = null)

/** 캐시 적중 확인용(T-003: Haiku는 접두부 4,096 토큰 미만이면 캐시되지 않는다). */
@Serializable
data class Usage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    @SerialName("cache_creation_input_tokens") val cacheCreationInputTokens: Int = 0,
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Int = 0,
)

@Serializable
data class ErrorResponse(val error: ErrorBody? = null)

@Serializable
data class ErrorBody(val type: String? = null, val message: String? = null)

/** null 필드(cache_control, thinking)는 보내지 않는다. 모르는 응답 키는 무시한다. */
val anthropicJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

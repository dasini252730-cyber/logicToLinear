package com.logiclinear.reading.data.remote.anthropic

import com.logiclinear.reading.data.prefs.AiModel
import com.logiclinear.reading.data.secret.InMemorySecretStore
import com.logiclinear.reading.data.secret.SecretKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnthropicClientTest {
    private lateinit var server: MockWebServer
    private var model = AiModel.HAIKU
    private var calls = 0
    private val secretStore = InMemorySecretStore(mapOf(SecretKey.ANTHROPIC_API to "sk-test-key"))

    private val request = AiRequest(
        system = listOf(
            SystemBlock(text = "역할 프롬프트"),
            SystemBlock(text = "책 컨텍스트", cacheControl = CacheControl()),
        ),
        messages = listOf(ChatMessage(ChatMessage.USER, "첫 질문을 해 줘")),
        maxTokens = 400,
    )

    private val okBody = """{"id":"msg_1","model":"claude-haiku-4-5","content":[{"type":"text","text":"안녕"},{"type":"text","text":"하세요"}],
        "stop_reason":"end_turn","usage":{"input_tokens":120,"output_tokens":8,"cache_creation_input_tokens":0,"cache_read_input_tokens":100}}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun client() = AnthropicClient.create(
        secretStore, modelProvider = { model }, onCall = { calls++ }, baseUrl = server.url("/").toString(), retryDelayMs = 0,
    )

    @Test
    fun 요청_JSON에_모델_max_tokens_system_cache_control이_실리고_헤더가_맞다() = runTest {
        server.enqueue(MockResponse(body = okBody))

        val result = client().complete(request) as AiResult.Success

        assertEquals("안녕하세요", result.text)
        assertEquals(100, result.usage?.cacheReadInputTokens)
        assertEquals("end_turn", result.stopReason)
        val recorded = server.takeRequest()
        assertEquals("/v1/messages", recorded.url.encodedPath)
        assertEquals("sk-test-key", recorded.headers["x-api-key"])
        assertEquals(AnthropicApi.API_VERSION, recorded.headers["anthropic-version"])
        val body = Json.parseToJsonElement(recorded.body!!.utf8()).jsonObject
        assertEquals("claude-haiku-4-5", body["model"]!!.jsonPrimitive.content)
        assertEquals(400, body["max_tokens"]!!.jsonPrimitive.content.toInt())
        val system = body["system"]!!.toString()
        assertTrue(system.contains("""{"text":"책 컨텍스트","type":"text","cache_control":{"type":"ephemeral"}}"""))
        assertTrue(system.contains("""{"text":"역할 프롬프트","type":"text"}""")) // null cache_control은 생략
        assertNull(body["thinking"]) // Haiku는 thinking 생략
        assertNull(body["temperature"])
        assertEquals(1, calls)
    }

    @Test
    fun Sonnet이면_thinking_disabled를_명시한다() = runTest {
        model = AiModel.SONNET
        server.enqueue(MockResponse(body = okBody))

        client().complete(request)

        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("claude-sonnet-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("disabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun 키가_없으면_호출하지_않고_NoKey() = runTest {
        val result = AnthropicClient.create(InMemorySecretStore(), { model }, { calls++ }, baseUrl = server.url("/").toString()).complete(request)
        assertEquals(AiResult.NoKey, result)
        assertEquals(0, server.requestCount)
        assertEquals(0, calls)
    }

    @Test
    fun HTTP_401은_재시도_없이_InvalidKey() = runTest {
        server.enqueue(MockResponse(code = 401, body = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))

        assertEquals(AiResult.InvalidKey, client().complete(request))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun HTTP_5xx는_한_번만_재시도하고_두_번째_성공을_돌려준다() = runTest {
        server.enqueue(MockResponse(code = 529, body = """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""))
        server.enqueue(MockResponse(body = okBody))

        val result = client().complete(request)

        assertTrue(result is AiResult.Success)
        assertEquals(2, server.requestCount)
        assertEquals(1, calls) // 재시도는 호출 횟수에 더하지 않는다
    }

    @Test
    fun 두_번_연속_실패하면_세_번째는_시도하지_않는다() = runTest {
        server.enqueue(MockResponse(code = 500))
        server.enqueue(MockResponse(code = 503))
        server.enqueue(MockResponse(body = okBody)) // 소비되지 않아야 한다

        val result = client().complete(request)

        assertEquals(AiResult.ServerError(503), result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun 네트워크_끊김은_재시도_뒤_Network로_끝나고_429는_RateLimited() = runTest {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        assertTrue(client().complete(request) is AiResult.Network)
        assertEquals(2, server.requestCount)

        val before = server.requestCount
        server.enqueue(MockResponse(code = 429))
        assertEquals(AiResult.RateLimited, client().complete(request))
        assertEquals(before + 1, server.requestCount) // 429는 자동 재시도하지 않는다
    }

    @Test
    fun 텍스트가_빈_200_응답은_Incomplete로_끝난다() = runTest {
        server.enqueue(MockResponse(body = """{"id":"m","content":[],"stop_reason":"end_turn"}"""))
        assertEquals(AiResult.Incomplete("end_turn"), client().complete(request))
    }

    @Test
    fun 그_밖의_4xx는_메시지를_담아_Rejected로_끝내고_재시도하지_않는다() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"type":"error","error":{"type":"invalid_request_error","message":"max_tokens too large"}}"""))

        val result = client().complete(request)

        assertEquals(AiResult.Rejected(400, "max_tokens too large"), result)
        assertEquals(1, server.requestCount)
        assertFalse((result as AiResult.Failure) is AiResult.Network)
    }
}

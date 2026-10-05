package com.logiclinear.reading.data.remote.anthropic

import com.logiclinear.reading.data.prefs.AiModel
import com.logiclinear.reading.data.secret.SecretKey
import com.logiclinear.reading.data.secret.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 한 번의 호출 입력. 모델은 클라이언트가 설정값에서 고른다. */
data class AiRequest(
    val system: List<SystemBlock>,
    val messages: List<ChatMessage>,
    /** 토론 400, 분석 2000(요구사항). */
    val maxTokens: Int,
)

/** 호출 결과. 실패 종류별 사용자 문구는 ui/ai/AiErrorMessage.kt가 맡는다(요구사항 "예외 처리"). */
sealed interface AiResult {
    data class Success(val text: String, val usage: Usage?, val stopReason: String?) : AiResult

    sealed interface Failure : AiResult

    /** 설정에 Anthropic 키가 없다 → 설정 화면으로 안내. */
    data object NoKey : Failure

    /** 401: 키가 틀렸거나 만료 → 설정 화면으로 안내. */
    data object InvalidKey : Failure

    /** 429: 한도 초과. 잠시 뒤 재시도. */
    data object RateLimited : Failure

    /** 5xx·529(overloaded). 재시도 버튼. */
    data class ServerError(val code: Int) : Failure

    /** 오프라인·타임아웃. 재시도 버튼. */
    data class Network(val cause: Throwable) : Failure

    /** 그 밖의 4xx(잘못된 요청 등). 재시도해도 같으므로 메시지만 보여준다. */
    data class Rejected(val code: Int, val message: String?) : Failure
}

/** 호출 추상화. ViewModel·Repository 테스트는 가짜 구현을 넣는다. */
interface AiChat {
    suspend fun complete(request: AiRequest): AiResult
}

/**
 * Messages API 클라이언트. 키는 호출 직전 [SecretStore]에서 읽고, 모델은 [modelProvider]에서 고른다.
 * 자동 재시도는 네트워크·5xx·429에 한해 1회만(요구사항 "비용 통제"). 호출마다 [onCall]로 월별 카운터를 올린다.
 */
class AnthropicClient(
    private val api: AnthropicApi,
    private val secretStore: SecretStore,
    private val modelProvider: () -> AiModel,
    private val onCall: () -> Unit = {},
    private val retryDelayMs: Long = RETRY_DELAY_MS,
) : AiChat {
    override suspend fun complete(request: AiRequest): AiResult {
        val key = withContext(Dispatchers.IO) { secretStore.get(SecretKey.ANTHROPIC_API) } ?: return AiResult.NoKey
        val model = modelProvider()
        val body = MessagesRequest(
            model = model.id,
            maxTokens = request.maxTokens,
            system = request.system,
            messages = request.messages,
            thinking = if (model == AiModel.SONNET) Thinking() else null,
        )
        onCall() // 사용자 동작 1회 = 호출 1회로 센다. 자동 재시도는 따로 세지 않는다.
        val first = attempt(key, body)
        if (!first.isRetryable()) return first
        delay(retryDelayMs)
        return attempt(key, body)
    }

    private suspend fun attempt(key: String, body: MessagesRequest): AiResult = try {
        val response = api.createMessage(key, body)
        AiResult.Success(response.text(), response.usage, response.stopReason)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        mapHttpError(e)
    } catch (e: IOException) {
        AiResult.Network(e)
    } catch (e: Exception) {
        // 직렬화 오류 등. 응답을 읽을 수 없으면 네트워크 계열로 묶어 재시도 버튼을 보여준다.
        AiResult.Network(e)
    }

    private fun mapHttpError(e: HttpException): AiResult {
        val code = e.code()
        val message = runCatching {
            e.response()?.errorBody()?.string()?.let { anthropicJson.decodeFromString(ErrorResponse.serializer(), it) }?.error?.message
        }.getOrNull()
        return when {
            code == 401 -> AiResult.InvalidKey
            code == 429 -> AiResult.RateLimited
            code >= 500 -> AiResult.ServerError(code)
            else -> AiResult.Rejected(code, message)
        }
    }

    private fun AiResult.isRetryable() = this is AiResult.Network || this is AiResult.ServerError || this is AiResult.RateLimited

    companion object {
        const val RETRY_DELAY_MS = 1_500L

        fun create(
            secretStore: SecretStore,
            modelProvider: () -> AiModel,
            onCall: () -> Unit,
            baseUrl: String = AnthropicApi.BASE_URL,
            client: OkHttpClient = defaultHttpClient(),
            retryDelayMs: Long = RETRY_DELAY_MS,
        ): AnthropicClient {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(anthropicJson.asConverterFactory("application/json".toMediaType()))
                .build()
            return AnthropicClient(retrofit.create(AnthropicApi::class.java), secretStore, modelProvider, onCall, retryDelayMs)
        }

        /** 분석은 max_tokens 2000이라 응답이 길다. 읽기 60초, 호출 전체 90초. 로깅 인터셉터는 두지 않는다(키·글귀 노출 방지). */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}

package com.logiclinear.reading.data.remote.aladin

import com.logiclinear.reading.data.secret.SecretKey
import com.logiclinear.reading.data.secret.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 검색 결과. UI는 실패 종류에 따라 직접 입력 폼으로 넘어간다(요구사항 "예외 처리"). */
sealed interface AladinResult {
    data class Found(val items: List<AladinItem>) : AladinResult

    /** 정상 응답인데 결과가 0건. */
    data object Empty : AladinResult

    /** 설정에 TTB 키가 없다. 설정 화면으로 안내한다. */
    data object NoKey : AladinResult

    /** 알라딘이 오류 코드를 돌려줬다(키 오류, 일 호출 한도 초과 등). */
    data class ApiError(val code: Int?, val message: String?) : AladinResult

    /** 오프라인·타임아웃·서버 오류. */
    data class Network(val cause: Throwable) : AladinResult
}

/** 검색 추상화. ViewModel 테스트는 가짜 구현을 넣는다. */
interface AladinSearch {
    suspend fun searchByTitle(title: String): AladinResult
}

/**
 * 알라딘 검색 클라이언트. TTB 키는 호출 직전에 [SecretStore]에서 읽는다(요구사항: 코드에 키를 두지 않음).
 * 네트워크 호출은 Repository/클라이언트 안에서만(rules/android.md).
 */
class AladinClient(
    private val api: AladinApi,
    private val secretStore: SecretStore,
) : AladinSearch {
    override suspend fun searchByTitle(title: String): AladinResult {
        // EncryptedSharedPreferences 첫 접근은 Keystore 초기화가 있어 메인 스레드에서 읽지 않는다.
        val key = withContext(Dispatchers.IO) { secretStore.get(SecretKey.ALADIN_TTB) } ?: return AladinResult.NoKey
        val query = title.trim()
        if (query.isEmpty()) return AladinResult.Empty
        return try {
            val response = api.itemSearch(ttbKey = key, query = query)
            when {
                response.errorCode != null -> AladinResult.ApiError(response.errorCode, response.errorMessage)
                response.item.isEmpty() -> AladinResult.Empty
                else -> AladinResult.Found(response.item)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AladinResult.Network(e)
        } catch (e: Exception) {
            // HTTP 오류(retrofit2.HttpException)·직렬화 오류 모두 네트워크 계열로 묶어 직접 입력 폼으로 보낸다.
            AladinResult.Network(e)
        }
    }

    companion object {
        val json: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        fun create(secretStore: SecretStore, baseUrl: String = AladinApi.BASE_URL, client: OkHttpClient = defaultHttpClient()): AladinClient {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return AladinClient(retrofit.create(AladinApi::class.java), secretStore)
        }

        /** callTimeout으로 호출 전체를 묶어 느리게 흘러오는 응답에도 20초 안에 끝나게 한다(검색 스피너가 멈추지 않도록). */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(AladinJsonCleanupInterceptor())
            .build()
    }
}

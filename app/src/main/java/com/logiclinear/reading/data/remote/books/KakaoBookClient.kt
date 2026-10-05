package com.logiclinear.reading.data.remote.books

import com.logiclinear.reading.data.secret.SecretKey
import com.logiclinear.reading.data.secret.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 카카오 책 검색 클라이언트. REST API 키는 호출 직전에 [SecretStore]에서 읽는다(요구사항: 코드에 키를 두지 않음).
 * 네트워크 호출은 Repository/클라이언트 안에서만(rules/android.md).
 */
class KakaoBookClient(
    private val api: KakaoBookApi,
    private val secretStore: SecretStore,
) : BookSearch {
    override suspend fun searchByTitle(title: String): BookSearchResult {
        // EncryptedSharedPreferences 첫 접근은 Keystore 초기화가 있어 메인 스레드에서 읽지 않는다.
        val key = withContext(Dispatchers.IO) { secretStore.get(SecretKey.KAKAO_REST) } ?: return BookSearchResult.NoKey
        val query = title.trim()
        if (query.isEmpty()) return BookSearchResult.Empty
        return try {
            val response = api.search(KakaoBookApi.AUTH_PREFIX + key, query)
            val items = response.documents.map { it.toItem() }.filter { it.title.isNotBlank() }
            if (items.isEmpty()) BookSearchResult.Empty else BookSearchResult.Found(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            mapHttpError(e)
        } catch (e: IOException) {
            BookSearchResult.Network(e)
        } catch (e: Exception) {
            // 직렬화 오류 등. 응답을 읽을 수 없으면 네트워크 계열로 묶어 직접 입력 폼으로 보낸다.
            BookSearchResult.Network(e)
        }
    }

    /** 401·403은 키 문제(설정 안내), 429(한도 초과)·400 등은 ApiError(폴백), 5xx는 Network(폴백). */
    private fun mapHttpError(e: HttpException): BookSearchResult {
        val code = e.code()
        val message = runCatching {
            e.response()?.errorBody()?.string()?.let { json.decodeFromString(KakaoErrorResponse.serializer(), it) }?.message
        }.getOrNull()
        return when {
            code == 401 || code == 403 -> BookSearchResult.InvalidKey
            code >= 500 -> BookSearchResult.Network(e)
            else -> BookSearchResult.ApiError(code.toString(), message)
        }
    }

    companion object {
        val json: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun create(secretStore: SecretStore, baseUrl: String = KakaoBookApi.BASE_URL, client: OkHttpClient = defaultHttpClient()): KakaoBookClient {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return KakaoBookClient(retrofit.create(KakaoBookApi::class.java), secretStore)
        }

        /** callTimeout으로 호출 전체를 20초 안에 끝낸다(검색 스피너가 멈추지 않도록). 로깅 인터셉터는 두지 않는다(키 노출 방지). */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

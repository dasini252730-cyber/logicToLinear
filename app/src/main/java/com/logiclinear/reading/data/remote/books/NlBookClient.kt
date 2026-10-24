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
 * 국립중앙도서관 소장자료 검색 클라이언트. 인증키는 호출 직전에 [SecretStore]에서 읽는다(요구사항: 코드에 키를 두지 않음).
 * 네트워크 호출은 Repository/클라이언트 안에서만(rules/android.md). 키가 URL 쿼리로 나가므로 로깅 인터셉터를 두지 않는다.
 */
class NlBookClient(
    private val api: NlBookApi,
    private val secretStore: SecretStore,
) : BookSearch {
    override suspend fun searchByTitle(title: String): BookSearchResult {
        // EncryptedSharedPreferences 첫 접근은 Keystore 초기화가 있어 메인 스레드에서 읽지 않는다.
        val key = withContext(Dispatchers.IO) { secretStore.get(SecretKey.NL_OPENAPI) } ?: return BookSearchResult.NoKey
        val query = title.trim()
        if (query.isEmpty()) return BookSearchResult.Empty
        return try {
            mapResponse(api.search(key, query))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) BookSearchResult.InvalidKey else BookSearchResult.Network(e)
        } catch (e: IOException) {
            BookSearchResult.Network(e)
        } catch (e: Exception) {
            // 직렬화 오류 등. 응답을 읽을 수 없으면 네트워크 계열로 묶어 직접 입력 폼으로 보낸다.
            BookSearchResult.Network(e)
        }
    }

    /**
     * 오류는 HTTP 200 본문의 errorCode로 온다. 010·011 → InvalidKey, 000·101 → Network, 그 외(012~015) → ApiError.
     * 응답 키 표기가 예상과 다르면(result 없음) Empty로 오진하지 않고 ApiError("shape")로 보내 로그에 남긴다(T-307 실기기 확인용).
     */
    private fun mapResponse(response: NlSearchResponse): BookSearchResult {
        val code = response.errorCode?.takeIf { it.isNotBlank() }?.let(NlBookApi::normalizeCode)
        if (code != null) {
            return when {
                NlBookApi.isKeyError(code) -> BookSearchResult.InvalidKey
                NlBookApi.isServerError(code) -> BookSearchResult.Network(IOException("NL $code: ${response.errorMsg}"))
                else -> BookSearchResult.ApiError(code, response.errorMsg)
            }
        }
        if (response.looksMalformed) {
            // 로그 대신 메시지에 단서를 담는다(순수 JVM 테스트에서 android.util.Log를 못 쓴다). 화면은 직접 입력 폼으로 넘어간다.
            return BookSearchResult.ApiError("shape", "응답 형식이 예상과 다름 (result=${response.result?.size}, total=${response.total})")
        }
        // 같은 판본(isbn13)은 하나로. isbn이 없으면 제목·저자·발행자가 모두 같을 때만 합친다(요구사항: 발행자로 판본 구분).
        val items = response.result.orEmpty()
            .map { it.toItem() }
            .filter { it.title.isNotBlank() }
            .distinctBy { it.isbn13.ifEmpty { listOf(it.title, it.author, it.publisher).joinToString("|") } }
        return if (items.isEmpty()) BookSearchResult.Empty else BookSearchResult.Found(items)
    }

    companion object {
        val json: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        fun create(secretStore: SecretStore, baseUrl: String = NlBookApi.BASE_URL, client: OkHttpClient = defaultHttpClient()): NlBookClient {
            // 서버는 Content-Type을 text/json으로 준다. Retrofit은 응답 컨버터를 미디어 타입으로 고르지 않으므로 그대로 파싱된다.
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return NlBookClient(retrofit.create(NlBookApi::class.java), secretStore)
        }

        /** callTimeout으로 호출 전체를 20초 안에 끝낸다(검색 스피너가 멈추지 않도록). 로깅 인터셉터는 두지 않는다(키 노출 방지). */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

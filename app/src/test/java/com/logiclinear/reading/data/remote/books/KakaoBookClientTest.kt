package com.logiclinear.reading.data.remote.books

import com.logiclinear.reading.data.secret.InMemorySecretStore
import com.logiclinear.reading.data.secret.SecretKey
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KakaoBookClientTest {
    private lateinit var server: MockWebServer
    private val secretStore = InMemorySecretStore(mapOf(SecretKey.KAKAO_REST to "kakao-key"))

    private val sample = """{"meta":{"total_count":3,"pageable_count":3,"is_end":true},"documents":[
        {"title":"채식주의자","contents":"소개글","url":"https://search.daum.net/book/1","isbn":"8936433598 9788936433598",
         "datetime":"2007-10-30T00:00:00.000+09:00","authors":["한강"],"publisher":"창비","translators":[],"price":12000,"sale_price":10800,
         "thumbnail":"https://img/cover.jpg","status":"정상판매"},
        {"title":"저자 둘","contents":"","url":"","isbn":"","authors":["김훈","박완서"],"publisher":"","translators":["옮긴이"],"thumbnail":""},
        {"title":"ISBN10만 있는 책","contents":"","url":"","isbn":"8936433598","authors":[],"publisher":"","translators":[],"thumbnail":""}
    ]}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun client(store: InMemorySecretStore = secretStore) = KakaoBookClient.create(store, baseUrl = server.url("/").toString())

    @Test
    fun 제목_검색은_헤더와_파라미터를_맞추고_저장_필드를_파싱한다() = runTest {
        server.enqueue(MockResponse(body = sample))

        val result = client().searchByTitle("  채식주의자 ") as BookSearchResult.Found

        val recorded = server.takeRequest()
        assertEquals("/v3/search/book", recorded.url.encodedPath)
        assertEquals("채식주의자", recorded.url.queryParameter("query"))
        assertEquals("title", recorded.url.queryParameter("target"))
        assertEquals("10", recorded.url.queryParameter("size"))
        assertEquals("KakaoAK kakao-key", recorded.headers["Authorization"])

        val first = result.items[0]
        assertEquals("채식주의자", first.title)
        assertEquals("한강", first.author)
        assertEquals("창비", first.publisher)
        assertEquals("9788936433598", first.isbn13) // "ISBN10 ISBN13"에서 13자리만
        assertEquals("https://img/cover.jpg", first.cover)
        assertEquals("소개글", first.description)
        assertEquals("https://search.daum.net/book/1", first.link)
        assertEquals("", first.category) // 카카오는 분류를 주지 않는다

        val second = result.items[1]
        assertEquals("김훈, 박완서", second.author)
        assertEquals("", second.isbn13)

        // 경계값: ISBN10만 오면 isbn13은 빈 값(중복 검사는 Repository가 건너뛴다)
        assertEquals("", result.items[2].isbn13)
        assertEquals("", result.items[2].author)
    }

    @Test
    fun 결과_없음과_빈_검색어와_키_없음() = runTest {
        server.enqueue(MockResponse(body = """{"meta":{"total_count":0,"pageable_count":0,"is_end":true},"documents":[]}"""))
        assertEquals(BookSearchResult.Empty, client().searchByTitle("zzzz"))

        assertEquals(BookSearchResult.Empty, client().searchByTitle("   "))
        assertEquals(BookSearchResult.NoKey, client(InMemorySecretStore()).searchByTitle("책"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun 키_오류는_InvalidKey_한도_초과는_ApiError_서버_오류와_끊김은_Network() = runTest {
        server.enqueue(MockResponse(code = 401, body = """{"errorType":"AccessDeniedError","message":"wrong app key"}"""))
        assertEquals(BookSearchResult.InvalidKey, client().searchByTitle("책"))

        server.enqueue(MockResponse(code = 429, body = """{"errorType":"RequestThrottled","message":"API limit has been exceeded."}"""))
        assertEquals(BookSearchResult.ApiError("429", "API limit has been exceeded."), client().searchByTitle("책"))

        server.enqueue(MockResponse(code = 503))
        assertTrue(client().searchByTitle("책") is BookSearchResult.Network)

        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        assertTrue(client().searchByTitle("책") is BookSearchResult.Network)
    }

    @Test
    fun 깨진_JSON은_Network로_묶여_직접_입력_폼으로_간다() = runTest {
        server.enqueue(MockResponse(body = "<html>점검 중</html>"))
        assertTrue(client().searchByTitle("책") is BookSearchResult.Network)
    }
}

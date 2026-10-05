package com.logiclinear.reading.data.remote.aladin

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

class AladinClientTest {
    private lateinit var server: MockWebServer
    private val secrets = InMemorySecretStore(mapOf(SecretKey.ALADIN_TTB to "ttbtest"))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun client(store: InMemorySecretStore = secrets) = AladinClient.create(store, baseUrl = server.url("/").toString())

    @Test
    fun 정상_응답은_저장_필드_7개를_파싱하고_파라미터를_요구사항대로_보낸다() = runTest {
        server.enqueue(MockResponse(body = SAMPLE_JSON))

        val result = client().searchByTitle("채식주의자")

        val found = result as AladinResult.Found
        val item = found.items.single()
        assertEquals("채식주의자", item.title)
        assertEquals("한강 (지은이)", item.author)
        assertEquals("창비", item.publisher)
        assertEquals("9788936433598", item.isbn13)
        assertTrue(item.cover.startsWith("https://"))
        assertEquals("국내도서>소설/시/희곡>한국소설", item.categoryName)
        assertEquals("소개글", item.description)

        val request = server.takeRequest()
        val query = request.url
        assertEquals("ttbtest", query.queryParameter("ttbkey"))
        assertEquals("채식주의자", query.queryParameter("Query"))
        assertEquals("Title", query.queryParameter("QueryType"))
        assertEquals("10", query.queryParameter("MaxResults"))
        assertEquals("Big", query.queryParameter("Cover"))
        assertEquals("js", query.queryParameter("output"))
        assertEquals(setOf("ttbkey", "Query", "QueryType", "MaxResults", "Cover", "output", "Version"), query.queryParameterNames)
    }

    @Test
    fun 끝_세미콜론이나_콜백_래퍼가_붙은_비표준_JSON도_파싱된다() = runTest {
        server.enqueue(MockResponse(body = "$SAMPLE_JSON;"))
        assertTrue(client().searchByTitle("채식주의자") is AladinResult.Found)

        server.enqueue(MockResponse(body = "callback($SAMPLE_JSON);"))
        assertTrue(client().searchByTitle("채식주의자") is AladinResult.Found)

        assertEquals("""{"a":1}""", cleanAladinJson("""  {"a":1} ; ;"""))
        assertEquals("""[1,2]""", cleanAladinJson("""cb ( [1,2] )"""))
        assertEquals("""{"a":"(x)"}""", cleanAladinJson("""{"a":"(x)"}"""))
    }

    @Test
    fun null_필드는_기본값으로_강제된다() = runTest {
        server.enqueue(MockResponse(body = """{"totalResults":1,"item":[{"title":"제목","author":null,"description":null,"isbn13":null}]}"""))
        val item = (client().searchByTitle("제목") as AladinResult.Found).items.single()
        assertEquals("", item.author)
        assertEquals("", item.description)
        assertEquals("", item.isbn13)
    }

    @Test
    fun 결과가_없으면_Empty() = runTest {
        server.enqueue(MockResponse(body = """{"totalResults":0,"item":[]}"""))
        assertEquals(AladinResult.Empty, client().searchByTitle("없는 책"))
    }

    @Test
    fun 알라딘_오류_코드는_ApiError() = runTest {
        server.enqueue(MockResponse(body = """{"errorCode":100,"errorMessage":"잘못된 TTBKey입니다."}"""))
        val result = client().searchByTitle("책")
        assertEquals(AladinResult.ApiError(100, "잘못된 TTBKey입니다."), result)
    }

    @Test
    fun 키가_없으면_호출하지_않고_NoKey() = runTest {
        assertEquals(AladinResult.NoKey, client(InMemorySecretStore()).searchByTitle("책"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun 빈_검색어는_호출하지_않고_Empty() = runTest {
        assertEquals(AladinResult.Empty, client().searchByTitle("   "))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun 네트워크_끊김과_서버_오류는_Network() = runTest {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        assertTrue(client().searchByTitle("책") is AladinResult.Network)

        server.enqueue(MockResponse(code = 503))
        assertTrue(client().searchByTitle("책") is AladinResult.Network)
    }

    private companion object {
        val SAMPLE_JSON = """
            {"version":"20131101","totalResults":1,"startIndex":1,"itemsPerPage":10,
             "item":[{"title":"채식주의자","link":"https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=1","author":"한강 (지은이)",
                      "pubDate":"2007-10-30","description":"소개글","isbn":"8936433598","isbn13":"9788936433598",
                      "cover":"https://image.aladin.co.kr/product/cover.jpg","categoryId":50993,
                      "categoryName":"국내도서>소설/시/희곡>한국소설","publisher":"창비","unknownField":123}]}
        """.trimIndent()
    }
}

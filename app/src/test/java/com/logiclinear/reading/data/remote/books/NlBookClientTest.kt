package com.logiclinear.reading.data.remote.books

import com.logiclinear.reading.data.secret.InMemorySecretStore
import com.logiclinear.reading.data.secret.SecretKey
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NlBookClientTest {
    private lateinit var server: MockWebServer
    private val secretStore = InMemorySecretStore(mapOf(SecretKey.NL_OPENAPI to "nl-key"))

    /** 공식 표의 snake_case 표기. 검색어 강조 태그와 "지음" 꼬리, 세트 ISBN을 섞었다. */
    private val snakeSample = """{"total":"3","kwd":"토지","result":[
        {"title_info":"<span class=\"searching_txt\">토지</span> : 박경리 대하소설. 1","author_info":"박경리 지음","pub_info":"마로니에북스",
         "pub_year_info":"2012","isbn":"9788960531963","type_name":"도서","detail_link":"/NL/contents/search.do?id=1","kdc_name_1s":"문학","call_no":"813.6-박14토"},
        {"title_info":"토지 세트","author_info":"박경리 지음 ; 김훈 옮김","pub_info":"","pub_year_info":"","isbn":"8936433598 9788936433598 (세트)","type_name":"도서","detail_link":"","kdc_name_1s":""},
        {"title_info":"토지 세트","author_info":"박경리, 김훈","pub_info":"","isbn":"9788936433598","type_name":"도서","detail_link":"https://www.nl.go.kr/x","kdc_name_1s":""}
    ]}"""

    /** 오류 응답과 같은 camelCase 표기도 받는다(실호출 확인 전 보험). */
    private val camelSample = """{"total":1,"result":[{"titleInfo":"채식주의자","authorInfo":"한강 저","pubInfo":"창비","isbn":"9788936433598","detailLink":"/NL/contents/search.do?id=2","kdcName1s":"문학"}]}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun client(store: InMemorySecretStore = secretStore) = NlBookClient.create(store, baseUrl = server.url("/").toString())

    @Test
    fun 제목_검색은_파라미터를_맞추고_태그와_역할_꼬리를_떼고_중복을_합친다() = runTest {
        server.enqueue(MockResponse(body = snakeSample, headers = headersOf("Content-Type", "text/json;charset=UTF-8")))

        val result = client().searchByTitle("  토지 ") as BookSearchResult.Found

        val recorded = server.takeRequest()
        assertEquals("/NL/search/openApi/search.do", recorded.url.encodedPath)
        assertEquals("nl-key", recorded.url.queryParameter("key"))
        assertEquals("토지", recorded.url.queryParameter("kwd"))
        assertEquals("title", recorded.url.queryParameter("srchTarget"))
        assertEquals("json", recorded.url.queryParameter("apiType"))
        assertEquals("1", recorded.url.queryParameter("pageNum"))
        assertEquals("10", recorded.url.queryParameter("pageSize"))
        assertEquals("도서", recorded.url.queryParameter("category"))

        val first = result.items[0]
        assertEquals("토지 : 박경리 대하소설. 1", first.title)
        assertEquals("박경리", first.author)
        assertEquals("마로니에북스", first.publisher)
        assertEquals("9788960531963", first.isbn13)
        assertEquals("문학", first.category)
        assertEquals("https://www.nl.go.kr/NL/contents/search.do?id=1", first.link)
        assertEquals("", first.cover) // 표지는 없다(포기한 기능)
        assertEquals("", first.description)

        // 세트 ISBN "ISBN10 ISBN13 (세트)"에서 13자리만, 같은 isbn13 두 건은 하나로
        assertEquals(2, result.items.size)
        assertEquals("9788936433598", result.items[1].isbn13)
        assertEquals("박경리, 김훈", result.items[1].author)
    }

    @Test
    fun 저자_꼬리는_공백_뒤에서만_떼고_isbn13은_978_979로_시작하는_것만_받는다() {
        assertEquals("김저", "김저".removeRoleSuffix())
        assertEquals("구글", "구글".removeRoleSuffix())
        assertEquals("김영글", "김영글".removeRoleSuffix())
        assertEquals("이문열", "이문열 지음".removeRoleSuffix())
        assertEquals("이문구", "이문구 共著".removeRoleSuffix())
        assertEquals("박경리, 김훈", "박경리 지음 ; 김훈 옮김".removeRoleSuffix())
        assertEquals("", "0212345678901".extractIsbn13()) // 13자리지만 ISBN 접두가 아니면 버린다
        assertEquals("9791162540640", "979-11-6254-064-0".extractIsbn13())
    }

    @Test
    fun camelCase_응답도_같은_필드로_읽는다() = runTest {
        server.enqueue(MockResponse(body = camelSample))
        val item = (client().searchByTitle("채식주의자") as BookSearchResult.Found).items.single()
        assertEquals("채식주의자", item.title)
        assertEquals("한강", item.author)
        assertEquals("창비", item.publisher)
        assertEquals("문학", item.category)
        assertEquals("", item.cover)
        assertEquals("https://www.nl.go.kr/NL/contents/search.do?id=2", item.link)
    }

    @Test
    fun 응답에_result_키가_없거나_total만_있으면_결과_없음이_아니라_형식_오류다() = runTest {
        server.enqueue(MockResponse(body = """{"total":"5","kwd":"토지","docs":[{"titleInfo":"토지"}]}"""))
        val shape = client().searchByTitle("토지") as BookSearchResult.ApiError
        assertEquals("shape", shape.code)
        assertEquals("응답 형식이 예상과 다름 (result=null, total=5)", shape.message)

        server.enqueue(MockResponse(body = """{"kwd":"토지"}"""))
        assertEquals("shape", (client().searchByTitle("토지") as BookSearchResult.ApiError).code)

        // total이 0이면 정상적인 "결과 없음"
        server.enqueue(MockResponse(body = """{"total":0,"kwd":"zzzz","result":[]}"""))
        assertEquals(BookSearchResult.Empty, client().searchByTitle("zzzz"))
    }

    @Test
    fun errorCode가_숫자로_와도_키_오류로_읽고_isbn이_없으면_발행자까지_같을_때만_합친다() = runTest {
        server.enqueue(MockResponse(body = """{"errorCode":11,"errorMsg":"INVALID KEY"}"""))
        assertEquals(BookSearchResult.InvalidKey, client().searchByTitle("책"))

        server.enqueue(MockResponse(body = """{"total":"2","result":[
            {"title_info":"토지","author_info":"박경리 著","pub_info":"솔","isbn":""},
            {"title_info":"토지","author_info":"박경리 지음","pub_info":"나남","isbn":""}]}"""))
        val items = (client().searchByTitle("토지") as BookSearchResult.Found).items
        assertEquals(2, items.size)
        assertEquals(listOf("박경리", "박경리"), items.map { it.author })
        assertEquals(listOf("솔", "나남"), items.map { it.publisher })
    }

    @Test
    fun 결과_없음과_빈_검색어와_키_없음() = runTest {
        server.enqueue(MockResponse(body = """{"total":"0","kwd":"zzzz","result":[]}"""))
        assertEquals(BookSearchResult.Empty, client().searchByTitle("zzzz"))

        assertEquals(BookSearchResult.Empty, client().searchByTitle("   "))
        assertEquals(BookSearchResult.NoKey, client(InMemorySecretStore()).searchByTitle("책"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun 본문_errorCode는_키_오류_서버_오류_파라미터_오류로_나뉜다() = runTest {
        server.enqueue(MockResponse(body = """{"errorCode":"011","errorMsg":"INVALID KEY:유효하지않은 인증키입니다."}"""))
        assertEquals(BookSearchResult.InvalidKey, client().searchByTitle("책"))

        server.enqueue(MockResponse(body = """{"errorCode":"010","errorMsg":"NO KEY VALUE"}"""))
        assertEquals(BookSearchResult.InvalidKey, client().searchByTitle("책"))

        server.enqueue(MockResponse(body = """{"errorCode":"101","errorMsg":"SEARCH ERROR"}"""))
        assertTrue(client().searchByTitle("책") is BookSearchResult.Network)

        server.enqueue(MockResponse(body = """{"errorCode":"014","errorMsg":"PARAMETER VALIDATION ERROR"}"""))
        assertEquals(BookSearchResult.ApiError("014", "PARAMETER VALIDATION ERROR"), client().searchByTitle("책"))
    }

    @Test
    fun HTTP_오류와_끊김과_깨진_응답은_Network로_묶여_직접_입력_폼으로_간다() = runTest {
        server.enqueue(MockResponse(code = 503))
        assertTrue(client().searchByTitle("책") is BookSearchResult.Network)

        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        assertTrue(client().searchByTitle("책") is BookSearchResult.Network)

        server.enqueue(MockResponse(body = "<html>점검 중</html>"))
        assertTrue(client().searchByTitle("책") is BookSearchResult.Network)

        // XML 오류 본문(apiType을 무시하는 경우)도 직렬화 실패 → Network
        server.enqueue(MockResponse(body = "<error><msg>NO KEY</msg><error_code>010</error_code></error>"))
        assertTrue(client().searchByTitle("책") is BookSearchResult.Network)
    }
}

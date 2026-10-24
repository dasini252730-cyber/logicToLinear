package com.logiclinear.reading.data.remote.books

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 국립중앙도서관 소장자료 검색 Open API(T-309, 2026-10-05 사용자 결정).
 * `GET https://www.nl.go.kr/NL/search/openApi/search.do?key=&kwd=&srchTarget=title&apiType=json&pageNum=1&pageSize=10&category=도서`.
 * 키는 쿼리 파라미터로만 받는다(헤더 방식 없음). 오류는 HTTP 200 본문의 `errorCode`로 온다.
 * 응답 필드 이름은 공식 표에 snake_case(title_info)로 적혀 있으나 오류 응답은 camelCase(errorCode)라 둘 다 받는다.
 * 인증키 승인 뒤 실호출로 확인한다(T-001).
 */
interface NlBookApi {
    @GET("NL/search/openApi/search.do")
    suspend fun search(
        @Query("key") key: String,
        @Query("kwd") keyword: String,
        /** 요구사항 "책 검색": 제목으로 검색. */
        @Query("srchTarget") target: String = "title",
        @Query("apiType") apiType: String = "json",
        @Query("pageNum") pageNum: Int = 1,
        /** 요구사항 MaxResults=10에 맞춘다. */
        @Query("pageSize") pageSize: Int = 10,
        /** 고문헌·학위논문·기사를 빼고 도서만. */
        @Query("category") category: String = "도서",
    ): NlSearchResponse

    companion object {
        const val BASE_URL = "https://www.nl.go.kr/"

        /** 상세 페이지 경로가 상대 경로로 온다. */
        fun absoluteLink(link: String): String = when {
            link.isBlank() -> ""
            link.startsWith("http") -> link
            else -> BASE_URL.trimEnd('/') + "/" + link.trimStart('/')
        }

        /** 코드가 숫자(10)로 오면 "010"으로 맞춘다(isLenient로 String에 읽히지만 선행 0이 빠진다). */
        fun normalizeCode(code: String): String = code.trim().padStart(3, '0')

        /** 오류 코드 → 결과 종류. 010 키 없음·011 유효하지 않은 키는 설정 안내, 000 시스템·101 검색서버 오류는 네트워크 계열. */
        fun isKeyError(code: String): Boolean = code == "010" || code == "011"

        fun isServerError(code: String): Boolean = code == "000" || code == "101"
    }
}

/**
 * 정상이면 [result]·[total], 오류면 [errorCode]·[errorMsg]만 온다. [result]는 키가 아예 없으면 null로 남겨
 * "응답 형식이 다르다"와 "검색 결과 0건"을 구분한다(실호출 확인 전 안전장치). `total`은 isLenient라 "3"·3 모두 문자열로 읽힌다.
 */
@Serializable
data class NlSearchResponse(
    val result: List<NlItem>? = null,
    val total: String? = null,
    val errorCode: String? = null,
    val errorMsg: String? = null,
) {
    /** result 키도 errorCode도 없거나, total이 0이 아닌데 result가 비었다: 키 표기가 예상과 다르다. */
    val looksMalformed: Boolean
        get() = errorCode.isNullOrBlank() && (result == null || (result.isEmpty() && !total.isNullOrBlank() && total.trim() != "0"))
}

/** 소장자료 한 건. 표지·소개글은 없다(사용자가 포기한 기능, 공식 필드 표에도 없음). 분류는 KDC 대분류 이름으로 채운다. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NlItem(
    @SerialName("title_info") @JsonNames("titleInfo") val title: String = "",
    @SerialName("author_info") @JsonNames("authorInfo") val author: String = "",
    @SerialName("pub_info") @JsonNames("pubInfo") val publisher: String = "",
    val isbn: String = "",
    @SerialName("detail_link") @JsonNames("detailLink") val detailLink: String = "",
    @SerialName("kdc_name_1s") @JsonNames("kdcName1s") val kdcName: String = "",
) {
    fun toItem(): BookSearchItem = BookSearchItem(
        title = title.stripTags().trim(),
        author = author.stripTags().removeRoleSuffix().trim(),
        publisher = publisher.stripTags().trim(),
        isbn13 = isbn.extractIsbn13(),
        cover = "",
        category = kdcName.stripTags().trim(),
        description = "",
        link = NlBookApi.absoluteLink(detailLink.trim()),
    )
}

/** 검색어 강조 `<span class="searching_txt">…</span>` 같은 태그를 뗀다. */
internal fun String.stripTags(): String = replace(Regex("<[^>]+>"), "")

/**
 * "박경리 지음", "한강 저", "이문구 共著" 같은 역할 꼬리를 뗀다. 공백 뒤에 올 때만 떼므로 "김저"·"구글" 같은 이름은 건드리지 않는다.
 * 공동 저자 구분(", ", ";")은 ", "로 통일한다.
 */
internal fun String.removeRoleSuffix(): String =
    split(';', ',')
        .map { it.trim().replace(ROLE_SUFFIX, "").trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ")

private val ROLE_SUFFIX = Regex("\\s+(지음|저|글|옮김|엮음|편|역|공저|共著|著|編|譯|共譯)$")

/** "9788936433598", "8936433598 9788936433598", "9788936433598 (세트)", "979-11-…" 어디서든 978/979로 시작하는 13자리 하나. */
internal fun String.extractIsbn13(): String =
    Regex("(?<!\\d)97[89]\\d{10}(?!\\d)").find(replace("-", ""))?.value ?: ""

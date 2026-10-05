package com.logiclinear.reading.data.remote.books

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * 카카오 책 검색 API(Daum 검색). 이전 공급자 OpenAPI 종료로 2026-10-05 교체했다.
 * `GET https://dapi.kakao.com/v3/search/book?query=&target=title&size=10`, 헤더 `Authorization: KakaoAK {REST API 키}`.
 */
interface KakaoBookApi {
    @GET("v3/search/book")
    suspend fun search(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        /** 요구사항 "책 검색": 제목으로 검색. */
        @Query("target") target: String = "title",
        /** 요구사항 MaxResults=10에 맞춘다. */
        @Query("size") size: Int = 10,
    ): KakaoBookResponse

    companion object {
        const val BASE_URL = "https://dapi.kakao.com/"
        const val AUTH_PREFIX = "KakaoAK "
    }
}

@Serializable
data class KakaoBookResponse(val meta: KakaoMeta = KakaoMeta(), val documents: List<KakaoBookDocument> = emptyList())

@Serializable
data class KakaoMeta(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("pageable_count") val pageableCount: Int = 0,
    @SerialName("is_end") val isEnd: Boolean = true,
)

/** 카카오 문서 하나. isbn은 "ISBN10 ISBN13"처럼 공백으로 이어져 온다. 저자는 배열. */
@Serializable
data class KakaoBookDocument(
    val title: String = "",
    val contents: String = "",
    val url: String = "",
    val isbn: String = "",
    val authors: List<String> = emptyList(),
    val publisher: String = "",
    val translators: List<String> = emptyList(),
    val thumbnail: String = "",
) {
    fun toItem(): BookSearchItem = BookSearchItem(
        title = title.trim(),
        author = authors.joinToString(", ").trim(),
        publisher = publisher.trim(),
        isbn13 = isbn.split(' ').map { it.trim() }.firstOrNull { it.length == 13 && it.all(Char::isDigit) } ?: "",
        cover = thumbnail.trim(),
        category = "", // 카카오는 분류를 주지 않는다
        description = contents.trim(),
        link = url.trim(),
    )
}

/** 오류 응답: {"errorType":"...","message":"..."} */
@Serializable
data class KakaoErrorResponse(val errorType: String? = null, val message: String? = null)

package com.logiclinear.reading.data.remote.aladin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 알라딘 Open API. 요구사항 "책 검색": ItemSearch(Query=제목, QueryType=Title, MaxResults=10, Cover=Big, Output=JS).
 * Version=20131101부터 output=js가 표준 JSON을 돌려준다.
 */
interface AladinApi {
    @GET("ttb/api/ItemSearch.aspx")
    suspend fun itemSearch(
        @Query("ttbkey") ttbKey: String,
        @Query("Query") query: String,
        @Query("QueryType") queryType: String = "Title",
        @Query("MaxResults") maxResults: Int = 10,
        @Query("Cover") cover: String = "Big",
        @Query("output") output: String = "js",
        /** 요구사항 외 파라미터는 이것 하나. 20131101부터 output=js가 JSON이다. 응답 정리는 [AladinJsonCleanupInterceptor]. */
        @Query("Version") version: String = "20131101",
    ): AladinSearchResponse

    companion object {
        const val BASE_URL = "https://www.aladin.co.kr/"
    }
}

/** 성공이면 item, 실패면 errorCode·errorMessage가 온다. 모르는 필드는 무시한다. */
@Serializable
data class AladinSearchResponse(
    val totalResults: Int = 0,
    val item: List<AladinItem> = emptyList(),
    val errorCode: Int? = null,
    val errorMessage: String? = null,
)

/** 요구사항 "책 검색" 저장 필드 7개: title, author, publisher, isbn13, cover, categoryName, description. link는 추천 카드용. */
@Serializable
data class AladinItem(
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val isbn13: String = "",
    val cover: String = "",
    val categoryName: String = "",
    val description: String = "",
    val link: String = "",
    @SerialName("pubDate") val pubDate: String = "",
)

package com.logiclinear.reading.data.remote.books

/**
 * 도서 검색 결과 한 건. 공급자(카카오 책 검색)와 무관한 모양으로, 요구사항 "책 검색" 저장 필드와 1:1이다.
 * [category]는 분류(장르) 자리다. 카카오는 분류를 주지 않아 비어 있다(2026-10-05 공급자 교체).
 */
data class BookSearchItem(
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val isbn13: String = "",
    val cover: String = "",
    val category: String = "",
    val description: String = "",
    /** 공급자 상세 페이지. 추천 카드 "책 정보 보기"에 쓴다. */
    val link: String = "",
)

/** 검색 결과. UI는 실패 종류에 따라 직접 입력 폼으로 넘어간다(요구사항 "예외 처리"). */
sealed interface BookSearchResult {
    data class Found(val items: List<BookSearchItem>) : BookSearchResult

    /** 정상 응답인데 결과가 0건. */
    data object Empty : BookSearchResult

    /** 설정에 검색 키가 없다. 설정 화면으로 안내한다. */
    data object NoKey : BookSearchResult

    /** 키가 틀렸거나 권한이 없다(401·403). 설정 화면으로 안내한다. */
    data object InvalidKey : BookSearchResult

    /** 공급자가 오류를 돌려줬다(한도 초과 429, 잘못된 요청 400 등). [code]는 공급자 코드 문자열(HTTP 코드 또는 본문 errorCode). 직접 입력 폼으로. */
    data class ApiError(val code: String?, val message: String?) : BookSearchResult

    /** 오프라인·타임아웃·서버 오류. 직접 입력 폼으로. */
    data class Network(val cause: Throwable) : BookSearchResult
}

/** 검색 추상화. ViewModel·Repository 테스트는 가짜 구현을 넣는다. */
interface BookSearch {
    suspend fun searchByTitle(title: String): BookSearchResult
}

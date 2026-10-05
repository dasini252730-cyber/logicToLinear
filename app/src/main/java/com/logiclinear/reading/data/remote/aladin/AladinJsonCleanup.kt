package com.logiclinear.reading.data.remote.aladin

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * 알라딘 `output=js` 응답을 표준 JSON으로 정리한다. 구버전·일부 엔드포인트는 끝에 `;`를 붙이거나
 * `callback({...})`로 감싸 보내는 경우가 있어 그대로 넘기면 역직렬화가 실패한다(T-301 리뷰 차단 1).
 * 순수 함수 [cleanAladinJson]은 테스트하고, 인터셉터는 그것을 응답 본문에 적용한다.
 */
fun cleanAladinJson(raw: String): String {
    var s = raw.trim()
    // 끝의 세미콜론(여러 개·공백 섞임 포함)
    while (s.endsWith(";")) s = s.dropLast(1).trimEnd()
    // callback(...) 래퍼: 첫 '{' 또는 '[' 앞에 식별자와 '('가 있고 끝이 ')'
    val firstBrace = s.indexOfFirst { it == '{' || it == '[' }
    if (firstBrace > 0 && s.endsWith(")") && s.substring(0, firstBrace).trimEnd().endsWith("(")) {
        s = s.substring(firstBrace, s.length - 1).trim()
    }
    return s
}

class AladinJsonCleanupInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        if (!response.isSuccessful) return response
        val cleaned = cleanAladinJson(body.string())
        return response.newBuilder()
            .body(cleaned.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }
}

package com.logiclinear.reading.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 추천 한 건. AI 응답은 title·author·reason만 주고, isbn13·coverUrl·storeUrl은 도서 검색 후처리(T-605)가 채운다.
 * Analysis.recommendationsJson의 원소 모양과 같다.
 */
@Serializable
data class Recommendation(
    val title: String,
    val author: String? = null,
    val reason: String = "",
    val isbn13: String? = null,
    val coverUrl: String? = null,
    val storeUrl: String? = null,
)

/** 요구사항 "출력은 JSON만: {"taste": "...", "recommendations": [...]}" */
@Serializable
data class AnalysisOutput(val taste: String, val recommendations: List<Recommendation> = emptyList())

/** 관대한 파서: 모르는 키 무시, null은 기본값으로. */
val analysisJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
}

/**
 * 요구사항 "예외 처리": 파싱 실패 시 펜스·앞뒤 텍스트 제거 후 재파싱, 그래도 실패하면 원문을 taste로 두고 추천은 비움.
 * 순서: 그대로 → 마크다운 펜스 제거 → 첫 `{`부터 마지막 `}`까지 → 폴백.
 */
fun parseAnalysis(raw: String): AnalysisOutput {
    val candidates = sequence {
        yield(raw)
        yield(stripFences(raw))
        yield(outermostObject(raw))
    }
    for (candidate in candidates) {
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) continue
        runCatching { analysisJson.decodeFromString(AnalysisOutput.serializer(), trimmed) }
            .getOrNull()
            ?.let { return it.copy(recommendations = it.recommendations.filter { r -> r.title.isNotBlank() }) }
    }
    return AnalysisOutput(taste = raw.trim(), recommendations = emptyList())
}

/** ```json ... ``` 또는 ``` ... ``` 펜스를 벗긴다. 펜스가 없으면 그대로. */
internal fun stripFences(text: String): String {
    val match = FENCE.find(text) ?: return text
    return match.groupValues[1]
}

/** 설명문 사이에 JSON 객체가 끼어 있을 때 첫 `{`와 마지막 `}` 사이만 남긴다. */
internal fun outermostObject(text: String): String {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    return if (start in 0 until end) text.substring(start, end + 1) else ""
}

private val FENCE = Regex("```[a-zA-Z]*\\s*([\\s\\S]*?)```")

fun encodeRecommendations(list: List<Recommendation>): String =
    analysisJson.encodeToString(ListSerializer(Recommendation.serializer()), list)

/** 저장된 recommendationsJson을 읽는다. 깨져 있으면 빈 목록(화면은 카드 영역을 숨긴다). */
fun decodeRecommendations(json: String): List<Recommendation> =
    runCatching { analysisJson.decodeFromString(ListSerializer(Recommendation.serializer()), json) }.getOrDefault(emptyList())

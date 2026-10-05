package com.logiclinear.reading.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisParseTest {
    private val valid = """{"taste":"문장에 오래 머무는 편","recommendations":[{"title":"흰","author":"한강","reason":"저장한 글귀와 닿아 있어서"}]}"""

    @Test
    fun 정상_JSON은_그대로_파싱된다() {
        val out = parseAnalysis(valid)
        assertEquals("문장에 오래 머무는 편", out.taste)
        assertEquals(listOf(Recommendation("흰", "한강", "저장한 글귀와 닿아 있어서")), out.recommendations)
    }

    @Test
    fun 마크다운_펜스를_벗겨_파싱한다() {
        val out = parseAnalysis("```json\n$valid\n```")
        assertEquals(1, out.recommendations.size)
        assertEquals(1, parseAnalysis("```\n$valid\n```").recommendations.size)
    }

    @Test
    fun 앞뒤_설명문이_있어도_객체만_잘라_파싱한다() {
        // 마지막 '}'가 설명문 안에 있으면 바깥 객체 추출도 실패해 원문 폴백으로 떨어진다(taste = 원문).
        val raw = "다음은 분석 결과입니다.\n$valid\n도움이 되길 바랍니다. {끝}"
        assertEquals(raw.trim(), parseAnalysis(raw).taste)
        // 바깥 객체 추출 경로: 펜스 없이 앞뒤에 설명문만 있을 때
        assertEquals("""{"a":1}""", outermostObject("앞 {\"a\":1} 뒤"))
        val clean = parseAnalysis("결과:\n$valid\n이상입니다.")
        assertEquals("문장에 오래 머무는 편", clean.taste)
        assertEquals("흰", clean.recommendations.single().title)
    }

    @Test
    fun 완전_실패면_원문이_taste에_들어가고_추천은_빈_배열() {
        val raw = "  이번엔 JSON을 만들지 못했어요.  "
        val out = parseAnalysis(raw)
        assertEquals(raw.trim(), out.taste)
        assertTrue(out.recommendations.isEmpty())
        assertEquals("", parseAnalysis("").taste)
    }

    @Test
    fun 필드가_빠지거나_null이면_기본값으로_읽고_제목_없는_추천은_버린다() {
        val out = parseAnalysis("""{"taste":"t","recommendations":[{"title":"","reason":"x"},{"title":"책","author":null}]}""")
        assertEquals(listOf(Recommendation("책")), out.recommendations)
        assertEquals(AnalysisOutput("추천 없음"), parseAnalysis("""{"taste":"추천 없음"}"""))
    }

    @Test
    fun recommendationsJson_인코딩_디코딩_라운드트립과_깨진_값() {
        val list = listOf(Recommendation("흰", "한강", "이유", isbn13 = "9788954651134", coverUrl = "https://c/1.jpg", aladinUrl = "https://a/1"))
        val json = encodeRecommendations(list)
        assertEquals(list, decodeRecommendations(json))
        assertEquals(emptyList<Recommendation>(), decodeRecommendations("깨짐"))
        assertEquals(emptyList<Recommendation>(), decodeRecommendations("[]"))
    }
}

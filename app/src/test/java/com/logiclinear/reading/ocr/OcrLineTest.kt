package com.logiclinear.reading.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLineTest {
    @Test
    fun 위에서_아래로_같은_높이면_왼쪽부터_정렬한다() {
        val raw = listOf(
            RawLine("셋째", 0, 300, 100, 330),
            RawLine("첫째 오른쪽", 200, 100, 300, 130),
            RawLine("첫째 왼쪽", 0, 100, 100, 130),
            RawLine("둘째", 0, 200, 100, 230),
        )

        val lines = toOcrLines(raw, imageHeight = 1000)

        assertEquals(listOf("첫째 왼쪽", "첫째 오른쪽", "둘째", "셋째"), lines.map { it.text })
    }

    @Test
    fun 빈_텍스트는_버리고_공백은_정리한다() {
        val raw = listOf(RawLine("  ", 0, 0, 10, 10), RawLine("  본문  ", 0, 20, 10, 30))

        val lines = toOcrLines(raw, imageHeight = 100)

        assertEquals(listOf("본문"), lines.map { it.text })
    }

    @Test
    fun 비율은_이미지_높이_기준이고_0과_1_사이로_자른다() {
        val raw = listOf(RawLine("상단", 0, 50, 10, 100), RawLine("밖", 0, -20, 10, 1200))

        val lines = toOcrLines(raw, imageHeight = 1000)

        assertEquals(0.05f, lines[1].topRatio, 0.0001f)
        assertEquals(0.10f, lines[1].bottomRatio, 0.0001f)
        assertEquals(0f, lines[0].topRatio, 0f)
        assertEquals(1f, lines[0].bottomRatio, 0f)
    }

    @Test
    fun 높이가_0이면_빈_결과() {
        assertTrue(toOcrLines(listOf(RawLine("a", 0, 0, 1, 1)), imageHeight = 0).isEmpty())
    }

    @Test
    fun 결과가_비면_isEmpty() {
        assertTrue(OcrResult(emptyList(), 100, 100, 5).isEmpty)
    }
}

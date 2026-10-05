package com.logiclinear.reading.domain

import com.logiclinear.reading.ocr.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageCandidatesTest {
    private fun line(text: String, topRatio: Float, bottomRatio: Float = topRatio + 0.02f) =
        OcrLine(text, 0, (topRatio * 1000).toInt(), 100, (bottomRatio * 1000).toInt(), topRatio, bottomRatio)

    @Test
    fun 하단_가장자리의_숫자_하나는_후보_1개() {
        val lines = listOf(line("본문 첫 줄", 0.20f), line("본문 둘째 줄", 0.50f), line("123", 0.95f))
        assertEquals(listOf(123), extractPageCandidates(lines))
    }

    @Test
    fun 상단과_하단에_숫자가_있으면_후보_2개_위에서_아래_순() {
        val lines = listOf(line("12", 0.03f), line("본문", 0.40f), line("13", 0.96f))
        assertEquals(listOf(12, 13), extractPageCandidates(lines))
    }

    @Test
    fun 본문_중간의_숫자는_후보가_아니다() {
        val lines = listOf(line("1984년에", 0.40f), line("42", 0.50f))
        assertEquals(emptyList<Int>(), extractPageCandidates(lines))
    }

    @Test
    fun 다섯_자리_이상과_숫자_섞인_글은_제외() {
        val lines = listOf(line("12345", 0.96f), line("p.12", 0.96f), line("12쪽", 0.96f))
        assertEquals(emptyList<Int>(), extractPageCandidates(lines))
    }

    @Test
    fun 장식_기호로_감싼_번호는_숫자만_본다() {
        assertEquals(77, parsePageNumber("- 77 -"))
        assertEquals(77, parsePageNumber("· 77 ·"))
        assertEquals(5, parsePageNumber(" 5 "))
    }

    @Test
    fun 경계값_15퍼센트는_포함_0은_제외() {
        val lines = listOf(line("1", 0.15f, 0.16f), line("0", 0.97f), line("2", 0.84f, 0.85f))
        assertEquals(listOf(1, 2), extractPageCandidates(lines))
        assertNull(parsePageNumber("0"))
        assertNull(parsePageNumber(""))
    }

    @Test
    fun 같은_번호가_두_번_잡히면_하나로() {
        val lines = listOf(line("88", 0.02f), line("88", 0.97f))
        assertEquals(listOf(88), extractPageCandidates(lines))
    }
}

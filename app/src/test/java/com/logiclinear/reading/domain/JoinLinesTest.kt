package com.logiclinear.reading.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class JoinLinesTest {
    @Test
    fun 음절_중간에서_끊긴_한국어는_공백_없이_붙인다() {
        assertEquals("나는 그날 처음으로 바다를 보았다", joinLines(listOf("나는 그날 처음으로 바", "다를 보았다")))
    }

    @Test
    fun 마침표로_끝나면_공백_하나로_잇는다() {
        assertEquals("바다를 보았다. 그리고 울었다.", joinLines(listOf("바다를 보았다.", "그리고 울었다.")))
    }

    @Test
    fun 쉼표와_닫는_따옴표도_문장_부호로_본다() {
        assertEquals("“괜찮아,” 그가 말했다", joinLines(listOf("“괜찮아,”", "그가 말했다")))
        assertEquals("하나, 둘", joinLines(listOf("하나,", "둘")))
    }

    @Test
    fun 숫자나_라틴_문자로_끝나도_문장_부호가_아니면_붙인다() {
        assertEquals("1945년 8월 15일에", joinLines(listOf("1945년 8월 15", "일에")))
        assertEquals("미토콘드리아는 ATP를 만든다", joinLines(listOf("미토콘드리아는 ATP", "를 만든다")))
        assertEquals("1984년의 봄", joinLines(listOf("1984", "년의 봄")))
    }

    @Test
    fun 직선_따옴표는_여는지_닫는지_몰라_붙인다() {
        assertEquals("그는 \"괜찮아", joinLines(listOf("그는 \"", "괜찮아")))
    }

    @Test
    fun 하이픈_분철은_하이픈을_떼고_붙인다() {
        assertEquals("understanding", joinLines(listOf("under-", "standing")))
    }

    @Test
    fun 한_줄이면_그대로_공백만_정리한다() {
        assertEquals("한 줄", joinLines(listOf("  한 줄  ")))
    }

    @Test
    fun 빈_줄은_무시하고_모두_비면_빈_문자열() {
        assertEquals("가나", joinLines(listOf("가", "", "   ", "나")))
        assertEquals("", joinLines(emptyList()))
        assertEquals("", joinLines(listOf(" ", "")))
    }

    @Test
    fun 연속_선택은_한_문장으로_떨어진_선택은_줄바꿈으로_나눈다() {
        val lines = listOf("첫 문장의 앞", "부분이다.", "건너뛴 줄", "다른 문장")
        assertEquals("첫 문장의 앞부분이다.\n다른 문장", joinSelectedLines(lines, setOf(0, 1, 3)))
        assertEquals("", joinSelectedLines(lines, emptySet()))
        assertEquals("다른 문장", joinSelectedLines(lines, setOf(3, 99)))
    }
}

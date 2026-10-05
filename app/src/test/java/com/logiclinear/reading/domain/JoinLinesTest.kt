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
    fun 영문은_단어_사이에_공백을_넣고_하이픈_분철은_붙인다() {
        assertEquals("the quick brown fox", joinLines(listOf("the quick", "brown fox")))
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
    fun 숫자로_끝나면_공백으로_잇는다() {
        assertEquals("1984 년의 봄", joinLines(listOf("1984", "년의 봄")))
    }
}

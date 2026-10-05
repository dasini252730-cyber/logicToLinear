package com.logiclinear.reading.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BookCoverTest {
    @Test
    fun 첫_글자는_한글_한_음절() {
        assertEquals("채", firstGlyph(" 채식주의자"))
    }

    @Test
    fun 이모지는_서로게이트_쌍을_쪼개지_않는다() {
        assertEquals("📚", firstGlyph("📚 책 이야기"))
    }

    @Test
    fun 빈_제목은_기본_글자() {
        assertEquals("책", firstGlyph("   "))
    }
}

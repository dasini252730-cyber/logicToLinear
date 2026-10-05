package com.logiclinear.reading.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SameBookTest {
    private val withIsbn = BookIdentity("채식주의자", "한강", "9788936433598")

    @Test
    fun isbn_일치하면_제목저자가_달라도_같은_책() {
        assertTrue(isSameBook(withIsbn, BookIdentity("The Vegetarian", "Han Kang", "978-89-364-3359-8")))
    }

    @Test
    fun isbn_불일치하면_제목저자가_같아도_다른_책() {
        assertFalse(isSameBook(withIsbn, withIsbn.copy(isbn13 = "9788936433599")))
    }

    @Test
    fun isbn_없으면_제목저자로_판정() {
        assertTrue(isSameBook(BookIdentity("채식주의자", "한강", null), withIsbn))
        assertTrue(isSameBook(BookIdentity("채식주의자", "한강", null), BookIdentity("채식주의자", "한강", null)))
        assertFalse(isSameBook(BookIdentity("채식주의자", "한강", null), BookIdentity("채식주의자", "김훈", null)))
        assertFalse(isSameBook(BookIdentity("소년이 온다", "한강", null), BookIdentity("채식주의자", "한강", null)))
    }

    @Test
    fun 공백과_대소문자_차이는_무시() {
        assertTrue(isSameBook(BookIdentity("  The  Vegetarian ", "HAN kang", null), BookIdentity("the vegetarian", "han  Kang", null)))
    }

    @Test
    fun 저자_없음은_둘_다_없을_때만_같다() {
        assertTrue(isSameBook(BookIdentity("채식주의자", null, null), BookIdentity("채식주의자", "  ", null)))
        assertFalse(isSameBook(BookIdentity("채식주의자", null, null), BookIdentity("채식주의자", "한강", null)))
    }

    @Test
    fun 제목이_비어_있으면_isbn이_없는_한_같은_책이_아니다() {
        assertFalse(isSameBook(BookIdentity("", null, null), BookIdentity("  ", "", null)))
        assertFalse(isSameBook(BookIdentity("", "한강", null), BookIdentity("", "한강", null)))
        assertTrue(isSameBook(BookIdentity("", null, "9788936433598"), BookIdentity("", null, "9788936433598")))
    }

    @Test
    fun isbn_정규화는_숫자만_남기고_비면_null() {
        assertEquals("9788936433598", normalizeIsbn("978-89-364-3359-8"))
        assertNull(normalizeIsbn("   "))
        assertNull(normalizeIsbn(null))
        assertEquals("", normalizeText(null))
    }
}

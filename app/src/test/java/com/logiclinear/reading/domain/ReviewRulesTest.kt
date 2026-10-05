package com.logiclinear.reading.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRulesTest {
    @Test
    fun 별점은_1에서_5만_받고_밖이면_null() {
        assertEquals(1, normalizeReview(1, null).rating)
        assertEquals(5, normalizeReview(5, null).rating)
        assertNull(normalizeReview(0, null).rating)
        assertNull(normalizeReview(6, null).rating)
        assertNull(normalizeReview(null, null).rating)
    }

    @Test
    fun 한_줄은_공백을_정리하고_100자에서_자른다() {
        assertEquals("좋았다", normalizeReview(null, "  좋았다  ").oneLiner)
        assertNull(normalizeReview(null, "   ").oneLiner)
        val long = "가".repeat(150)
        assertEquals(100, normalizeReview(null, long).oneLiner!!.length)
        assertEquals(100, normalizeReview(null, "가".repeat(100)).oneLiner!!.length)
    }

    @Test
    fun 둘_다_비면_isEmpty() {
        assertTrue(normalizeReview(null, "").isEmpty())
        assertFalse(normalizeReview(3, null).isEmpty())
        assertFalse(normalizeReview(null, "한 줄").isEmpty())
    }
}

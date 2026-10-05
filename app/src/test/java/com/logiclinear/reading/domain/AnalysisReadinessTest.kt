package com.logiclinear.reading.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisReadinessTest {
    @Test
    fun 경계값_3권_10개에서_활성_그_아래는_비활성() {
        assertTrue(AnalysisReadiness(3, 10).ready)
        assertTrue(AnalysisReadiness(30, 200).ready)
        assertFalse(AnalysisReadiness(2, 10).ready)
        assertFalse(AnalysisReadiness(3, 9).ready)
        assertFalse(AnalysisReadiness(0, 0).ready)
    }

    @Test
    fun 남은_수는_정확하고_음수가_되지_않는다() {
        val r = AnalysisReadiness(1, 4)
        assertEquals(2, r.missingBooks)
        assertEquals(6, r.missingQuotes)
        assertEquals(0, AnalysisReadiness(5, 12).missingBooks)
        assertEquals(0, AnalysisReadiness(5, 12).missingQuotes)
    }
}

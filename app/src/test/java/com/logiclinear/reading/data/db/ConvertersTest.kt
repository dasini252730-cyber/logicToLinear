package com.logiclinear.reading.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun instant_왕복변환_밀리초보존() {
        val now = Instant.ofEpochMilli(1_759_600_000_123)
        assertEquals(now, converters.longToInstant(converters.instantToLong(now)))
        assertEquals(1_759_600_000_123L, converters.instantToLong(now))
    }

    @Test
    fun instant_null은_null() {
        assertNull(converters.instantToLong(null))
        assertNull(converters.longToInstant(null))
    }

    @Test
    fun localDate_ISO문자열로_저장() {
        val date = LocalDate.of(2026, 10, 5)
        assertEquals("2026-10-05", converters.localDateToString(date))
        assertEquals(date, converters.stringToLocalDate("2026-10-05"))
    }

    @Test
    fun localDate_null은_null() {
        assertNull(converters.localDateToString(null))
        assertNull(converters.stringToLocalDate(null))
    }

    @Test
    fun bookStatus_세_값_모두_name으로_왕복() {
        for (status in BookStatus.entries) {
            assertEquals(status.name, converters.bookStatusToString(status))
            assertEquals(status, converters.stringToBookStatus(status.name))
        }
        assertNull(converters.bookStatusToString(null))
        assertNull(converters.stringToBookStatus(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun bookStatus_모르는_문자열은_예외() {
        converters.stringToBookStatus("ARCHIVED")
    }
}

package com.ybugmobile.waktiva.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DayCircleStyleTest {

    @Test
    fun readsEveryStoredName() {
        DayCircleStyle.entries.forEach { assertEquals(it, DayCircleStyle.fromName(it.name)) }
    }

    @Test
    fun ignoresCase() {
        assertEquals(DayCircleStyle.BRASS, DayCircleStyle.fromName("brass"))
    }

    @Test
    fun fallsBackToClassicForMissingOrUnknownValues() {
        assertEquals(DayCircleStyle.CLASSIC, DayCircleStyle.fromName(null))
        assertEquals(DayCircleStyle.CLASSIC, DayCircleStyle.fromName("CHROME"))
    }
}

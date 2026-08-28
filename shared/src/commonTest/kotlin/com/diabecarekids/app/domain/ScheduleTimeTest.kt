package com.diabecarekids.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScheduleTimeTest {

    @Test
    fun parsesStrictHhMm() {
        assertEquals(LocalTimeOfDay(8, 0), LocalTimeOfDay.parse("08:00"))
        assertEquals(LocalTimeOfDay(12, 30), LocalTimeOfDay.parse("12:30"))
        assertEquals(LocalTimeOfDay(0, 0), LocalTimeOfDay.parse("00:00"))
        assertEquals(LocalTimeOfDay(23, 59), LocalTimeOfDay.parse("23:59"))
    }

    @Test
    fun rejectsSingleDigitHour() {
        assertFailsWith<IllegalArgumentException> { LocalTimeOfDay.parse("8:00") }
    }

    @Test
    fun rejectsHourOutOfRange() {
        assertFailsWith<IllegalArgumentException> { LocalTimeOfDay.parse("24:00") }
        assertFailsWith<IllegalArgumentException> { LocalTimeOfDay.parse("-1:00") }
    }

    @Test
    fun rejectsMinuteOutOfRange() {
        assertFailsWith<IllegalArgumentException> { LocalTimeOfDay.parse("08:60") }
        assertFailsWith<IllegalArgumentException> { LocalTimeOfDay.parse("08:99") }
    }

    @Test
    fun rejectsNonTimeStrings() {
        assertFailsWith<IllegalArgumentException> { LocalTimeOfDay.parse("abc") }
        assertFailsWith<IllegalArgumentException> { LocalTimeOfDay.parse("8:0") }
        assertFailsWith<IllegalArgumentException> { LocalTimeOfDay.parse("08 00") }
        assertFailsWith<IllegalArgumentException> { LocalTimeOfDay.parse("") }
    }

    @Test
    fun constructorRejectsOutOfRangeValues() {
        assertFailsWith<IllegalArgumentException> { LocalTimeOfDay(24, 0) }
        assertFailsWith<IllegalArgumentException> { LocalTimeOfDay(0, 60) }
    }
}

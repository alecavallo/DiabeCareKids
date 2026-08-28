package com.diabecarekids.app.platform

import com.diabecarekids.app.format.formatEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ID-TZ: wall-clock-as-UTC live-meal timestamps.
 *
 * [wallClockAsUtcEpochNow] is a platform (Android) actual using the local
 * timezone, so its exact value is environment-dependent. We verify it stays on the
 * wall-clock-as-UTC timeline: it must differ from the true-UTC [epochMillisNow] by
 * at most the maximum real-world timezone offset (UTC±14h, with headroom), i.e. it
 * encodes "now" shifted onto the local-wall-clock-as-UTC axis rather than the true
 * UTC axis. Combined with the shared formatter's wall-clock-as-UTC interpretation
 * ([com.diabecarekids.app.format.formatEpochMillis]), a live meal stored via this
 * seam renders the user's local wall clock with no timezone shift.
 *
 * ID-TZ-TIMELINE: the reminder engine's [todayAtLocalTimeMillis] /
 * [localTimeAtDayOffsetMillis] seams must live on the SAME wall-clock-as-UTC axis,
 * otherwise query bounds and stored records are offset by the device tz and
 * post-logging suppression (ID-SUPPRESS) breaks off-UTC. [formatEpochMillis]
 * renders an epoch's components as wall-clock-as-UTC, so a seam returns the
 * wall-clock-as-UTC epoch of (h:m today) IFF formatting it yields exactly that h:m.
 */
class PlatformTimeTest {

    @Test
    fun wallClockAsUtcEpochNowIsBoundedWithinMaxTzOffsetOfTrueNow() {
        val trueNow = epochMillisNow()
        val wallClock = wallClockAsUtcEpochNow()

        // Max real-world tz offset is UTC-12..+14; give headroom.
        val maxTzOffsetMillis = 15 * 60 * 60 * 1000L
        val diff = kotlin.math.abs(wallClock - trueNow)
        assertTrue(
            diff <= maxTzOffsetMillis,
            "wallClockAsUtcEpochNow()=$wallClock should be within ±14h of true now=$trueNow (was $diff ms)",
        )
    }

    @Test
    fun todayAtLocalTimeIsOnWallClockAsUtcAxis() {
        // The seam must re-encode the local wall-clock h:m as UTC: formatting the
        // returned epoch must yield exactly "HH:mm" (wall-clock-as-UTC). A true-UTC
        // seam would render h+tzOffset, breaking suppression off-UTC (ID-TZ-TIMELINE).
        assertEquals("12:10", formatEpochMillis(todayAtLocalTimeMillis(12, 10)).substringAfter(' '))
        assertEquals("12:15", formatEpochMillis(todayAtLocalTimeMillis(12, 15)).substringAfter(' '))
    }

    @Test
    fun localTimeAtDayOffsetIsOnWallClockAsUtcAxis() {
        // Same property for the next-day re-arm seam (ID-REARM); dayOffset 0 must
        // agree with todayAtLocalTimeMillis and positive offsets stay wall-clock-as-UTC.
        assertEquals("12:15", formatEpochMillis(localTimeAtDayOffsetMillis(12, 15, 0)).substringAfter(' '))
        assertEquals("08:00", formatEpochMillis(localTimeAtDayOffsetMillis(8, 0, 1)).substringAfter(' '))
        assertEquals("12:15", formatEpochMillis(localTimeAtDayOffsetMillis(12, 15, 3)).substringAfter(' '))
    }
}

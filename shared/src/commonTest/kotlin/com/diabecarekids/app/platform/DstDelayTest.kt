package com.diabecarekids.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ID-DST-DELAY: the WorkManager delay must be REAL ELAPSED time, not the nominal
 * wall-clock span.
 *
 * WorkManager's `setInitialDelay` counts real elapsed milliseconds. The reminder
 * trigger (`triggerAt`) lives on the wall-clock-as-UTC axis, so the delay the
 * scheduler passes must be converted to the true-UTC (real elapsed) axis using the
 * device offset AT THE TRIGGER'S WALL TIME. On a DST day that offset differs from
 * the current one; using the current offset would cancel out and reproduce the
 * nominal-span bug. These tests exercise the pure [reminderDelayMillis] /
 * [wallClockAsUtcToTrueUtc] helpers with injected offsets.
 *
 * Conventions used here: a wall-clock-as-UTC epoch directly encodes a local wall
 * time as "HH:mm" on the UTC calendar, so the wall-clock-as-UTC epoch of 03:00 is
 * `3h`. The true-UTC epoch of a wall time is `wallClockAsUtc - offsetAtWallTime`.
 * All values below are expressed in minutes of a synthetic epoch for readability.
 */
class DstDelayTest {

    private fun h(hour: Int): Long = hour * 60L * 60_000

    /**
     * Fall-back day (25h): offset shrinks from EDT (-4h) to EST (-5h) between now
     * and the trigger. Wall now 00:00, trigger wall 03:00 → nominal span 3h, but
     * the real elapsed span is 4h. The delay must be 4h.
     */
    @Test
    fun delayEqualsTrueUtcSpanOnFallBackDay() {
        val realNowMillis = h(4)          // true-UTC now = 04:00
        val nowOffset = -h(4)             // EDT before transition
        val triggerOffset = -h(5)         // EST at the trigger's wall time (after fall-back)
        val triggerAt = h(3)              // wall-clock-as-UTC 03:00

        // Sanity: the wall-clock-as-UTC "now" (00:00) and the nominal 3h span that
        // the old code would have used.
        val wallClockNow = realNowMillis + nowOffset // 00:00 as wall-clock-as-UTC
        assertEquals(h(0), wallClockNow)
        assertEquals(h(3), triggerAt - wallClockNow, "nominal wall span is 3h")

        val delay = reminderDelayMillis(triggerAt, triggerOffset, realNowMillis)

        assertEquals(h(4), delay, "delay must be the real 4h span on a 25h fall-back day")
    }

    /**
     * Spring-forward day (23h): offset grows from EST (-5h) to EDT (-4h) between
     * now and the trigger. Wall now 00:00, trigger wall 03:00 → nominal span 3h,
     * but the real elapsed span is only 2h. The delay must be 2h.
     */
    @Test
    fun delayEqualsTrueUtcSpanOnSpringForwardDay() {
        val realNowMillis = h(5)          // true-UTC now = 05:00
        val nowOffset = -h(5)             // EST before transition
        val triggerOffset = -h(4)         // EDT at the trigger's wall time (after spring-forward)
        val triggerAt = h(3)              // wall-clock-as-UTC 03:00

        val wallClockNow = realNowMillis + nowOffset // 00:00 as wall-clock-as-UTC
        assertEquals(h(0), wallClockNow)
        assertEquals(h(3), triggerAt - wallClockNow, "nominal wall span is 3h")

        val delay = reminderDelayMillis(triggerAt, triggerOffset, realNowMillis)

        assertEquals(h(2), delay, "delay must be the real 2h span on a 23h spring-forward day")
    }

    /** Regular day (offset unchanged): the real span equals the nominal wall span. */
    @Test
    fun delayEqualsNominalSpanWhenOffsetIsStable() {
        val realNowMillis = h(6)          // true-UTC 06:00
        val offset = -h(3)                // fixed -3h, no transition
        val triggerAt = h(5)              // wall-clock-as-UTC 05:00
        // real span = (5 - (-3)) - 6 = 2h, matching the nominal 2h wall span.
        assertEquals(h(2), reminderDelayMillis(triggerAt, offset, realNowMillis))
    }

    /** The pure conversion helper re-expresses a wall-clock-as-UTC epoch as true-UTC. */
    @Test
    fun wallClockAsUtcToTrueUtcShiftsByTheOffsetAtWallTime() {
        // 03:00 wall-clock-as-UTC at EST (-5h) is 08:00 true-UTC.
        assertEquals(h(8), wallClockAsUtcToTrueUtc(h(3), -h(5)))
        // 03:00 wall-clock-as-UTC at EDT (-4h) is 07:00 true-UTC.
        assertEquals(h(7), wallClockAsUtcToTrueUtc(h(3), -h(4)))
    }
}

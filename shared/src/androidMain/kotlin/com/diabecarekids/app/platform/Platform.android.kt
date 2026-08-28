package com.diabecarekids.app.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpClientEngine(): HttpClientEngine = OkHttp.create()

actual fun epochMillisNow(): Long = System.currentTimeMillis()

/**
 * Reads the current local wall-clock (all fields) and re-builds an epoch treating
 * those components as UTC, so the stored value lives on the wall-clock-as-UTC
 * timeline the shared formatter expects (ID-TZ). Differs from [epochMillisNow] by
 * the local timezone offset.
 */
actual fun wallClockAsUtcEpochNow(): Long {
    val now = java.util.Calendar.getInstance()
    val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(java.util.Calendar.YEAR, now.get(java.util.Calendar.YEAR))
        set(java.util.Calendar.MONTH, now.get(java.util.Calendar.MONTH))
        set(java.util.Calendar.DAY_OF_MONTH, now.get(java.util.Calendar.DAY_OF_MONTH))
        set(java.util.Calendar.HOUR_OF_DAY, now.get(java.util.Calendar.HOUR_OF_DAY))
        set(java.util.Calendar.MINUTE, now.get(java.util.Calendar.MINUTE))
        set(java.util.Calendar.SECOND, now.get(java.util.Calendar.SECOND))
        set(java.util.Calendar.MILLISECOND, now.get(java.util.Calendar.MILLISECOND))
    }
    return utc.timeInMillis
}

/**
 * Epoch millis for the given LOCAL wall-clock time (hour:minute) today, re-encoded
 * as UTC so the result lives on the wall-clock-as-UTC axis (ID-TZ-TIMELINE). Live
 * meal records are stored via [wallClockAsUtcEpochNow] (wall-clock-as-UTC), so the
 * engine's trigger/query bounds MUST be on the same numeric axis or post-logging
 * suppression (ID-SUPPRESS) breaks off-UTC (records and bounds offset by the device
 * tz). Differs from the old true-UTC seam by the local timezone offset.
 */
actual fun todayAtLocalTimeMillis(hour: Int, minute: Int): Long {
    val now = java.util.Calendar.getInstance()
    val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(java.util.Calendar.YEAR, now.get(java.util.Calendar.YEAR))
        set(java.util.Calendar.MONTH, now.get(java.util.Calendar.MONTH))
        set(java.util.Calendar.DAY_OF_MONTH, now.get(java.util.Calendar.DAY_OF_MONTH))
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return utc.timeInMillis
}

/**
 * Epoch millis for the given LOCAL wall-clock time `dayOffset` calendar days from
 * today (DST-aware wall date roll), re-encoded as UTC so it stays on the
 * wall-clock-as-UTC axis (ID-TZ-TIMELINE, used by ID-REARM next-day triggers).
 * `dayOffset == 0` matches [todayAtLocalTimeMillis].
 */
actual fun localTimeAtDayOffsetMillis(hour: Int, minute: Int, dayOffset: Int): Long {
    val local = java.util.Calendar.getInstance()
    // Roll the LOCAL calendar day first (DST-aware wall date), then re-encode the
    // wall-clock components as UTC to preserve the wall-clock-as-UTC axis.
    if (dayOffset != 0) local.add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
    val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(java.util.Calendar.YEAR, local.get(java.util.Calendar.YEAR))
        set(java.util.Calendar.MONTH, local.get(java.util.Calendar.MONTH))
        set(java.util.Calendar.DAY_OF_MONTH, local.get(java.util.Calendar.DAY_OF_MONTH))
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return utc.timeInMillis
}

/**
 * Device UTC offset at the wall time encoded by [wallClockAsUtcEpoch].
 *
 * The wall-clock-as-UTC epoch is exactly the local wall-clock components
 * re-encoded as UTC, so decoding it with a UTC calendar reproduces those
 * components; re-encoding them in the device's timezone yields the TRUE-UTC epoch
 * of that wall time (DST-aware at that date). The device offset at that wall time
 * is the difference. This is the trigger's own offset, not the current one — on a
 * DST transition they differ, and only the trigger-time offset makes the WorkManager
 * delay real-elapsed correct (ID-DST-DELAY).
 */
actual fun wallClockAsUtcOffsetAtWallTimeMillis(wallClockAsUtcEpoch: Long): Long {
    val wall = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = wallClockAsUtcEpoch
    }
    val trueUtc = java.util.Calendar.getInstance().apply {
        clear()
        set(java.util.Calendar.YEAR, wall.get(java.util.Calendar.YEAR))
        set(java.util.Calendar.MONTH, wall.get(java.util.Calendar.MONTH))
        set(java.util.Calendar.DAY_OF_MONTH, wall.get(java.util.Calendar.DAY_OF_MONTH))
        set(java.util.Calendar.HOUR_OF_DAY, wall.get(java.util.Calendar.HOUR_OF_DAY))
        set(java.util.Calendar.MINUTE, wall.get(java.util.Calendar.MINUTE))
        set(java.util.Calendar.SECOND, wall.get(java.util.Calendar.SECOND))
        set(java.util.Calendar.MILLISECOND, wall.get(java.util.Calendar.MILLISECOND))
    }.timeInMillis
    return wallClockAsUtcEpoch - trueUtc
}

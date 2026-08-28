package com.diabecarekids.app.platform

import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.domain.UbicacionGps
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform seam. Each supported platform provides its own actual
 * implementations (see Platform.android.kt).
 */

/** Ktor engine used by HTTP clients in production. commonTest uses MockEngine instead. */
expect fun httpClientEngine(): HttpClientEngine

/** Current time as epoch milliseconds (true UTC timeline). */
expect fun epochMillisNow(): Long

/**
 * The current LOCAL wall-clock time interpreted as a UTC epoch. The codebase
 * convention (design decision, see [com.diabecarekids.app.format.formatEpochMillis])
 * stores record timestamps as wall-clock-as-UTC so the pure arithmetic formatter
 * renders the user's local wall clock without a kotlinx-datetime dependency. This
 * seam converts "now" onto that same wall-clock-as-UTC timeline, mirroring
 * [epochMillisNow] (ID-TZ).
 */
expect fun wallClockAsUtcEpochNow(): Long

/**
 * Epoch milliseconds for the given local wall-clock time today, in the
 * platform's current timezone (DST-aware). Mirrors the [epochMillisNow] seam
 * (design D1); used by [com.diabecarekids.app.domain.ReminderScheduleEngine].
 */
expect fun todayAtLocalTimeMillis(hour: Int, minute: Int): Long

/**
 * Epoch milliseconds for the given local wall-clock time `dayOffset` days from
 * today, in the platform's current timezone (DST-aware). `dayOffset == 0`
 * matches [todayAtLocalTimeMillis]; positive/negative offsets shift the calendar
 * day before applying the time. Used to re-arm a daily meal reminder for the next
 * day (ID-REARM).
 */
expect fun localTimeAtDayOffsetMillis(hour: Int, minute: Int, dayOffset: Int): Long

/**
 * The device's UTC offset (ms) that applies at the LOCAL WALL time encoded by the
 * wall-clock-as-UTC epoch [wallClockAsUtcEpoch], i.e. the offset at that wall
 * date/time — NOT the offset at "now". DST-aware: across a transition this differs
 * from the current offset, which is exactly what keeps the WorkManager delay
 * real-elapsed correct (ID-DST-DELAY).
 */
expect fun wallClockAsUtcOffsetAtWallTimeMillis(wallClockAsUtcEpoch: Long): Long

/**
 * True-UTC epoch of a wall-clock-as-UTC timestamp, given the device's UTC offset
 * that applies at that WALL time. On the wall-clock-as-UTC axis a local wall time
 * `L` is stored as the epoch of `L`-as-if-UTC; subtracting the offset at `L`
 * re-expresses it on the true-UTC (real elapsed) axis. Pure, so it is testable in
 * commonTest with an injected offset.
 */
fun wallClockAsUtcToTrueUtc(wallClockAsUtcEpoch: Long, utcOffsetAtWallTimeMillis: Long): Long =
    wallClockAsUtcEpoch - utcOffsetAtWallTimeMillis

/**
 * Real-elapsed delay (ms) for WorkManager to fire a wall-clock-as-UTC trigger at
 * [triggerAt]. [triggerUtcOffsetMillis] is the device offset at the trigger's WALL
 * time (see [wallClockAsUtcOffsetAtWallTimeMillis]); [realNowMillis] is true-UTC
 * now ([android.os.SystemClock]/System.currentTimeMillis axis). The result is the
 * TRUE-UTC span between now and the trigger — NOT the nominal wall-clock span — so
 * it stays correct on a spring-forward (23h) / fall-back (25h) day (ID-DST-DELAY).
 * Using the CURRENT offset here would cancel out and reproduce the bug; only the
 * trigger-time offset yields the real elapsed span.
 */
fun reminderDelayMillis(
    triggerAt: Long,
    triggerUtcOffsetMillis: Long,
    realNowMillis: Long,
): Long = (wallClockAsUtcToTrueUtc(triggerAt, triggerUtcOffsetMillis) - realNowMillis)
    .coerceAtLeast(0L)

/**
 * Schedules offline background work that fires a local meal reminder at
 * [triggerAt], and cancels all such work. Implemented by the platform bridge
 * (WorkManager in composeApp androidMain — Slice 2).
 */
interface MealReminderScheduler {
    fun schedule(tipo: TipoComida, triggerAt: Long)
    fun cancelAll()
}

/**
 * Captures a meal photo (REQ-MEAL-004 / INV-005). Returns a temp URI string,
 * or null when the user cancels or no photo is taken.
 */
interface PhotoCapture {
    suspend fun takePhoto(): String?

    /** True when the most recent [takePhoto] was blocked by a denied camera runtime permission. */
    val cameraDenied: StateFlow<Boolean>

    /** Reads and clears [cameraDenied]. Call after surfacing the denial to the user. */
    fun consumeCameraDenied(): Boolean
}

/**
 * Schedules a follow-up reminder ~2h after a meal (T2 stage).
 */
interface PostprandialAlarmScheduler {
    fun schedule(mealId: String)
    fun cancel(mealId: String)
}

/**
 * Captures the device's current GPS fix for an SOS alert (REQ-SOS-003).
 * Returns null when location permissions are denied or coordinates are
 * unavailable — the alert must still fire with null location data.
 */
interface LocationProvider {
    suspend fun currentLocation(): UbicacionGps?
}

/**
 * System haptic feedback on a successful SOS activation (REQ-SOS-001 SHOULD).
 */
interface Haptics {
    fun vibrateSosTriggered()
}

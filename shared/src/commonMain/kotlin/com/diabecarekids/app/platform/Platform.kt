package com.diabecarekids.app.platform

import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform seam. Each supported platform provides its own actual
 * implementations (see Platform.android.kt).
 */

/** Ktor engine used by HTTP clients in production. commonTest uses MockEngine instead. */
expect fun httpClientEngine(): HttpClientEngine

/** Current time as epoch milliseconds. */
expect fun epochMillisNow(): Long

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

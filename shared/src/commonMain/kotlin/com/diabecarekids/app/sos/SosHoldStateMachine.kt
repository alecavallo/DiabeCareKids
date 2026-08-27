package com.diabecarekids.app.sos

import com.diabecarekids.app.platform.epochMillisNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A hold-state value exposed by [SosHoldStateMachine]. [progress] is the
 * completed-fraction of the hold (0.0..1.0) driving the visual progress ring.
 */
sealed interface SosState {
    val progress: Float

    /** No hold in progress. */
    data object Idle : SosState {
        override val progress: Float = 0f
    }

    /** User is holding; elapsed time below the arming threshold. */
    data class Pressing(override val progress: Float) : SosState

    /**
     * Point of no return (INV-003): elapsed >= hold duration. Reached during a
     * tick; subsequent [SosHoldStateMachine.holdEnd] is a no-op ("regardless of
     * subsequent release" boundary scenario). Awaiting
     * [SosHoldStateMachine.onTriggerConfirmed].
     */
    data object Arming : SosState {
        override val progress: Float = 1f
    }

    /** Fire pipeline completed. */
    data object Triggered : SosState {
        override val progress: Float = 1f
    }
}

/**
 * Pure, clock-injected state machine enforcing INV-003 (REQ-SOS-001): an
 * emergency alert triggers only after a continuous hold reaching exactly
 * [holdDurationMillis]. Releasing before that resets progress to 0% with no
 * trigger; releasing after arming is ignored.
 *
 * UI-agnostic: the gesture layer reports only [holdStart]/[onTick]/[holdEnd];
 * the machine does not read the wall clock directly — it uses the injected
 * [clock] so synthetic clocks make timing tests deterministic and fully
 * offline (design decision #2).
 *
 * Transitions: Idle → Pressing(progress) → Arming → Triggered.
 */
class SosHoldStateMachine(
    private val clock: () -> Long = ::epochMillisNow,
    private val holdDurationMillis: Long = DEFAULT_HOLD_DURATION_MILLIS,
) {
    private val _state = MutableStateFlow<SosState>(SosState.Idle)
    val state: StateFlow<SosState> = _state.asStateFlow()

    private var holdStartedAt: Long? = null

    /** Begins a hold. No-op unless currently idle. */
    fun holdStart() {
        if (_state.value !is SosState.Idle) return
        holdStartedAt = clock()
        _state.value = SosState.Pressing(0f)
    }

    /**
     * Advances progress from the elapsed time. At exactly [holdDurationMillis]
     * the machine arms (point of no return) — INV-003 boundary scenario.
     */
    fun onTick() {
        val current = _state.value
        if (current !is SosState.Pressing) return
        val startedAt = holdStartedAt ?: return
        val elapsed = (clock() - startedAt).coerceAtLeast(0L)
        _state.value = if (elapsed >= holdDurationMillis) {
            SosState.Arming
        } else {
            SosState.Pressing((elapsed.toFloat() / holdDurationMillis).coerceIn(0f, 1f))
        }
    }

    /**
     * Ends the hold. Resets to [SosState.Idle] (0% progress, no trigger) if
     * still pressing; no-op once armed or triggered (INV-003 boundary).
     */
    fun holdEnd() {
        if (_state.value is SosState.Pressing) {
            holdStartedAt = null
            _state.value = SosState.Idle
        }
    }

    /** Advances [SosState.Arming] → [SosState.Triggered] once the pipeline ran. */
    fun onTriggerConfirmed() {
        if (_state.value is SosState.Arming) {
            holdStartedAt = null
            _state.value = SosState.Triggered
        }
    }

    /** Returns to [SosState.Idle] unconditionally (e.g. screen exit). */
    fun reset() {
        holdStartedAt = null
        _state.value = SosState.Idle
    }

    private companion object {
        const val DEFAULT_HOLD_DURATION_MILLIS: Long = 3000L
    }
}

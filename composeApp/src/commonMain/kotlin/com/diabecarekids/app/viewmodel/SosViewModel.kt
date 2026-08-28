package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.sos.SosController
import com.diabecarekids.app.sos.SosState
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin event-forwarding binder for the SOS screen (design decision #7). It does
 * NOT own a tick loop or coroutines: the UI gesture layer reports
 * start/tick/end, which the binder forwards to the [SosController] (the machine
 * owner). The controller's [state] exposes the machine [SosState], giving the
 * UI both the progress fraction and the triggered/confirmation signal.
 *
 * Keep this class a pure binder: all INV-003 (REQ-SOS-001) logic lives in the
 * shared [SosHoldStateMachine]; this layer adds no timing or trigger rules so
 * it stays trivially testable and matches the manual-DI pattern.
 */
class SosViewModel(
    private val controller: SosController,
) {
    /** Machine state: progress 0..1 via [SosState.progress], confirmation via [SosState.Triggered]. */
    val state: StateFlow<SosState> = controller.state

    fun onHoldStart() = controller.onHoldStart()

    fun onTick() = controller.onTick()

    fun onHoldEnd() = controller.onHoldEnd()

    /** Returns to Idle unconditionally (screen exit / back). */
    fun reset() = controller.reset()
}

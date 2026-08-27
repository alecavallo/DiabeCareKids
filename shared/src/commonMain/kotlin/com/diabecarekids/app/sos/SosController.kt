package com.diabecarekids.app.sos

import com.diabecarekids.app.domain.Emergencia
import com.diabecarekids.app.domain.EmergenciaEstado
import com.diabecarekids.app.persistence.EmergenciaStore
import com.diabecarekids.app.platform.Haptics
import com.diabecarekids.app.platform.LocationProvider
import com.diabecarekids.app.platform.epochMillisNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the [SosHoldStateMachine] and runs the SOS fire pipeline (REQ-SOS-002,
 * 003, 004): capture location → build `Emergencia(ACTIVA)` → `store.save` →
 * `notifier.notifyAllGuardians` → `haptics.vibrateSosTriggered` →
 * `machine.onTriggerConfirmed` → UI confirmation.
 *
 * The pipeline is fired once, on the guarded Arming-entry transition (design
 * decision #1), so `onTick` is cheap on every other tick. A null location fix
 * never blocks or crashes the alert: the record is saved and guardians are
 * notified with null coordinates (REQ-SOS-003).
 *
 * **ID generation**: [idGenerator] is injected (`() -> String`) rather than
 * using `kotlin.uuid.Uuid` (experimental in Kotlin 2.0.21) so identifiers stay
 * portable across platforms and deterministic in tests. The default derives a
 * per-trigger value from the epoch clock, which is unique enough for an
 * in-memory store in this change.
 */
class SosController(
    private val machine: SosHoldStateMachine,
    private val store: EmergenciaStore,
    private val locationProvider: LocationProvider,
    private val notifier: GuardianNotifier,
    private val haptics: Haptics,
    private val scope: CoroutineScope,
    private val patientId: String,
    private val patientName: String,
    private val idGenerator: () -> String = { "sos-${epochMillisNow()}" },
) {
    val state: StateFlow<SosState> = machine.state

    fun onHoldStart() = machine.holdStart()

    fun onTick() {
        val wasArming = machine.state.value is SosState.Arming
        machine.onTick()
        val isArming = machine.state.value is SosState.Arming
        if (isArming && !wasArming) {
            scope.launch { fire() }
        }
    }

    fun onHoldEnd() = machine.holdEnd()

    fun reset() = machine.reset()

    private suspend fun fire() {
        // REQ-SOS-003: a null fix (denied/unavailable) yields null coordinates,
        // never blocks the alert.
        val ubicacion = locationProvider.currentLocation()
        val emergencia = Emergencia(
            id = idGenerator(),
            id_paciente = patientId,
            nombre_paciente = patientName,
            fecha_hora = epochMillisNow(),
            ubicacion = ubicacion,
            estado = EmergenciaEstado.ACTIVA,
        )
        store.save(emergencia)
        notifier.notifyAllGuardians(emergencia)
        haptics.vibrateSosTriggered()
        machine.onTriggerConfirmed()
    }
}

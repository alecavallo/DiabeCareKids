package com.diabecarekids.app.domain

import kotlinx.serialization.Serializable

/**
 * An active emergency alert record (CAP-001 / REQ-SOS-002).
 *
 * Schema mirrors the emergency-alert document. [fecha_hora] is epoch
 * milliseconds; [ubicacion] carries the GPS fix captured at trigger time, or
 * null when location permissions were denied / coordinates were unavailable
 * (REQ-SOS-003 — the alert never blocks on a missing fix). [estado] is always
 * [EmergenciaEstado.ACTIVA] at creation; resolution to a later state is a
 * future change (the RESUELTA lifecycle is out of scope here).
 */
@Serializable
data class Emergencia(
    val id: String,
    /** Identifier of the patient that triggered the alert. */
    val id_paciente: String,
    /** Display name of the patient (used by guardian notifications). */
    val nombre_paciente: String,
    /** Trigger time as epoch milliseconds. */
    val fecha_hora: Long,
    /** GPS fix captured on trigger, or null when unavailable/denied (REQ-SOS-003). */
    val ubicacion: UbicacionGps? = null,
    val estado: EmergenciaEstado = EmergenciaEstado.ACTIVA,
)

/** Lifecycle state of an [Emergencia] record (REQ-SOS-002). */
@Serializable
enum class EmergenciaEstado {
    ACTIVA,
}

/**
 * A GPS coordinate fix (REQ-SOS-003): latitude/longitude in degrees and the
 * reported horizontal accuracy in meters.
 */
@Serializable
data class UbicacionGps(
    val latitud: Double,
    val longitud: Double,
    val precision_metros: Double? = null,
)

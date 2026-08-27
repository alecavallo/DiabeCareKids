package com.diabecarekids.app.domain

import kotlinx.serialization.Serializable

/**
 * Scheduled meal reminder configuration (Reminder Eligibility Gate).
 *
 * Habitual meal times are stored as strict "HH:mm" wall-clock strings (one per
 * primary meal type). [ventana_anticipacion_minutos] is the advance window
 * subtracted from the habitual time to compute the trigger, and
 * [recordatorios_activos] is the global kill switch.
 *
 * Note: there is intentionally no slot for COLACION (INV-006). [TipoComida] has
 * no COLACION value by design, so the primary meal set is structurally limited.
 */
@Serializable
data class ConfiguracionHorarios(
    val horario_desayuno: String,
    val horario_almuerzo: String,
    val horario_merienda: String,
    val horario_cena: String,
    val ventana_anticipacion_minutos: Int = 15,
    val recordatorios_activos: Boolean = true,
) {
    /**
     * The habitual "HH:mm" time for [tipo]. [tipo] is a primary meal type, so
     * this is total for every enum value.
     */
    fun horarioFor(tipo: TipoComida): String = when (tipo) {
        TipoComida.DESAYUNO -> horario_desayuno
        TipoComida.ALMUERZO -> horario_almuerzo
        TipoComida.MERIENDA -> horario_merienda
        TipoComida.CENA -> horario_cena
    }
}

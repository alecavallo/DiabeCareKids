package com.diabecarekids.app.domain

import kotlinx.serialization.Serializable

/**
 * A meal log entry for two-stage meal logging (T0 immediately after the meal,
 * T2 during follow-up ~2h later).
 *
 * Schema mirrors the Firestore meal-log document. T0 fields
 * ([fecha_hora_inicio] through [fuente_carbohidratos]) are set at creation;
 * T2 fields ([porcentaje_consumido], [carbohidratos_reales],
 * [glicemia_postprandial_2h], [foto_despues_url]) are populated during follow-up.
 *
 * [carbohidratos_reales] is stored as a raw [Double] with no rounding; UI
 * formatting (1-decimal display) is a presentation concern handled in the UI
 * layer (design decision carried).
 */
@Serializable
data class RegistroComida(
    val id: String,
    /** Meal start time as epoch milliseconds (Firestore Timestamp deferred to the Firestore adapter). */
    val fecha_hora_inicio: Long,
    val tipo_comida: TipoComida,
    val glicemia_inicial: Double,
    val nombre_alimento: String,
    val carbohidratos_estimados: Double,
    val fuente_carbohidratos: CarbSource,
    val foto_antes_url: String? = null,
    val foto_despues_url: String? = null,
    /** Percent of the meal consumed (T2). Defaults to 100 on T2 open (design decision carried). */
    val porcentaje_consumido: Int = 100,
    val carbohidratos_reales: Double? = null,
    val glicemia_postprandial_2h: Double? = null,
    val es_registro_historico: Boolean = false,
    val creado_por_usuario_id: String,
    /** Last modification time as epoch milliseconds. */
    val ultima_modificacion: Long,
)

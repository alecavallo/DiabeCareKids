package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.calcularCarbohidratosReales
import com.diabecarekids.app.persistence.PersistenceStore
import com.diabecarekids.app.platform.epochMillisNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Formats a carb [Double] without trailing decimals when integral (e.g. 50, not 50.0). */
private fun formatCarbs(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** UI state for editing an existing historical record (R3). */
data class EditRecordState(
    val registro: RegistroComida,
    val carbInput: String = formatCarbs(registro.carbohidratos_estimados),
    val consumedPercent: Int = registro.porcentaje_consumido,
    val bgPost2h: String = "",
    val realCarbsPreview: Double = calcularCarbohidratosReales(registro.carbohidratos_estimados, registro.porcentaje_consumido),
    val isSaving: Boolean = false,
    val error: String? = null,
)

/**
 * Edits an existing historical record (R3): carb estimate, consumed %, and 2h
 * postprandial BG. Recalculates [RegistroComida.carbohidratos_reales] via
 * CarbMath and persists with [PersistenceStore.update]. Editing the carb
 * estimate flips [fuente_carbohidratos] to MANUAL (provenance stays honest —
 * design decision). A blank 2h-BG input leaves the stored value unchanged.
 */
class EditRecordViewModel(
    private val store: PersistenceStore,
    private val scope: CoroutineScope,
    registro: RegistroComida,
) {
    private val _state = MutableStateFlow(EditRecordState(registro = registro))
    val state: StateFlow<EditRecordState> = _state.asStateFlow()

    private val _updated = MutableStateFlow<RegistroComida?>(null)
    val updated: StateFlow<RegistroComida?> = _updated.asStateFlow()

    /** Clears the "updated" signal once the UI has navigated. */
    fun onUpdatedConsumed() {
        _updated.value = null
    }

    fun onCarbInputChange(value: String) {
        _state.update {
            val carbs = value.toDoubleOrNull()
            it.copy(
                carbInput = value,
                realCarbsPreview = calcularCarbohidratosReales(carbs ?: 0.0, it.consumedPercent),
                error = null,
            )
        }
        // Edited estimates are no longer USDA/Gemini data — provenance stays honest.
        _state.update { it.copy(registro = it.registro.copy(fuente_carbohidratos = CarbSource.MANUAL)) }
    }

    fun onConsumedPercentChange(percent: Int) {
        _state.update {
            val carbs = it.carbInput.toDoubleOrNull() ?: it.registro.carbohidratos_estimados
            it.copy(
                consumedPercent = percent,
                realCarbsPreview = calcularCarbohidratosReales(carbs, percent),
                error = null,
            )
        }
    }

    fun onBgPost2hChange(value: String) {
        _state.update { it.copy(bgPost2h = value.filter { c -> c.isDigit() || c == '.' }, error = null) }
    }

    /** Persists the edited record: recalculated reals, MANUAL source when the
     *  carb estimate changed, 2h BG kept when the input is blank (R3). */
    fun save() {
        val s = _state.value
        val carbs = s.carbInput.toDoubleOrNull()
        if (carbs == null || carbs < 0) {
            _state.update { it.copy(error = "Enter a valid carb amount") }
            return
        }
        val bgText = s.bgPost2h.trim()
        val bg = if (bgText.isEmpty()) null else bgText.toDoubleOrNull()?.takeIf { it >= 0 }
        if (bgText.isNotEmpty() && bg == null) {
            _state.update { it.copy(error = "Enter valid 2-hour blood glucose") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        scope.launch {
            val now = epochMillisNow()
            val recalc = calcularCarbohidratosReales(carbs, s.consumedPercent)
            val carbChanged = carbs != s.registro.carbohidratos_estimados
            val updatedRegistro = s.registro.copy(
                carbohidratos_estimados = carbs,
                porcentaje_consumido = s.consumedPercent,
                carbohidratos_reales = recalc,
                glicemia_postprandial_2h = bg ?: s.registro.glicemia_postprandial_2h,
                fuente_carbohidratos = if (carbChanged) CarbSource.MANUAL else s.registro.fuente_carbohidratos,
                ultima_modificacion = now,
            )
            store.update(updatedRegistro)
            _updated.value = updatedRegistro
            _state.update { it.copy(isSaving = false) }
        }
    }
}

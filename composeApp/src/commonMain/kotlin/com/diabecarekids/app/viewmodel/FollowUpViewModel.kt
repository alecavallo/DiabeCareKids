package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.calcularCarbohidratosReales
import com.diabecarekids.app.persistence.PersistenceStore
import com.diabecarekids.app.platform.PhotoCapture
import com.diabecarekids.app.platform.epochMillisNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the T2 postprandial follow-up. */
data class FollowUpState(
    val registro: RegistroComida,
    val intakePercent: Int = 100,
    val bgPost2h: String = "",
    val realCarbsPreview: Double = 0.0,
    val photoUri: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
)

/**
 * T2 postprandial follow-up: intake % slider, 2h blood glucose, live
 * real-carbs preview (REQ-MEAL-003), and an optional after photo (INV-005).
 * On save it updates the existing [RegistroComida] in [PersistenceStore].
 */
class FollowUpViewModel(
    private val store: PersistenceStore,
    private val photoCapture: PhotoCapture,
    private val scope: CoroutineScope,
    registro: RegistroComida,
) {
    private val _state = MutableStateFlow(
        FollowUpState(
            registro = registro,
            realCarbsPreview = calcularCarbohidratosReales(registro.carbohidratos_estimados, 100),
        )
    )
    val state: StateFlow<FollowUpState> = _state.asStateFlow()

    private val _completed = MutableStateFlow<RegistroComida?>(null)
    val completed: StateFlow<RegistroComida?> = _completed.asStateFlow()

    /** Clears the completion signal once the UI has navigated away. */
    fun onCompletedConsumed() {
        _completed.value = null
    }

    /** Updates the intake % slider and recomputes the live real-carbs preview. */
    fun onIntakePercentChange(percent: Int) {
        _state.update {
            it.copy(
                intakePercent = percent,
                realCarbsPreview = calcularCarbohidratosReales(it.registro.carbohidratos_estimados, percent),
                error = null,
            )
        }
    }

    fun onBgPost2hChange(value: String) {
        _state.update { it.copy(bgPost2h = value.filter { c -> c.isDigit() || c == '.' }, error = null) }
    }

    /** Captures an optional after photo; null on cancel (INV-005). */
    fun takePhoto() {
        scope.launch {
            val uri = photoCapture.takePhoto()
            _state.update { it.copy(photoUri = uri) }
        }
    }

    /** Persists the T2 update: intake %, real carbs, 2h BG, optional after photo. */
    fun save() {
        val s = _state.value
        val bg = s.bgPost2h.toDoubleOrNull()
        if (bg == null || bg < 0) {
            _state.update { it.copy(error = "Enter 2-hour blood glucose") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        scope.launch {
            val now = epochMillisNow()
            val updated = s.registro.copy(
                porcentaje_consumido = s.intakePercent,
                carbohidratos_reales = calcularCarbohidratosReales(s.registro.carbohidratos_estimados, s.intakePercent),
                glicemia_postprandial_2h = bg,
                foto_despues_url = s.photoUri,
                ultima_modificacion = now,
            )
            store.update(updated)
            _completed.value = updated
            _state.update { it.copy(isSaving = false) }
        }
    }
}

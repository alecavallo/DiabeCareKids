package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.domain.calcularCarbohidratosReales
import com.diabecarekids.app.nutrition.CarbResolution
import com.diabecarekids.app.nutrition.NutritionRepository
import com.diabecarekids.app.persistence.PersistenceStore
import com.diabecarekids.app.platform.epochMillisNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** UI state for the add-past-record form (R1, R4). */
data class AddPastRecordState(
    val dateTimeEpochMillis: Long = epochMillisNow(),
    val mealType: TipoComida = TipoComida.ALMUERZO,
    val foodQuery: String = "",
    val carbInput: String = "",
    val source: CarbSource? = null,
    val sourceLabel: String? = null,
    val bgInitial: String = "",
    val consumedPercent: Int = 100,
    val isResolving: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)

/**
 * Historical past-record form (R1, R4): lets a guardian log a meal for a past
 * date/time. The date/time is an epoch-millis [Long] chosen in the UI; the
 * ViewModel only sees the resulting value (wall-clock-as-UTC, no
 * kotlinx-datetime — design decision). Carb lookup is optional ([resolve]);
 * manual carb entry is always allowed (R4). On save it persists a
 * [RegistroComida] flagged [es_registro_historico]=true with null photos (R1).
 */
class AddPastRecordViewModel(
    private val repository: NutritionRepository,
    private val store: PersistenceStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AddPastRecordState())
    val state: StateFlow<AddPastRecordState> = _state.asStateFlow()

    private val _saved = MutableStateFlow<RegistroComida?>(null)
    val saved: StateFlow<RegistroComida?> = _saved.asStateFlow()

    /** Clears the "saved" signal once the UI has navigated. */
    fun onSavedConsumed() {
        _saved.value = null
    }

    fun onDateTimeChange(millis: Long) {
        _state.update { it.copy(dateTimeEpochMillis = millis, error = null) }
    }

    fun onMealTypeChange(type: TipoComida) {
        _state.update { it.copy(mealType = type) }
    }

    fun onFoodQueryChange(query: String) {
        _state.update { it.copy(foodQuery = query, error = null) }
    }

    /** The carb field is always editable, regardless of resolution source (R4). */
    fun onCarbInputChange(value: String) {
        _state.update { it.copy(carbInput = value, error = null) }
    }

    fun onBgInitialChange(value: String) {
        _state.update { it.copy(bgInitial = value.filter { c -> c.isDigit() || c == '.' }, error = null) }
    }

    fun onConsumedPercentChange(percent: Int) {
        _state.update { it.copy(consumedPercent = percent, error = null) }
    }

    /** Optional USDA/Gemini carb lookup (R4): manual entry is never required. */
    fun resolve() {
        val query = _state.value.foodQuery.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(error = "Enter a food name first") }
            return
        }
        _state.update { it.copy(isResolving = true, error = null) }
        scope.launch {
            when (val result = repository.resolveCarbs(query)) {
                is CarbResolution.Resolved -> _state.update {
                    it.copy(
                        foodQuery = query,
                        carbInput = formatCarbs(result.carbsGrams),
                        source = result.source,
                        sourceLabel = result.label,
                        isResolving = false,
                        error = null,
                    )
                }
                CarbResolution.ManualRequired -> _state.update {
                    it.copy(source = CarbSource.MANUAL, sourceLabel = MANUAL_LABEL, isResolving = false, error = null)
                }
            }
        }
    }

    /** Validates and persists a historical record (R1, R4). */
    fun save() {
        val s = _state.value
        val name = s.foodQuery.trim()
        val carbs = s.carbInput.toDoubleOrNull()
        val bg = s.bgInitial.toDoubleOrNull()
        when {
            name.isEmpty() -> { _state.update { it.copy(error = "Enter a food name") }; return }
            carbs == null || carbs < 0 -> { _state.update { it.copy(error = "Enter a valid carb amount") }; return }
            bg == null || bg < 0 -> { _state.update { it.copy(error = "Enter initial blood glucose") }; return }
        }
        _state.update { it.copy(isSaving = true, error = null) }
        scope.launch {
            val registro = newRegistro(name, carbs, bg, s.dateTimeEpochMillis, s.consumedPercent)
            store.save(registro)
            _saved.value = registro
            _state.update { it.copy(isSaving = false) }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newRegistro(name: String, carbs: Double, bg: Double, whenMillis: Long, consumed: Int): RegistroComida {
        val now = epochMillisNow()
        return RegistroComida(
            id = Uuid.random().toString(),
            fecha_hora_inicio = whenMillis,
            tipo_comida = _state.value.mealType,
            glicemia_inicial = bg,
            nombre_alimento = name,
            carbohidratos_estimados = carbs,
            fuente_carbohidratos = _state.value.source ?: CarbSource.MANUAL,
            foto_antes_url = null,
            foto_despues_url = null,
            porcentaje_consumido = consumed,
            carbohidratos_reales = calcularCarbohidratosReales(carbs, consumed),
            glicemia_postprandial_2h = null,
            es_registro_historico = true,
            creado_por_usuario_id = DEFAULT_USER_ID,
            ultima_modificacion = now,
        )
    }

    private companion object {
        const val MANUAL_LABEL = "Manual"
        const val DEFAULT_USER_ID = "local"
        fun formatCarbs(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}

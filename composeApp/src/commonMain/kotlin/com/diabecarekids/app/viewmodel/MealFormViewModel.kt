package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.FoodItem
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.persistence.PersistenceStore
import com.diabecarekids.app.platform.PhotoCapture
import com.diabecarekids.app.platform.PostprandialAlarmScheduler
import com.diabecarekids.app.nutrition.CarbResolution
import com.diabecarekids.app.nutrition.NutritionRepository
import com.diabecarekids.app.platform.wallClockAsUtcEpochNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Quick-select blood glucose (mg/dL) chips offered on the T0 form. */
val BG_QUICK_CHIPS = listOf(80, 100, 120, 150, 200)

/** UI state for the T0 meal-entry form. */
data class MealFormState(
    val foodQuery: String = "",
    val suggestions: List<FoodItem> = emptyList(),
    val selectedFoodName: String? = null,
    val carbInput: String = "",
    val source: CarbSource? = null,
    val sourceLabel: String? = null,
    val bgInitial: String = "",
    val mealType: TipoComida = TipoComida.ALMUERZO,
    val photoUri: String? = null,
    val isResolving: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)

/**
 * T0 meal form: food-name lookup with USDA/Gemini suggestion + resolution,
 * quick-select BG chips, an always-editable carb field (INV-002), and an
 * optional before photo (INV-005). On save it persists via [PersistenceStore]
 * and schedules the 2h postprandial alarm.
 */
class MealFormViewModel(
    private val repository: NutritionRepository,
    private val store: PersistenceStore,
    private val photoCapture: PhotoCapture,
    private val alarmScheduler: PostprandialAlarmScheduler,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(MealFormState())
    val state: StateFlow<MealFormState> = _state.asStateFlow()

    private val _savedMeal = MutableStateFlow<RegistroComida?>(null)
    val savedMeal: StateFlow<RegistroComida?> = _savedMeal.asStateFlow()

    /** In-flight suggestion lookups, cancelled on keystroke/reset (ID-RACE, ID-RESET-CANCEL). */
    private var suggestJob: Job? = null

    /** In-flight carb-resolution work, cancelled on reset (ID-RESET-CANCEL). */
    private var resolveJob: Job? = null

    /** Clears the "saved" signal once the UI has navigated. */
    fun onSavedMealConsumed() {
        _savedMeal.value = null
    }

    /** Resets the form to a pristine state so the next meal starts clean (ID-LEAK).
     *  Cancels in-flight suggestion/resolution work so a slow response cannot
     *  repopulate the fresh form (ID-RESET-CANCEL). */
    fun reset() {
        suggestJob?.cancel()
        resolveJob?.cancel()
        suggestJob = null
        resolveJob = null
        _state.value = MealFormState()
    }

    fun onFoodQueryChange(query: String) {
        _state.update { it.copy(foodQuery = query, error = null) }
        // Only the latest query may drive suggestions; cancel the prior in-flight
        // lookup so an out-of-order (stale) response never overwrites (ID-RACE).
        suggestJob?.cancel()
        if (query.isBlank()) {
            suggestJob = null
            _state.update { it.copy(suggestions = emptyList(), isResolving = false) }
            return
        }
        _state.update { it.copy(isResolving = true) }
        suggestJob = scope.launch {
            val suggestions = repository.suggestFoods(query.trim())
            _state.update { it.copy(suggestions = suggestions, isResolving = false) }
        }
    }

    /** Picks a suggestion card: fills the name + editable carb estimate (INV-002). */
    fun selectSuggestion(food: FoodItem) {
        _state.update {
            it.copy(
                foodQuery = food.name,
                selectedFoodName = food.name,
                carbInput = formatCarbs(food.carbsGrams),
                source = food.source,
                sourceLabel = if (food.source == CarbSource.GEMINI_AI) AI_LABEL else null,
                suggestions = emptyList(),
                error = null,
            )
        }
    }

    /** Resolves carbs for the current query (USDA → Gemini → manual). */
    fun resolve() {
        val query = _state.value.foodQuery.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(error = "Enter a food name first") }
            return
        }
        _state.update { it.copy(isResolving = true, error = null) }
        resolveJob?.cancel()
        resolveJob = scope.launch {
            when (val result = repository.resolveCarbs(query)) {
                is CarbResolution.Resolved -> _state.update {
                    it.copy(
                        selectedFoodName = query,
                        carbInput = formatCarbs(result.carbsGrams),
                        source = result.source,
                        sourceLabel = result.label,
                        isResolving = false,
                        error = null,
                    )
                }
                CarbResolution.ManualRequired -> _state.update {
                    it.copy(
                        source = CarbSource.MANUAL,
                        sourceLabel = MANUAL_LABEL,
                        isResolving = false,
                        error = null,
                    )
                }
            }
        }
    }

    /** The carb field is always editable, regardless of resolution source (INV-002). */
    fun onCarbInputChange(value: String) {
        _state.update { it.copy(carbInput = value, error = null) }
    }

    fun onBgInitialChange(value: String) {
        _state.update { it.copy(bgInitial = value.filter { c -> c.isDigit() || c == '.' }, error = null) }
    }

    fun onMealTypeChange(type: TipoComida) {
        _state.update { it.copy(mealType = type) }
    }

    /** Captures an optional before photo; null on cancel (INV-005). A cancelled
     *  capture must NOT erase a photo already captured this session. A denied
     *  camera runtime permission surfaces a friendly error instead of a crash. */
    fun takePhoto() {
        scope.launch {
            val uri = photoCapture.takePhoto()
            if (uri != null) {
                // A successful capture clears any stale camera-denied error (ID-ERR-CLEAR).
                _state.update { it.copy(photoUri = uri, error = null) }
            } else if (photoCapture.consumeCameraDenied()) {
                _state.update { it.copy(error = CAMERA_DENIED_MESSAGE) }
            }
        }
    }

    /** Persists the meal and schedules the 2h alarm. Carb value persisted as edited. */
    fun save() {
        val s = _state.value
        val query = s.foodQuery.trim()
        val carbs = s.carbInput.toDoubleOrNull()
        val bg = s.bgInitial.toDoubleOrNull()
        when {
            query.isEmpty() -> { _state.update { it.copy(error = "Enter a food name") }; return }
            carbs == null || carbs < 0 -> { _state.update { it.copy(error = "Enter a valid carb amount") }; return }
            bg == null || bg < 0 -> { _state.update { it.copy(error = "Enter initial blood glucose") }; return }
        }
        _state.update { it.copy(isSaving = true, error = null) }
        scope.launch {
            // Wall-clock-as-UTC (ID-TZ): store the local wall clock interpreted as
            // UTC so the shared formatter renders the user's local time without a
            // timezone offset, consistent with advanced-history / PDF handling.
            val now = wallClockAsUtcEpochNow()
            val registro = newRegistro(query, carbs, bg, now)
            store.save(registro)
            alarmScheduler.schedule(registro.id)
            _savedMeal.value = registro
            // Reset the form so the next meal does not leak this meal's photo/query/carbs (ID-LEAK).
            reset()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newRegistro(query: String, carbs: Double, bg: Double, now: Long) =
        RegistroComida(
            id = Uuid.random().toString(),
            fecha_hora_inicio = now,
            tipo_comida = _state.value.mealType,
            glicemia_inicial = bg,
            nombre_alimento = query,
            carbohidratos_estimados = carbs,
            fuente_carbohidratos = _state.value.source ?: CarbSource.MANUAL,
            foto_antes_url = _state.value.photoUri,
            foto_despues_url = null,
            porcentaje_consumido = 100,
            carbohidratos_reales = null,
            glicemia_postprandial_2h = null,
            es_registro_historico = false,
            creado_por_usuario_id = DEFAULT_USER_ID,
            ultima_modificacion = now,
        )

    private companion object {
        const val AI_LABEL = "[AI Estimated]"
        const val MANUAL_LABEL = "Manual"
        const val DEFAULT_USER_ID = "local"
        const val CAMERA_DENIED_MESSAGE = "Camera permission denied. Grant camera access to add a photo."
        fun formatCarbs(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}

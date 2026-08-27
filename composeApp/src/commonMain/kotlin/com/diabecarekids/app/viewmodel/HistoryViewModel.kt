package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.persistence.PersistenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the historical timeline (R2). */
data class HistoryState(
    val records: List<RegistroComida> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Historical timeline (R2). Loads all records via [PersistenceStore.getAll] and
 * applies the display policy: newest first (`fecha_hora_inicio` descending).
 * The store stays dumb; sorting is a ViewModel concern (design decision).
 *
 * The ViewModel is created per navigation, so entering the History branch
 * always reloads the store fresh (refresh via per-navigation creation). [reload]
 * is also exposed for explicit refreshes.
 */
class HistoryViewModel(
    private val store: PersistenceStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(HistoryState(isLoading = true))
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init {
        reload()
    }

    /** (Re)loads all records sorted newest-first (R2). */
    fun reload() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        scope.launch {
            val records = store.getAll().sortedByDescending { it.fecha_hora_inicio }
            _state.value = HistoryState(records = records, isLoading = false)
        }
    }
}

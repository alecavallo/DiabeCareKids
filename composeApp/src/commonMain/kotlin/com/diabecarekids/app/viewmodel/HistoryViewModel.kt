package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.export.PdfExportOutcome
import com.diabecarekids.app.export.PdfReportExporter
import com.diabecarekids.app.export.ReportShareLauncher
import com.diabecarekids.app.export.buildReportData
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
    /** True while a PDF export is in progress (CAP-004). */
    val isExporting: Boolean = false,
    /** Non-null when the last export failed (CAP-004); share is never triggered. */
    val exportError: String? = null,
)

/**
 * Historical timeline (R2). Loads all records via [PersistenceStore.getAll] and
 * applies the display policy: newest first (`fecha_hora_inicio` descending).
 * The store stays dumb; sorting is a ViewModel concern (design decision).
 *
 * The ViewModel is created per navigation, so entering the History branch
 * always reloads the store fresh (refresh via per-navigation creation). [reload]
 * is also exposed for explicit refreshes.
 *
 * PDF export (CAP-004): [exportReport] runs the pure shared [buildReportData]
 * over the already-loaded records, hands the result to the [PdfReportExporter],
 * and only on [PdfExportOutcome.Success] forwards the URI to the
 * [ReportShareLauncher]. A [PdfExportOutcome.Failure] surfaces through
 * [HistoryState.exportError] and never shares (safe-behavior contract).
 */
class HistoryViewModel(
    private val store: PersistenceStore,
    private val exporter: PdfReportExporter,
    private val shareLauncher: ReportShareLauncher,
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

    /**
     * Builds, renders and shares a PDF report for records within
     * `[fromMillis, toMillis]` (inclusive). Ignores a re-entry while an export
     * is already in flight.
     */
    fun exportReport(from: Long, to: Long) {
        if (_state.value.isExporting) return
        _state.value = _state.value.copy(isExporting = true, exportError = null)
        scope.launch {
            val data = buildReportData(_state.value.records, from, to)
            when (val outcome = exporter.export(data)) {
                is PdfExportOutcome.Success -> {
                    shareLauncher.sharePdf(outcome.uri)
                    _state.value = _state.value.copy(isExporting = false)
                }
                is PdfExportOutcome.Failure -> {
                    _state.value = _state.value.copy(isExporting = false, exportError = outcome.message)
                }
            }
        }
    }
}

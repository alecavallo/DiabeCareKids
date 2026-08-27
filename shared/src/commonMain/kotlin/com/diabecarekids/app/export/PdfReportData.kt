package com.diabecarekids.app.export

import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.format.formatEpochMillis
import com.diabecarekids.app.format.formatGrams

/**
 * A single rendered row of the clinical meal report (CAP-004). All values are
 * pre-formatted strings so the platform renderer stays dumb and the mapping is
 * fully covered by commonTest (design DECISION: two-step build (pure) + render
 * (platform)). Nullable record fields render as "—" to match the HistoryRow UI.
 */
data class PdfReportRow(
    val dateText: String,
    val preMealBgText: String,
    val twoHourBgText: String,
    val realCarbsText: String,
)

/** The fully-prepared report handed to a [PdfReportExporter] for rendering. */
data class PdfReportData(
    val title: String,
    val rangeLabel: String,
    val rows: List<PdfReportRow>,
)

/**
 * Pure builder: filters [records] to those whose `fecha_hora_inicio` falls
 * within `[fromMillis, toMillis]` (inclusive bounds), sorts ascending by
 * `fecha_hora_inicio`, and maps each record to a [PdfReportRow] using the
 * shared formatters (INV-004 Table Content Format / Date-Range Filtering /
 * Chronological Ordering).
 *
 * No coroutines, no platform APIs — safe to run and assert in commonTest.
 */
fun buildReportData(
    records: List<RegistroComida>,
    fromMillis: Long,
    toMillis: Long,
): PdfReportData {
    val rows = records
        .filter { it.fecha_hora_inicio in fromMillis..toMillis }
        .sortedBy { it.fecha_hora_inicio }
        .map { reg ->
            PdfReportRow(
                dateText = formatEpochMillis(reg.fecha_hora_inicio),
                preMealBgText = formatGrams(reg.glicemia_inicial),
                twoHourBgText = reg.glicemia_postprandial_2h?.let(::formatGrams) ?: "—",
                realCarbsText = reg.carbohidratos_reales?.let(::formatGrams) ?: "—",
            )
        }
    return PdfReportData(
        title = "Reporte de Registro de Comidas",
        rangeLabel = "${formatEpochMillis(fromMillis)} — ${formatEpochMillis(toMillis)}",
        rows = rows,
    )
}

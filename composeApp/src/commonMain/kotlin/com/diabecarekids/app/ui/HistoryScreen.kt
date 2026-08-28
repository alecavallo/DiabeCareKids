@file:OptIn(ExperimentalMaterial3Api::class)

package com.diabecarekids.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.format.formatEpochMillis
import com.diabecarekids.app.format.formatGrams
import com.diabecarekids.app.platform.epochMillisNow
import com.diabecarekids.app.viewmodel.HistoryViewModel

/** Length of one day in milliseconds. */
private const val DAY_MILLIS = 86_400_000L

/**
 * Advanced View history timeline (CAP-005, R2). Displays all records newest-first
 * (already sorted descending by [HistoryViewModel]), each row showing the meal's
 * date/time, type, initial BG, estimated carbs and real carbs. Tapping a record
 * opens [EditRecordScreen]; the FAB opens [AddPastRecordScreen]. Back returns to T0.
 *
 * A top-bar "Exportar" action opens a Material3 [DateRangePicker] (default last
 * 7 days) that drives [HistoryViewModel.exportReport] (CAP-004), with exporting
 * and failure feedback.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onRecordClick: (RegistroComida) -> Unit,
    onAddRecord: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    // Default range = last 7 days (inclusive): from = now - 6d, to = now.
    val defaultRange = remember {
        val now = epochMillisNow()
        (now - 6 * DAY_MILLIS) to now
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = { showPicker = true }, enabled = !state.isExporting) {
                        Text(if (state.isExporting) "Exportando…" else "Exportar")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecord) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar registro")
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Text("Cargando...", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.records.isEmpty()) {
                        item { Text("Sin registros aún", style = MaterialTheme.typography.bodyMedium) }
                    }
                    items(state.records, key = { it.id }) { registro ->
                        HistoryRow(registro = registro, onClick = { onRecordClick(registro) })
                    }
                    state.error?.let { err ->
                        item { Text(err, color = MaterialTheme.colorScheme.error) }
                    }
                }
                state.exportError?.let { err ->
                    Text(
                        "No se pudo exportar el informe: $err",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (showPicker) {
        ExportRangeDialog(
            defaultFrom = defaultRange.first,
            defaultTo = defaultRange.second,
            onConfirm = { from, to ->
                viewModel.exportReport(from, to)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun ExportRangeDialog(
    defaultFrom: Long,
    defaultTo: Long,
    onConfirm: (from: Long, to: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = defaultFrom,
        initialSelectedEndDateMillis = defaultTo,
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Exportar informe médico", style = MaterialTheme.typography.titleMedium)
            DateRangePicker(
                state = pickerState,
                title = {
                    Text(
                        "Rango de fechas (últimos 7 días por defecto)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            // Include the full selected end day (end-of-day inclusive, design).
                            onConfirm(start, end + DAY_MILLIS - 1)
                        }
                    },
                ) { Text("Exportar") }
            }
        }
    }
}

@Composable
private fun HistoryRow(registro: RegistroComida, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatEpochMillis(registro.fecha_hora_inicio), style = MaterialTheme.typography.labelMedium)
                Text(registro.tipo_comida.name, style = MaterialTheme.typography.labelMedium)
            }
            Text(registro.nombre_alimento, style = MaterialTheme.typography.titleSmall)
            Text(
                "BG inicial ${formatGrams(registro.glicemia_inicial)} · " +
                    "Carbos ${formatGrams(registro.carbohidratos_estimados)} g · " +
                    "Reales ${registro.carbohidratos_reales?.let { formatGrams(it) } ?: "—"} g",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.viewmodel.HistoryViewModel

/**
 * Advanced View history timeline (CAP-005, R2). Displays all records newest-first
 * (already sorted descending by [HistoryViewModel]), each row showing the meal's
 * date/time, type, initial BG, estimated carbs and real carbs. Tapping a record
 * opens [EditRecordScreen]; the FAB opens [AddPastRecordScreen]. Back returns to T0.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onRecordClick: (RegistroComida) -> Unit,
    onAddRecord: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
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

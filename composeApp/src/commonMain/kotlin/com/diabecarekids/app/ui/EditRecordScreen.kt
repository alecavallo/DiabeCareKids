@file:OptIn(ExperimentalMaterial3Api::class)

package com.diabecarekids.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diabecarekids.app.format.formatEpochMillis
import com.diabecarekids.app.format.formatGrams
import com.diabecarekids.app.viewmodel.EditRecordViewModel

/**
 * Edit existing historical record (CAP-005, R3): carb estimate, consumed % and
 * 2h postprandial BG, with a live recalculated real-carbs preview (FollowUpScreen
 * pattern). Saving persists via [EditRecordViewModel.update] then signals
 * [EditRecordViewModel.updated] and the screen returns to History via [onSaved].
 */
@Composable
fun EditRecordScreen(
    viewModel: EditRecordViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val updated by viewModel.updated.collectAsState()

    LaunchedEffect(updated) {
        updated?.let {
            viewModel.onUpdatedConsumed()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar registro") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(state.registro.nombre_alimento, style = MaterialTheme.typography.titleMedium)
            Text(
                "Tipo: ${state.registro.tipo_comida.name} · ${formatEpochMillis(state.registro.fecha_hora_inicio)}",
                style = MaterialTheme.typography.labelMedium,
            )

            OutlinedTextField(
                value = state.carbInput,
                onValueChange = viewModel::onCarbInputChange,
                label = { Text("Carbohidratos (g)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Porcentaje consumido: ${state.consumedPercent}%")
            Slider(
                value = state.consumedPercent.toFloat(),
                onValueChange = { viewModel.onConsumedPercentChange(it.toInt()) },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.bgPost2h,
                onValueChange = viewModel::onBgPost2hChange,
                label = { Text("Glicemia a las 2h (mg/dL)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Carbohidratos reales: ${formatGrams(state.realCarbsPreview)} g",
                style = MaterialTheme.typography.titleMedium,
            )

            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "Guardando..." else "Guardar")
            }
        }
    }
}

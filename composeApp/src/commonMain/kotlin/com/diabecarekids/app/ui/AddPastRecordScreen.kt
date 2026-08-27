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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.viewmodel.AddPastRecordViewModel

/**
 * Historical past-record form (CAP-005, R1/R4). Date/time are chosen with the
 * Material3 pickers and combined into an epoch-millis [Long] — the ViewModel
 * only ever sees the resulting value (wall-clock-as-UTC, no kotlinx-datetime).
 * The carb field is always editable; lookup ([AddPastRecordViewModel.resolve])
 * is optional. On save the VM signals [AddPastRecordViewModel.saved] and the
 * screen returns to History via [onSaved].
 */
@Composable
fun AddPastRecordScreen(
    viewModel: AddPastRecordViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val saved by viewModel.saved.collectAsState()

    // Pickers are the source of truth for date/time; results are pushed up as
    // epoch millis. The state factories remember their initial value, so VM state
    // changes do not re-seed the pickers and truncating seconds on first
    // composition is fine.
    val dateState = rememberDatePickerState(initialSelectedDateMillis = state.dateTimeEpochMillis)
    val timeState = rememberTimePickerState(
        initialHour = hourOfDay(state.dateTimeEpochMillis),
        initialMinute = minuteOfHour(state.dateTimeEpochMillis),
    )

    LaunchedEffect(dateState.selectedDateMillis) {
        dateState.selectedDateMillis?.let { dateMillis ->
            viewModel.onDateTimeChange(combineDateTimeMillis(dateMillis, timeState.hour, timeState.minute))
        }
    }
    LaunchedEffect(timeState.hour, timeState.minute) {
        dateState.selectedDateMillis?.let { dateMillis ->
            viewModel.onDateTimeChange(combineDateTimeMillis(dateMillis, timeState.hour, timeState.minute))
        }
    }

    LaunchedEffect(saved) {
        saved?.let {
            viewModel.onSavedConsumed()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registrar comida pasada") },
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
            Text("Fecha y hora", style = MaterialTheme.typography.titleSmall)
            DatePicker(state = dateState)
            TimePicker(state = timeState)

            Text("Tipo de comida", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoComida.entries.forEach { type ->
                    FilterChip(
                        selected = type == state.mealType,
                        onClick = { viewModel.onMealTypeChange(type) },
                        label = { Text(type.name) },
                    )
                }
            }

            OutlinedTextField(
                value = state.foodQuery,
                onValueChange = viewModel::onFoodQueryChange,
                label = { Text("Nombre del alimento") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isResolving) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Buscando...")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::resolve) { Text("Resolver carbos") }
                if (state.sourceLabel != null) {
                    Text(state.sourceLabel!!, color = MaterialTheme.colorScheme.primary)
                }
            }

            OutlinedTextField(
                value = state.carbInput,
                onValueChange = viewModel::onCarbInputChange,
                label = { Text("Carbohidratos (g)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.bgInitial,
                onValueChange = viewModel::onBgInitialChange,
                label = { Text("Glicemia inicial (mg/dL)") },
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

/** Hour (0-23, UTC) of an epoch-millis value — matches wall-clock-as-UTC convention. */
private fun hourOfDay(millis: Long): Int = Math.floorMod(millis / 3_600_000L, 24L).toInt()

/** Minute (0-59, UTC) of an epoch-millis value. */
private fun minuteOfHour(millis: Long): Int = Math.floorMod(millis / 60_000L, 60L).toInt()

/** Combines a midnight-epoch date millis (from DatePicker) with an hour/minute. */
private fun combineDateTimeMillis(dateMillis: Long, hour: Int, minute: Int): Long =
    dateMillis + hour * 3_600_000L + minute * 60_000L

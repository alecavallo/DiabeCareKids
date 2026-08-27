package com.diabecarekids.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.diabecarekids.app.format.formatGrams
import com.diabecarekids.app.viewmodel.FollowUpViewModel

/**
 * T2 postprandial follow-up: intake % slider, 2h blood glucose, a live real-carbs
 * preview (REQ-MEAL-003), and an optional after photo (INV-005). On save the
 * existing meal record is updated; [onDone] returns to the T0 form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpScreen(
    viewModel: FollowUpViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val completed by viewModel.completed.collectAsState()

    LaunchedEffect(completed) {
        completed?.let {
            viewModel.onCompletedConsumed()
            onDone()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Seguimiento postprandial (T2)") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(state.registro.nombre_alimento, style = MaterialTheme.typography.titleMedium)

            Text("Porcentaje consumido: ${state.intakePercent}%")
            Slider(
                value = state.intakePercent.toFloat(),
                onValueChange = { viewModel.onIntakePercentChange(it.toInt()) },
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::takePhoto) {
                    Text(if (state.photoUri != null) "Cambiar foto" else "Agregar foto")
                }
                if (state.photoUri != null) Text("Foto capturada")
            }

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

package com.diabecarekids.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.diabecarekids.app.sos.LocationPermissionRequester
import com.diabecarekids.app.sos.SosState
import com.diabecarekids.app.viewmodel.SosViewModel

/**
 * Dedicated SOS screen (design decision #5: a non-scrolling, gesture-stable
 * surface hosting [EmergencySOSButton]). REQ-SOS-001 hold-to-activate; on a
 * successful trigger the machine reaches [SosState.Triggered] and the screen
 * swaps the ring for the "Alerta enviada" confirmation (REQ-SOS-003).
 *
 * Runtime location permission is requested on ENTRY via a [LaunchedEffect]
 * (design decision #6, [LocationPermissionRequester]), never at trigger — so a
 * denial at trigger time yields null coordinates but the alert still fires.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(
    viewModel: SosViewModel,
    permissionRequester: LocationPermissionRequester,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val triggered = state is SosState.Triggered

    // Request location on screen entry (design decision #6), not at trigger.
    LaunchedEffect(Unit) {
        permissionRequester.requestOnEntry()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("SOS de emergencia") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (triggered) {
                Text(
                    text = "Alerta enviada",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Tus tutores han sido notificados. Mantente en un lugar seguro.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Text("Volver")
                }
            } else {
                EmergencySOSButton(
                    progress = state.progress,
                    onHoldStart = viewModel::onHoldStart,
                    onTick = viewModel::onTick,
                    onHoldEnd = viewModel::onHoldEnd,
                )
                Text(
                    text = "Mantén presionado durante 3 segundos para enviar la alerta de emergencia.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

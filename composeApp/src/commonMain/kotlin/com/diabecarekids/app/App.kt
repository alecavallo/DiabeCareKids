package com.diabecarekids.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.diabecarekids.app.navigation.AppGraph
import com.diabecarekids.app.navigation.Route
import com.diabecarekids.app.ui.AddPastRecordScreen
import com.diabecarekids.app.ui.EditRecordScreen
import com.diabecarekids.app.ui.FollowUpScreen
import com.diabecarekids.app.ui.HistoryScreen
import com.diabecarekids.app.ui.MealFormScreen
import com.diabecarekids.app.ui.SosScreen

/**
 * Root composable. Sealed-route state drives navigation between the T0 meal form,
 * the T2 postprandial follow-up, and the Advanced View screens (History /
 * AddPastRecord / EditRecord) — design DECISION: no nav-compose dependency.
 * The [graph] composition root supplies the ViewModels. Advanced View ViewModels
 * are created per navigation so entering a branch always reloads fresh state.
 */
@Composable
fun App(graph: AppGraph) {
    MaterialTheme {
        var route by remember { mutableStateOf<Route>(Route.T0) }
        when (val current = route) {
            is Route.T0 -> MealFormScreen(
                viewModel = graph.mealFormViewModel,
                onMealSaved = { registro -> route = Route.T2(registro) },
                onOpenSos = { route = Route.Sos },
                onOpenHistory = { route = Route.History },
            )
            is Route.T2 -> {
                // Key on the meal id so state survives recomposition but resets on navigation.
                val followUpViewModel = remember(current.registro.id) {
                    graph.followUpViewModel(current.registro)
                }
                FollowUpScreen(
                    viewModel = followUpViewModel,
                    onDone = {
                        // Returning T2 -> T0 starts a fresh meal: reset the graph singleton form (ID-LEAK).
                        graph.mealFormViewModel.reset()
                        route = Route.T0
                    },
                )
            }
            is Route.Sos -> {
                val sosViewModel = graph.sosViewModel
                SosScreen(
                    viewModel = sosViewModel,
                    permissionRequester = graph.locationPermission,
                    onBack = {
                        // Reset the singleton machine on exit so re-entry starts clean (INV-003).
                        sosViewModel.reset()
                        route = Route.T0
                    },
                )
            }
            is Route.History -> {
                // Per-navigation creation: leaving reloads the store on the next entry (refresh).
                val historyViewModel = remember { graph.historyViewModel() }
                HistoryScreen(
                    viewModel = historyViewModel,
                    onBack = { route = Route.T0 },
                    onRecordClick = { registro -> route = Route.EditRecord(registro) },
                    onAddRecord = { route = Route.AddPastRecord },
                )
            }
            is Route.AddPastRecord -> {
                val addPastViewModel = remember { graph.addPastRecordViewModel() }
                AddPastRecordScreen(
                    viewModel = addPastViewModel,
                    onBack = { route = Route.History },
                    onSaved = { route = Route.History },
                )
            }
            is Route.EditRecord -> {
                val editViewModel = remember(current.registro.id) {
                    graph.editRecordViewModel(current.registro)
                }
                EditRecordScreen(
                    viewModel = editViewModel,
                    onBack = { route = Route.History },
                    onSaved = { route = Route.History },
                )
            }
        }
    }
}

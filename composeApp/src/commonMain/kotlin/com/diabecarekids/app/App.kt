package com.diabecarekids.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.diabecarekids.app.navigation.AppGraph
import com.diabecarekids.app.navigation.Route
import com.diabecarekids.app.ui.FollowUpScreen
import com.diabecarekids.app.ui.MealFormScreen

/**
 * Root composable. Sealed-route state drives navigation between the T0 meal form
 * and the T2 postprandial follow-up (design DECISION: no nav-compose dependency).
 * The [graph] composition root supplies the ViewModels.
 */
@Composable
fun App(graph: AppGraph) {
    MaterialTheme {
        var route by remember { mutableStateOf<Route>(Route.T0) }
        when (val current = route) {
            is Route.T0 -> MealFormScreen(
                viewModel = graph.mealFormViewModel,
                onMealSaved = { registro -> route = Route.T2(registro) },
            )
            is Route.T2 -> {
                // Key on the meal id so state survives recomposition but resets on navigation.
                val followUpViewModel = remember(current.registro.id) {
                    graph.followUpViewModel(current.registro)
                }
                FollowUpScreen(
                    viewModel = followUpViewModel,
                    onDone = { route = Route.T0 },
                )
            }
        }
    }
}

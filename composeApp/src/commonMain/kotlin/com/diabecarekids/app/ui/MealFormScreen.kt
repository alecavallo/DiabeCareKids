@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.diabecarekids.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diabecarekids.app.domain.FoodItem
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.viewmodel.BG_QUICK_CHIPS
import com.diabecarekids.app.viewmodel.MealFormState
import com.diabecarekids.app.viewmodel.MealFormViewModel

/**
 * T0 meal-entry form. Food lookup with USDA/Gemini suggestion cards, quick-select
 * BG chips, an always-editable carb field (INV-002), and an optional before photo
 * (INV-005). Navigating to T2 is delegated via [onMealSaved].
 */
@Composable
fun MealFormScreen(
    viewModel: MealFormViewModel,
    onMealSaved: (com.diabecarekids.app.domain.RegistroComida) -> Unit,
    onOpenSos: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val savedMeal by viewModel.savedMeal.collectAsState()

    LaunchedEffect(savedMeal) {
        savedMeal?.let {
            viewModel.onSavedMealConsumed()
            onMealSaved(it)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Nueva comida (T0)") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MealTypeSelector(state.mealType, viewModel::onMealTypeChange) }

            item {
                OutlinedTextField(
                    value = state.foodQuery,
                    onValueChange = viewModel::onFoodQueryChange,
                    label = { Text("Nombre del alimento") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.isResolving) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("Buscando...")
                    }
                }
            }

            items(state.suggestions) { food ->
                SuggestionCard(food = food, onClick = { viewModel.selectSuggestion(food) })
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::resolve) { Text("Resolver carbos") }
                    if (state.sourceLabel != null) {
                        Text(state.sourceLabel!!, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.carbInput,
                    onValueChange = viewModel::onCarbInputChange,
                    label = { Text("Carbohidratos (g)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { QuickBgSelector(state.bgInitial, viewModel::onBgInitialChange) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::takePhoto) { Text(if (state.photoUri != null) "Cambiar foto" else "Agregar foto") }
                    if (state.photoUri != null) Text("Foto capturada")
                }
            }

            state.error?.let { err ->
                item { Text(err, color = MaterialTheme.colorScheme.error) }
            }

            item {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isSaving) "Guardando..." else "Guardar (seguir a T2)")
                }
            }

            // Emergency SOS entry (CAP-001). Reachability from the meal flow;
            // design decision #5 hosts the 3s hold on the dedicated SosScreen.
            item {
                OutlinedButton(
                    onClick = onOpenSos,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("SOS de emergencia")
                }
            }
        }
    }
}

@Composable
private fun MealTypeSelector(selected: TipoComida, onSelect: (TipoComida) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TipoComida.entries.forEach { type ->
            FilterChip(selected = type == selected, onClick = { onSelect(type) }, label = { Text(type.name) })
        }
    }
}

@Composable
private fun SuggestionCard(food: FoodItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(food.name, style = MaterialTheme.typography.titleSmall)
            Text("${formatGrams(food.carbsGrams)} g · ${food.source}")
        }
    }
}

@Composable
private fun QuickBgSelector(current: String, onChange: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BG_QUICK_CHIPS.forEach { value ->
            FilterChip(
                selected = current == value.toString(),
                onClick = { onChange(value.toString()) },
                label = { Text("$value") },
            )
        }
    }
}

/** Formats a gram value for display, rounding to 1 decimal to avoid float
 *  artifacts like 19.413999999999998 rendering as-is (ID-ROUND). */
internal fun formatGrams(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

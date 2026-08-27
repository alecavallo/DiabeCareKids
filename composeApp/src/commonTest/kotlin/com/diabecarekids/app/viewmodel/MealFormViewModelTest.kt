@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.nutrition.CarbResolution
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealFormViewModelTest {

    private fun baseRegistro(
        id: String = "meal-1",
        estimados: Double = 50.0,
    ) = RegistroComida(
        id = id,
        fecha_hora_inicio = 1L,
        tipo_comida = TipoComida.ALMUERZO,
        glicemia_inicial = 120.0,
        nombre_alimento = "test food",
        carbohidratos_estimados = estimados,
        fuente_carbohidratos = CarbSource.MANUAL,
        foto_antes_url = null,
        foto_despues_url = null,
        porcentaje_consumido = 100,
        carbohidratos_reales = null,
        glicemia_postprandial_2h = null,
        es_registro_historico = false,
        creado_por_usuario_id = "local",
        ultima_modificacion = 1L,
    )

    @Test
    fun editableCarbOverridesResolvedValueBeforePersistence() = runTest {
        val repo = FakeNutritionRepository(
            resolution = CarbResolution.Resolved(50.0, CarbSource.USDA),
        )
        val store = FakePersistenceStore()
        val vm = MealFormViewModel(repo, store, FakePhotoCapture(), FakeAlarmScheduler(), this)

        vm.onFoodQueryChange("apple")
        vm.resolve()
        advanceUntilIdle()
        assertEquals("50", vm.state.value.carbInput, "resolved value should populate the field")

        // User edits the resolved value (INV-002): persisted value must honor the edit.
        vm.onCarbInputChange("35")
        vm.onBgInitialChange("110")
        vm.save()
        advanceUntilIdle()

        val saved = store.saved.single()
        assertEquals(35.0, saved.carbohidratos_estimados)
        assertEquals(CarbSource.USDA, saved.fuente_carbohidratos)
        assertEquals("apple", saved.nombre_alimento)
    }

    @Test
    fun saveWithoutPhotoPersistsNullFotoAntes() = runTest {
        val repo = FakeNutritionRepository(resolution = CarbResolution.ManualRequired)
        val store = FakePersistenceStore()
        val vm = MealFormViewModel(repo, store, FakePhotoCapture(uri = null), FakeAlarmScheduler(), this)

        vm.onFoodQueryChange("pizza")
        vm.onCarbInputChange("60")
        vm.onBgInitialChange("130")
        vm.save()
        advanceUntilIdle()

        val saved = store.saved.single()
        assertNull(saved.foto_antes_url, "no photo taken -> foto_antes_url must be null (INV-005)")
        assertEquals(CarbSource.MANUAL, saved.fuente_carbohidratos)
    }

    @Test
    fun saveSchedulesTwoHourPostprandialAlarm() = runTest {
        val repo = FakeNutritionRepository(resolution = CarbResolution.ManualRequired)
        val store = FakePersistenceStore()
        val alarm = FakeAlarmScheduler()
        val vm = MealFormViewModel(repo, store, FakePhotoCapture(), alarm, this)

        vm.onFoodQueryChange("sandwich")
        vm.onCarbInputChange("45")
        vm.onBgInitialChange("90")
        vm.save()
        advanceUntilIdle()

        val saved = store.saved.single()
        assertEquals(listOf(saved.id), alarm.scheduled, "T0 save must schedule the 2h follow-up")
    }

    @Test
    fun saveWithInvalidCarbShowsErrorAndPersistsNothing() = runTest {
        val repo = FakeNutritionRepository()
        val store = FakePersistenceStore()
        val vm = MealFormViewModel(repo, store, FakePhotoCapture(), FakeAlarmScheduler(), this)

        vm.onFoodQueryChange("bread")
        vm.onCarbInputChange("not-a-number")
        vm.onBgInitialChange("100")
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.error != null, "invalid carb should surface an error")
        assertTrue(store.saved.isEmpty(), "nothing should persist with an invalid carb value")
    }

    @Test
    fun suggestionSelectFillsEditableCarb() = runTest {
        val food = com.diabecarekids.app.domain.FoodItem("Oatmeal", 27.0, CarbSource.USDA)
        val repo = FakeNutritionRepository(suggestions = listOf(food))
        val store = FakePersistenceStore()
        val vm = MealFormViewModel(repo, store, FakePhotoCapture(), FakeAlarmScheduler(), this)

        vm.onFoodQueryChange("oat")
        advanceUntilIdle()
        assertEquals(listOf(food), vm.state.value.suggestions)
        vm.selectSuggestion(food)

        assertEquals("Oatmeal", vm.state.value.foodQuery)
        assertEquals("27", vm.state.value.carbInput)
    }
}

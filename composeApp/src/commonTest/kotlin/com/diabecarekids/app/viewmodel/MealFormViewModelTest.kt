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
    fun cancelledSecondCaptureKeepsExistingPhoto() = runTest {
        val repo = FakeNutritionRepository()
        val store = FakePersistenceStore()
        val photo = FakePhotoCapture()
        val vm = MealFormViewModel(repo, store, photo, FakeAlarmScheduler(), this)

        photo.results.addAll(listOf("file://first.jpg", null)) // capture then cancel
        vm.takePhoto()
        advanceUntilIdle()
        assertEquals("file://first.jpg", vm.state.value.photoUri, "first capture should set the photo")

        vm.takePhoto()
        advanceUntilIdle()
        assertEquals(
            "file://first.jpg",
            vm.state.value.photoUri,
            "a cancelled second capture must NOT erase the existing photo",
        )
    }

    @Test
    fun cameraDeniedSurfacesFriendlyErrorAndSetsNoPhoto() = runTest {
        val repo = FakeNutritionRepository()
        val store = FakePersistenceStore()
        val photo = FakePhotoCapture(uri = null)
        val vm = MealFormViewModel(repo, store, photo, FakeAlarmScheduler(), this)

        photo.results.addAll(listOf(null)) // denied -> takePhoto returns null
        photo.markCameraDenied()
        vm.takePhoto()
        advanceUntilIdle()

        assertNull(vm.state.value.photoUri, "a denied permission must not set a photo")
        assertEquals(
            true,
            vm.state.value.error?.contains("permission", ignoreCase = true),
            "a denied camera permission must surface a friendly error state",
        )
    }

    @Test
    fun resetClearsFormForNextMeal() = runTest {
        val repo = FakeNutritionRepository(resolution = CarbResolution.Resolved(50.0, CarbSource.USDA))
        val store = FakePersistenceStore()
        val photo = FakePhotoCapture(uri = "file://meal.jpg")
        val vm = MealFormViewModel(repo, store, photo, FakeAlarmScheduler(), this)

        vm.onFoodQueryChange("apple")
        vm.resolve()
        advanceUntilIdle()
        vm.onBgInitialChange("110")
        vm.takePhoto()
        advanceUntilIdle()
        assertTrue(vm.state.value.photoUri != null, "photo should be captured before save")

        // Saving persists the meal and resets the form so the next meal starts clean.
        vm.save()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("", s.foodQuery, "fresh meal must start with an empty food query")
        assertEquals("", s.carbInput, "fresh meal must start with an empty carb field")
        assertEquals("", s.bgInitial, "fresh meal must start with an empty BG field")
        assertNull(s.photoUri, "fresh meal must start with no photo (no leaked foto_antes_url)")
        assertTrue(s.suggestions.isEmpty(), "fresh meal must start with no stale suggestions")
        assertNull(s.selectedFoodName, "fresh meal must start with no selected food")
        assertEquals(false, s.isSaving, "fresh meal must not be stuck in a saving state")
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

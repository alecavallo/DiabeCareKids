@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.nutrition.CarbResolution
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddPastRecordViewModelTest {

    @Test
    fun saveBuildsHistoricalRecordWithoutPhotos() = runTest {
        val store = FakePersistenceStore()
        val vm = AddPastRecordViewModel(FakeNutritionRepository(), store, this)

        vm.onFoodQueryChange("Pasta")
        vm.onCarbInputChange("60")
        vm.onBgInitialChange("140")
        vm.onDateTimeChange(1_700_000_000_000L)
        vm.onConsumedPercentChange(50)
        vm.save()
        advanceUntilIdle()

        val saved = store.saved.single()
        assertTrue(saved.es_registro_historico, "historical records must be flagged es_registro_historico=true (R1)")
        assertNull(saved.foto_antes_url, "historical records must omit the before photo (R1)")
        assertNull(saved.foto_despues_url, "historical records must omit the after photo (R1)")
        assertEquals(1_700_000_000_000L, saved.fecha_hora_inicio, "save must use the selected historical time (R1)")
        assertEquals(60.0, saved.carbohidratos_estimados)
        assertEquals(30.0, saved.carbohidratos_reales, "50% of 60g must recalc to 30g via CarbMath")
        assertEquals("local", saved.creado_por_usuario_id)
        assertTrue(saved.id.isNotBlank())
        assertEquals(CarbSource.MANUAL, saved.fuente_carbohidratos, "default source must be MANUAL")
    }

    @Test
    fun manualOnlySaveAcceptsManualCarbs() = runTest {
        val store = FakePersistenceStore()
        val repo = FakeNutritionRepository(resolution = CarbResolution.ManualRequired)
        val vm = AddPastRecordViewModel(repo, store, this)

        // Lookup declines; the guardian bypasses it and enters carbs manually (R4).
        vm.onFoodQueryChange("Apple")
        vm.resolve()
        advanceUntilIdle()
        assertEquals(CarbSource.MANUAL, vm.state.value.source, "declined lookup must fall back to manual source")

        vm.onCarbInputChange("30")
        vm.onBgInitialChange("110")
        vm.save()
        advanceUntilIdle()

        val saved = store.saved.single()
        assertEquals(30.0, saved.carbohidratos_estimados, "manual carb value must be accepted (R4)")
        assertEquals(CarbSource.MANUAL, saved.fuente_carbohidratos)
    }

    @Test
    fun resolveLookupFillsCarbsWithSource() = runTest {
        val repo = FakeNutritionRepository(
            resolution = CarbResolution.Resolved(carbsGrams = 45.0, source = CarbSource.USDA),
        )
        val vm = AddPastRecordViewModel(repo, FakePersistenceStore(), this)

        vm.onFoodQueryChange("Rice")
        vm.resolve()
        advanceUntilIdle()

        assertEquals("45", vm.state.value.carbInput)
        assertEquals(CarbSource.USDA, vm.state.value.source)
    }

    @Test
    fun saveRejectsMissingOrInvalidFields() = runTest {
        val store = FakePersistenceStore()
        val vm = AddPastRecordViewModel(FakeNutritionRepository(), store, this)

        // Missing food name.
        vm.onCarbInputChange("30")
        vm.onBgInitialChange("110")
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.error != null, "missing food name must block save")
        assertEquals(0, store.saved.size)

        // Invalid (negative) carbs.
        vm.onFoodQueryChange("Pasta")
        vm.onCarbInputChange("-5")
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.error != null, "negative carbs must block save")
        assertEquals(0, store.saved.size)

        // Blank initial BG.
        vm.onCarbInputChange("30")
        vm.onBgInitialChange("")
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.error != null, "blank initial BG must block save")
        assertEquals(0, store.saved.size)
    }
}

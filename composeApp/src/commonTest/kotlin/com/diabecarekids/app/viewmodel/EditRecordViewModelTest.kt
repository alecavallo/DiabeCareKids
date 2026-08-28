@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.TipoComida
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditRecordViewModelTest {

    private fun base(
        estimados: Double = 50.0,
        fuente: CarbSource = CarbSource.USDA,
        bg2h: Double? = null,
    ) = RegistroComida(
        id = "hist-1",
        fecha_hora_inicio = 1000L,
        tipo_comida = TipoComida.ALMUERZO,
        glicemia_inicial = 120.0,
        nombre_alimento = "Pasta",
        carbohidratos_estimados = estimados,
        fuente_carbohidratos = fuente,
        porcentaje_consumido = 100,
        carbohidratos_reales = estimados,
        glicemia_postprandial_2h = bg2h,
        es_registro_historico = true,
        creado_por_usuario_id = "local",
        ultima_modificacion = 1000L,
    )

    @Test
    fun consumedPercentChangeRecalculatesReals() = runTest {
        val store = FakePersistenceStore()
        val vm = EditRecordViewModel(store, this, base()) // 50g @ 100% -> reals 50

        vm.onConsumedPercentChange(50)
        assertEquals(25.0, vm.state.value.realCarbsPreview, "50% of 50g must preview 25g (R3)")

        vm.onBgPost2hChange("160")
        vm.save()
        advanceUntilIdle()

        val updated = store.updated.single()
        assertEquals(50, updated.porcentaje_consumido)
        assertEquals(25.0, updated.carbohidratos_reales, "reals must recalc to 25g (R3)")
    }

    @Test
    fun carbEditFlipsSourceToManual() = runTest {
        val store = FakePersistenceStore()
        val vm = EditRecordViewModel(store, this, base(fuente = CarbSource.USDA))

        vm.onCarbInputChange("40")
        assertEquals(
            CarbSource.MANUAL,
            vm.state.value.registro.fuente_carbohidratos,
            "editing the carb estimate must flip provenance to MANUAL",
        )

        vm.onBgPost2hChange("160")
        vm.save()
        advanceUntilIdle()
        assertEquals(CarbSource.MANUAL, store.updated.single().fuente_carbohidratos)
    }

    @Test
    fun blankTwoHourBgLeavesStoredValueUnchanged() = runTest {
        val store = FakePersistenceStore()
        val vm = EditRecordViewModel(store, this, base(bg2h = 180.0))

        vm.onConsumedPercentChange(100)
        vm.save() // bgPost2h left blank
        advanceUntilIdle()

        val updated = store.updated.single()
        assertEquals(180.0, updated.glicemia_postprandial_2h, "blank 2h BG must keep the stored value (R3)")
    }

    @Test
    fun savePersistsUpdatedRecord() = runTest {
        val store = FakePersistenceStore()
        val vm = EditRecordViewModel(store, this, base())

        vm.onConsumedPercentChange(80)
        vm.onBgPost2hChange("150")
        vm.save()
        advanceUntilIdle()

        val updated = store.updated.single()
        assertEquals("hist-1", updated.id)
        assertEquals(80, updated.porcentaje_consumido)
        assertEquals(150.0, updated.glicemia_postprandial_2h)
        assertEquals(40.0, updated.carbohidratos_reales, "80% of 50g must recalc to 40g")
        assertTrue(updated.ultima_modificacion >= 1000L, "update must refresh ultima_modificacion")
    }
}

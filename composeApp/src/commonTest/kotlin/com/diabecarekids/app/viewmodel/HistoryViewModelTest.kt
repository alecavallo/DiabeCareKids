@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.TipoComida
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryViewModelTest {

    private fun reg(id: String, at: Long) = RegistroComida(
        id = id,
        fecha_hora_inicio = at,
        tipo_comida = TipoComida.ALMUERZO,
        glicemia_inicial = 120.0,
        nombre_alimento = "food $id",
        carbohidratos_estimados = 50.0,
        fuente_carbohidratos = CarbSource.MANUAL,
        porcentaje_consumido = 100,
        carbohidratos_reales = 50.0,
        es_registro_historico = true,
        creado_por_usuario_id = "local",
        ultima_modificacion = at,
    )

    @Test
    fun loadsRecordsNewestFirst() = runTest {
        val store = FakePersistenceStore()
        // Insert out of order to prove the ViewModel sorts, not the store (design decision).
        store.save(reg("old", at = 100L))
        store.save(reg("new", at = 300L))
        store.save(reg("mid", at = 200L))

        val vm = HistoryViewModel(store, this)
        advanceUntilIdle()

        assertEquals(
            listOf("new", "mid", "old"),
            vm.state.value.records.map { it.id },
            "timeline must be sorted by fecha_hora_inicio descending, newest first (R2)",
        )
    }

    @Test
    fun emptyStoreLoadsEmptyTimeline() = runTest {
        val vm = HistoryViewModel(FakePersistenceStore(), this)
        advanceUntilIdle()

        assertEquals(emptyList(), vm.state.value.records, "empty store must yield an empty timeline (R2)")
    }

    @Test
    fun reloadRefreshesAfterNewSave() = runTest {
        val store = FakePersistenceStore()
        val vm = HistoryViewModel(store, this)
        advanceUntilIdle()
        assertEquals(0, vm.state.value.records.size)

        // A record persisted after initial load must appear on explicit reload.
        store.save(reg("later", at = 999L))
        vm.reload()
        advanceUntilIdle()

        assertEquals(
            listOf("later"),
            vm.state.value.records.map { it.id },
            "reload must reflect records persisted after the initial load",
        )
    }
}

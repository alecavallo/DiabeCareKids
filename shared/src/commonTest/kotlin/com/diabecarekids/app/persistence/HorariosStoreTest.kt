package com.diabecarekids.app.persistence

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HorariosStoreTest {

    @Test
    fun defaultSeedIsLoaded() = runTest {
        val store = InMemoryHorariosStore()
        val config = store.load()

        assertEquals("08:00", config.horario_desayuno)
        assertEquals("12:30", config.horario_almuerzo)
        assertEquals("17:00", config.horario_merienda)
        assertEquals("21:00", config.horario_cena)
        assertEquals(15, config.ventana_anticipacion_minutos)
        assertEquals(true, config.recordatorios_activos)
    }

    @Test
    fun saveThenLoadReturnsSavedConfig() = runTest {
        val store = InMemoryHorariosStore()
        val changed = store.load().copy(horario_desayuno = "09:00", recordatorios_activos = false)

        store.save(changed)

        assertEquals(changed, store.load())
    }

    @Test
    fun customInitialIsRespected() = runTest {
        val store = InMemoryHorariosStore(initial = defaultConfiguracionHorarios.copy(horario_cena = "20:30"))
        assertEquals("20:30", store.load().horario_cena)
    }
}

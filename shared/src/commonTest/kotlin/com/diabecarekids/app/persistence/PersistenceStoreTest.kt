package com.diabecarekids.app.persistence

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.TipoComida
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PersistenceStoreTest {

    private fun registro(id: String) = RegistroComida(
        id = id,
        fecha_hora_inicio = 1000L,
        tipo_comida = TipoComida.DESAYUNO,
        glicemia_inicial = 110.0,
        nombre_alimento = "Avena",
        carbohidratos_estimados = 30.0,
        fuente_carbohidratos = CarbSource.USDA,
        creado_por_usuario_id = "user-1",
        ultima_modificacion = 2000L,
    )

    @Test
    fun saveThenGetReturnsExactRecord() = runTest {
        // REQ-MEAL-005 scenario: save → retrieve the exact same record by ID.
        val store = InMemoryPersistenceStore()
        val record = registro("meal-1")

        store.save(record)

        assertEquals(record, store.get("meal-1"))
    }

    @Test
    fun updateReplacesExistingRecord() = runTest {
        val store = InMemoryPersistenceStore()
        store.save(registro("meal-1"))

        val updated = registro("meal-1").copy(
            carbohidratos_reales = 24.0,
            glicemia_postprandial_2h = 130.0,
            foto_despues_url = "content://photos/despues.jpg",
        )
        store.update(updated)

        assertEquals(updated, store.get("meal-1"))
    }

    @Test
    fun updateMissingRecordThrows() = runTest {
        val store = InMemoryPersistenceStore()

        assertFailsWith<IllegalStateException> { store.update(registro("nope")) }
    }

    @Test
    fun deleteRemovesRecord() = runTest {
        val store = InMemoryPersistenceStore()
        store.save(registro("meal-1"))

        store.delete("meal-1")

        assertNull(store.get("meal-1"))
    }

    @Test
    fun getMissingReturnsNull() = runTest {
        val store = InMemoryPersistenceStore()

        assertNull(store.get("missing"))
    }

    @Test
    fun recordWithoutPhotoRoundTripsThroughStore() = runTest {
        // REQ-MEAL-004 scenario: T0 saved with foto_antes_url null.
        val store = InMemoryPersistenceStore()
        val withoutPhoto = registro("meal-9").copy(foto_antes_url = null)

        store.save(withoutPhoto)

        assertEquals(null, store.get("meal-9")?.foto_antes_url)
    }
}

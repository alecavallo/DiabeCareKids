package com.diabecarekids.app.persistence

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.TipoComida
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecentRecordCheckerTest {

    private fun registro(tipo: TipoComida, epochMillis: Long) = RegistroComida(
        id = "m-$tipo-$epochMillis",
        fecha_hora_inicio = epochMillis,
        tipo_comida = tipo,
        glicemia_inicial = 110.0,
        nombre_alimento = "Avena",
        carbohidratos_estimados = 30.0,
        fuente_carbohidratos = CarbSource.USDA,
        creado_por_usuario_id = "user-1",
        ultima_modificacion = epochMillis,
    )

    @Test
    fun recordInsideInclusiveWindowIsDetected() = runTest {
        val store = InMemoryPersistenceStore()
        val recordAt = 12_00_000L
        store.save(registro(TipoComida.ALMUERZO, recordAt))

        val checker = PersistenceRecentRecordWindowCheck(store)

        assertTrue(checker.hasRecent(TipoComida.ALMUERZO, from = recordAt - 1000, until = recordAt + 1000))
    }

    @Test
    fun recordOutsideWindowIsNotDetected() = runTest {
        val store = InMemoryPersistenceStore()
        val recordAt = 12_00_000L
        store.save(registro(TipoComida.ALMUERZO, recordAt))

        val checker = PersistenceRecentRecordWindowCheck(store)

        assertFalse(checker.hasRecent(TipoComida.ALMUERZO, from = recordAt + 5000, until = recordAt + 6000))
    }

    @Test
    fun recordOfDifferentMealTypeIsNotDetected() = runTest {
        val store = InMemoryPersistenceStore()
        store.save(registro(TipoComida.CENA, 12_00_000L))

        val checker = PersistenceRecentRecordWindowCheck(store)

        assertFalse(checker.hasRecent(TipoComida.ALMUERZO, from = 0, until = 99_999_999_999L))
    }

    @Test
    fun emptyStoreHasNoRecent() = runTest {
        val checker = PersistenceRecentRecordWindowCheck(InMemoryPersistenceStore())
        assertFalse(checker.hasRecent(TipoComida.DESAYUNO, from = 0, until = 99_999_999_999L))
    }
}

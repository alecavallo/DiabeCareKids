package com.diabecarekids.app.persistence

import com.diabecarekids.app.domain.Emergencia
import com.diabecarekids.app.domain.EmergenciaEstado
import com.diabecarekids.app.domain.UbicacionGps
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class EmergenciaStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun emergencia(id: String, ubicacion: UbicacionGps? = UbicacionGps(-34.6037, -58.3816, 10.0)) =
        Emergencia(
            id = id,
            id_paciente = "paciente-1",
            nombre_paciente = "Ana",
            fecha_hora = 1_728_000_000_000L,
            ubicacion = ubicacion,
            estado = EmergenciaEstado.ACTIVA,
        )

    @Test
    fun saveThenGetReturnsExactRecord() = runTest {
        // REQ-SOS-002 scenario: save → retrieve the exact record by id.
        val store = InMemoryEmergenciaStore()
        val record = emergencia("sos-1")

        store.save(record)

        assertEquals(record, store.get("sos-1"))
    }

    @Test
    fun updateReplacesExistingRecord() = runTest {
        val store = InMemoryEmergenciaStore()
        store.save(emergencia("sos-1"))

        val updated = emergencia("sos-1").copy(
            ubicacion = UbicacionGps(-34.6000, -58.3800, 25.0),
        )
        store.update(updated)

        assertEquals(updated, store.get("sos-1"))
    }

    @Test
    fun updateMissingRecordThrows() = runTest {
        val store = InMemoryEmergenciaStore()

        assertFailsWith<IllegalStateException> { store.update(emergencia("nope")) }
    }

    @Test
    fun getMissingReturnsNull() = runTest {
        val store = InMemoryEmergenciaStore()

        assertNull(store.get("missing"))
    }

    @Test
    fun serializationRoundTripPreservesAllFields() {
        val original = emergencia("sos-1")

        val roundTripped = json.decodeFromString<Emergencia>(json.encodeToString(original))

        assertEquals(original, roundTripped)
        assertIs<UbicacionGps>(roundTripped.ubicacion)
    }

    @Test
    fun nullLocationSerializesAsNull() {
        val original = emergencia("sos-2", ubicacion = null)

        val decoded = json.decodeFromString<Emergencia>(json.encodeToString(original))

        assertEquals(original, decoded)
        assertNull(decoded.ubicacion)
    }

    @Test
    fun estadoDefaultsToActivaOnDecode() {
        val jsonText =
            """{"id":"sos-3","id_paciente":"p","nombre_paciente":"Ana","fecha_hora":1}"""

        val decoded = json.decodeFromString<Emergencia>(jsonText)

        assertEquals(EmergenciaEstado.ACTIVA, decoded.estado)
        assertNull(decoded.ubicacion)
    }
}

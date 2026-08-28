package com.diabecarekids.app.sos

import com.diabecarekids.app.domain.EmergenciaEstado
import com.diabecarekids.app.domain.UbicacionGps
import com.diabecarekids.app.persistence.EmergenciaStore
import com.diabecarekids.app.persistence.InMemoryEmergenciaStore
import com.diabecarekids.app.platform.Haptics
import com.diabecarekids.app.platform.LocationProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SosControllerTest {

    private class FakeLocationProvider(var result: UbicacionGps? = null) : LocationProvider {
        var calls = 0
        override suspend fun currentLocation(): UbicacionGps? {
            calls++
            return result
        }
    }

    private class FakeHaptics : Haptics {
        var vibrated = false
        override fun vibrateSosTriggered() {
            vibrated = true
        }
    }

    private class FakeClock(var now: Long = 0L) {
        fun tick() = { now }
    }

    private fun sosController(
        clock: FakeClock,
        location: FakeLocationProvider,
        store: EmergenciaStore,
        notifier: InMemoryGuardianNotifier,
        haptics: FakeHaptics,
        scope: kotlinx.coroutines.CoroutineScope,
    ): SosController {
        val machine = SosHoldStateMachine(clock = clock.tick())
        return SosController(
            machine = machine,
            store = store,
            locationProvider = location,
            notifier = notifier,
            haptics = haptics,
            scope = scope,
            patientId = "paciente-1",
            patientName = "Ana",
            idGenerator = { "sos-1" },
        )
    }

    @Test
    fun triggerRunsFullPipelineAndStoresActiveRecord() = runTest {
        // REQ-SOS-002/003/004 scenario: hold to 3.0s → save → notify → haptics.
        val clock = FakeClock(0L)
        val location = FakeLocationProvider(UbicacionGps(latitud = -34.6037, longitud = -58.3816, precision_metros = 12.0))
        val store = InMemoryEmergenciaStore()
        val notifier = InMemoryGuardianNotifier()
        val haptics = FakeHaptics()
        val controller = sosController(clock, location, store, notifier, haptics, this)

        controller.onHoldStart()
        clock.now = 3000L
        controller.onTick()
        testScheduler.advanceUntilIdle() // run the async fire() pipeline

        val saved = store.get("sos-1")
        assertIs<com.diabecarekids.app.domain.Emergencia>(saved)
        assertEquals("sos-1", saved.id)
        assertEquals("paciente-1", saved.id_paciente)
        assertEquals("Ana", saved.nombre_paciente)
        assertEquals(EmergenciaEstado.ACTIVA, saved.estado)
        assertEquals(UbicacionGps(-34.6037, -58.3816, 12.0), saved.ubicacion)
        assertEquals(1, location.calls)

        val dispatched = notifier.dispatched()
        assertEquals(1, dispatched.size)
        assertEquals("sos-1", dispatched[0].id)
        assertTrue(haptics.vibrated)
        assertEquals(SosState.Triggered, controller.state.value)
    }

    @Test
    fun nullLocationStillSavesAndNotifiesWithNullCoordinates() = runTest {
        // REQ-SOS-003 scenario: location unavailable → record saved with null
        // coords, alert still fires (never blocks or crashes).
        val clock = FakeClock(0L)
        val location = FakeLocationProvider(null)
        val store = InMemoryEmergenciaStore()
        val notifier = InMemoryGuardianNotifier()
        val haptics = FakeHaptics()
        val controller = sosController(clock, location, store, notifier, haptics, this)

        controller.onHoldStart()
        clock.now = 3000L
        controller.onTick()
        testScheduler.advanceUntilIdle() // run the async fire() pipeline

        val saved = store.get("sos-1")
        assertIs<com.diabecarekids.app.domain.Emergencia>(saved)
        assertNull(saved.ubicacion)
        assertEquals(1, location.calls)
        assertEquals(1, notifier.dispatched().size)
        assertTrue(haptics.vibrated)
        assertEquals(SosState.Triggered, controller.state.value)
    }

    @Test
    fun earlyReleaseDoesNotTriggerPipeline() = runTest {
        // INV-003: releasing at 2.5s resets progress to 0%, no record, no notify.
        val clock = FakeClock(0L)
        val location = FakeLocationProvider(UbicacionGps(0.0, 0.0, 5.0))
        val store = InMemoryEmergenciaStore()
        val notifier = InMemoryGuardianNotifier()
        val haptics = FakeHaptics()
        val controller = sosController(clock, location, store, notifier, haptics, this)

        controller.onHoldStart()
        clock.now = 2500L
        controller.onTick()
        controller.onHoldEnd()

        assertNull(store.get("sos-1"))
        assertEquals(0, notifier.dispatched().size)
        assertTrue(!haptics.vibrated)
        assertEquals(SosState.Idle, controller.state.value)
    }

    @Test
    fun fireRunsExactlyOnceDespiteSubsequentTicks() = runTest {
        val clock = FakeClock(0L)
        val location = FakeLocationProvider(UbicacionGps(1.0, 2.0, null))
        val store = InMemoryEmergenciaStore()
        val notifier = InMemoryGuardianNotifier()
        val haptics = FakeHaptics()
        val controller = sosController(clock, location, store, notifier, haptics, this)

        controller.onHoldStart()
        clock.now = 3000L
        controller.onTick()
        clock.now = 3100L
        controller.onTick()
        testScheduler.advanceUntilIdle() // run the async fire() pipeline

        assertEquals(1, location.calls)
        assertEquals(1, notifier.dispatched().size)
        assertTrue(haptics.vibrated)
    }
}

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.UbicacionGps
import com.diabecarekids.app.persistence.InMemoryEmergenciaStore
import com.diabecarekids.app.sos.InMemoryGuardianNotifier
import com.diabecarekids.app.sos.SosController
import com.diabecarekids.app.sos.SosHoldStateMachine
import com.diabecarekids.app.sos.SosState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * SosViewModel is a thin binder (design decision #7): the machine (INV-003) and
 * the fire pipeline are owned by the shared [SosController]. These tests drive
 * the binder with a synthetic clock and fakes to prove start/tick/end/reset are
 * forwarded correctly, progress is exposed, and a full hold triggers the
 * pipeline (location consumed + haptics fired). No coroutine-timing coupling:
 * [onTick] is called explicitly with the synthetic clock (REQ-SOS-001).
 */
class SosViewModelTest {

    private class FakeClock(var now: Long = 0L) {
        fun read() = now
    }

    private fun sosViewModel(
        clock: FakeClock,
        location: FakeLocationProvider,
        haptics: FakeHaptics,
        scope: CoroutineScope,
    ): SosViewModel {
        val controller = SosController(
            machine = SosHoldStateMachine(clock = clock::read),
            store = InMemoryEmergenciaStore(),
            locationProvider = location,
            notifier = InMemoryGuardianNotifier(),
            haptics = haptics,
            scope = scope,
            patientId = "paciente-1",
            patientName = "Ana",
            idGenerator = { "sos-1" },
        )
        return SosViewModel(controller)
    }

    @Test
    fun holdStartForwardedToMachineAndProgressExposed() = runTest {
        val clock = FakeClock(0L)
        val vm = sosViewModel(clock, FakeLocationProvider(), FakeHaptics(), this)

        vm.onHoldStart()
        clock.now = 1500L
        vm.onTick()

        val state = vm.state.value
        assertIs<SosState.Pressing>(state)
        assertEquals(0.5f, state.progress, "at 1.5s of a 3.0s hold progress should be 0.5")
    }

    @Test
    fun earlyReleaseResetsToIdleWithoutTrigger() = runTest {
        // INV-003: releasing before 3.0s resets progress to 0% and never triggers.
        val clock = FakeClock(0L)
        val location = FakeLocationProvider(UbicacionGps(-34.6037, -58.3816, 12.0))
        val haptics = FakeHaptics()
        val vm = sosViewModel(clock, location, haptics, this)

        vm.onHoldStart()
        clock.now = 2500L
        vm.onTick()
        vm.onHoldEnd()

        assertEquals(SosState.Idle, vm.state.value)
        assertEquals(0f, vm.state.value.progress)
        assertEquals(0, location.calls, "no location read on early release")
        assertTrue(!haptics.vibrated, "no haptic on early release")
    }

    @Test
    fun fullThreeSecondHoldTriggersPipelineAndConfirmation() = runTest {
        // REQ-SOS-001/003/004: 3.0s hold → fire pipeline (location + haptics) → Triggered.
        val clock = FakeClock(0L)
        val location = FakeLocationProvider(UbicacionGps(-34.6037, -58.3816, 12.0))
        val haptics = FakeHaptics()
        val vm = sosViewModel(clock, location, haptics, this)

        vm.onHoldStart()
        clock.now = 3000L
        vm.onTick()
        testScheduler.advanceUntilIdle() // run the async fire() pipeline

        assertIs<SosState.Triggered>(vm.state.value)
        assertEquals(1, location.calls, "location provider consumed exactly once on trigger")
        assertTrue(haptics.vibrated, "haptic feedback on successful activation (REQ-SOS-001 SHOULD)")
    }

    @Test
    fun nullLocationStillTriggersAlert() = runTest {
        // REQ-SOS-003: denied/unavailable location → alert still fires (null coords).
        val clock = FakeClock(0L)
        val location = FakeLocationProvider(null)
        val haptics = FakeHaptics()
        val vm = sosViewModel(clock, location, haptics, this)

        vm.onHoldStart()
        clock.now = 3000L
        vm.onTick()
        testScheduler.advanceUntilIdle()

        assertIs<SosState.Triggered>(vm.state.value, "alert must fire even with null location")
        assertEquals(1, location.calls)
        assertTrue(haptics.vibrated)
    }

    @Test
    fun resetReturnsToIdleFromPressing() = runTest {
        val clock = FakeClock(0L)
        val vm = sosViewModel(clock, FakeLocationProvider(), FakeHaptics(), this)

        vm.onHoldStart()
        clock.now = 1000L
        vm.onTick()
        assertIs<SosState.Pressing>(vm.state.value)

        vm.reset()

        assertEquals(SosState.Idle, vm.state.value)
        assertEquals(0f, vm.state.value.progress)
    }

    @Test
    fun tickAfterTriggeredDoesNotRefire() = runTest {
        // Fires-once guarantee: subsequent ticks after Triggered are inert.
        val clock = FakeClock(0L)
        val location = FakeLocationProvider(UbicacionGps(1.0, 2.0, null))
        val haptics = FakeHaptics()
        val vm = sosViewModel(clock, location, haptics, this)

        vm.onHoldStart()
        clock.now = 3000L
        vm.onTick()
        testScheduler.advanceUntilIdle()
        clock.now = 3100L
        vm.onTick()
        testScheduler.advanceUntilIdle()

        assertEquals(SosState.Triggered, vm.state.value)
        assertEquals(1, location.calls, "pipeline must run exactly once despite further ticks")
        assertEquals(1, haptics.calls)
    }
}

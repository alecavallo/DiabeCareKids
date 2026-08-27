package com.diabecarekids.app.sos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SosHoldStateMachineTest {

    /** Synthetic clock driven manually so all timing is deterministic and offline. */
    private class FakeClock(var now: Long = 0L) {
        fun tick() = { now }
    }

    @Test
    fun holdStartEntersPressingAtZeroProgress() {
        val clock = FakeClock(1_000L)
        val machine = SosHoldStateMachine(clock = clock.tick())

        machine.holdStart()

        assertEquals(SosState.Pressing(0f), machine.state.value)
    }

    @Test
    fun progressScalesWithElapsedTime() {
        val clock = FakeClock(0L)
        val machine = SosHoldStateMachine(clock = clock.tick())
        machine.holdStart()

        clock.now = 1500L
        machine.onTick()
        assertEquals(SosState.Pressing(0.5f), machine.state.value)

        clock.now = 2250L
        machine.onTick()
        assertEquals(SosState.Pressing(0.75f), machine.state.value)
    }

    @Test
    fun earlyReleaseBeforeThreeSecondsResetsToIdleWithZeroProgress() {
        // REQ-SOS-001 / INV-003 early-release scenario: 2.5s hold then release.
        val clock = FakeClock(0L)
        val machine = SosHoldStateMachine(clock = clock.tick())
        machine.holdStart()

        clock.now = 2500L
        machine.onTick()
        assertEquals(SosState.Pressing(2_500f / 3_000f), machine.state.value)

        machine.holdEnd()

        assertEquals(SosState.Idle, machine.state.value)
        assertEquals(0f, machine.state.value.progress)
    }

    @Test
    fun exactThreeSecondBoundaryArmsAndIgnoresSubsequentRelease() {
        // REQ-SOS-001 boundary scenario: hold reaches exactly 3.0s → arming
        // regardless of a subsequent release.
        val clock = FakeClock(0L)
        val machine = SosHoldStateMachine(clock = clock.tick())
        machine.holdStart()

        clock.now = 3000L
        machine.onTick()
        assertEquals(SosState.Arming, machine.state.value)

        // Release after arming is a no-op (point of no return).
        machine.holdEnd()
        assertEquals(SosState.Arming, machine.state.value)
    }

    @Test
    fun releaseAfterArmingIsIgnored() {
        val clock = FakeClock(0L)
        val machine = SosHoldStateMachine(clock = clock.tick())
        machine.holdStart()

        clock.now = 3000L
        machine.onTick()
        assertIs<SosState.Arming>(machine.state.value)

        machine.holdEnd()
        assertIs<SosState.Arming>(machine.state.value)
    }

    @Test
    fun onTriggerConfirmedAdvancesArmingToTriggered() {
        val clock = FakeClock(0L)
        val machine = SosHoldStateMachine(clock = clock.tick())
        machine.holdStart()

        clock.now = 3000L
        machine.onTick()
        assertEquals(SosState.Arming, machine.state.value)

        machine.onTriggerConfirmed()

        assertEquals(SosState.Triggered, machine.state.value)
    }

    @Test
    fun onTriggerConfirmedIsNoOpBeforeArming() {
        val clock = FakeClock(0L)
        val machine = SosHoldStateMachine(clock = clock.tick())
        machine.holdStart()

        // Confirm before reaching 3.0s must not trigger.
        clock.now = 2000L
        machine.onTick()
        machine.onTriggerConfirmed()

        assertTrue(machine.state.value is SosState.Pressing)
    }

    @Test
    fun ticksAfterArmingAreNoOp() {
        val clock = FakeClock(0L)
        val machine = SosHoldStateMachine(clock = clock.tick())
        machine.holdStart()

        clock.now = 3000L
        machine.onTick()
        assertEquals(SosState.Arming, machine.state.value)

        clock.now = 5000L
        machine.onTick()
        assertEquals(SosState.Arming, machine.state.value)
    }

    @Test
    fun holdStartIsNoOpWhilePressingOrArmed() {
        val clock = FakeClock(0L)
        val machine = SosHoldStateMachine(clock = clock.tick())
        machine.holdStart()

        clock.now = 1000L
        machine.onTick()
        val progressed = machine.state.value

        // A second holdStart while pressing must not reset the clock.
        machine.holdStart()
        assertEquals(progressed, machine.state.value)
    }

    @Test
    fun resetReturnsToIdle() {
        val clock = FakeClock(0L)
        val machine = SosHoldStateMachine(clock = clock.tick())
        machine.holdStart()

        clock.now = 3000L
        machine.onTick()
        assertEquals(SosState.Arming, machine.state.value)

        machine.reset()

        assertEquals(SosState.Idle, machine.state.value)
    }
}

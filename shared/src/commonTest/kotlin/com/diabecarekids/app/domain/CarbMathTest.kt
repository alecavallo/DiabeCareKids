package com.diabecarekids.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class CarbMathTest {

    @Test
    fun eightyPercentOfFiftyIsForty() {
        // REQ-MEAL-003 scenario: 50g estimate, 80% consumed → 40g.
        assertEquals(40.0, calcularCarbohidratosReales(estimados = 50.0, porcentaje = 80))
    }

    @Test
    fun hundredPercentKeepsEstimate() {
        assertEquals(50.0, calcularCarbohidratosReales(estimados = 50.0, porcentaje = 100))
    }

    @Test
    fun zeroPercentYieldsZero() {
        assertEquals(0.0, calcularCarbohidratosReales(estimados = 50.0, porcentaje = 0))
    }

    @Test
    fun rawDoubleIsNotRoundedForDisplay() {
        // Stored raw (0.03), NOT rounded to a display precision (0.0). UI rounds
        // to 1 decimal later — design decision carried.
        assertEquals(0.03, calcularCarbohidratosReales(estimados = 1.0, porcentaje = 3))
    }
}

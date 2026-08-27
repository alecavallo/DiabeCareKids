package com.diabecarekids.app.domain

/**
 * Real carbohydrates consumed (REQ-MEAL-003):
 *
 *     carbohidratos_reales = estimados * (porcentaje / 100)
 *
 * Returns the raw [Double] with NO rounding. Presentation layers round to
 * 1 decimal for display only (design decision carried). Pure and unit-testable.
 */
fun calcularCarbohidratosReales(estimados: Double, porcentaje: Int): Double =
    estimados * porcentaje / 100.0

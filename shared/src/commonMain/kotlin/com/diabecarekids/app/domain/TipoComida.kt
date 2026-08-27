package com.diabecarekids.app.domain

import kotlinx.serialization.Serializable

/**
 * Meal type for a [RegistroComida].
 */
@Serializable
enum class TipoComida {
    DESAYUNO,   // Breakfast
    ALMUERZO,   // Lunch
    MERIENDA,   // Snack
    CENA,       // Dinner
}

package com.diabecarekids.app.domain

/**
 * A food suggestion card returned by the nutrition repository (USDA only).
 *
 * [carbsGrams] is the carbohydrate estimate for this food and is editable by
 * the user before persistence (INV-002), regardless of [source].
 */
data class FoodItem(
    val name: String,
    val carbsGrams: Double,
    val source: CarbSource,
)

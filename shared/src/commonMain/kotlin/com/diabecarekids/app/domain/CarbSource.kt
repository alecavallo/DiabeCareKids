package com.diabecarekids.app.domain

import kotlinx.serialization.Serializable

/**
 * Source of a carbohydrate estimate (REQ-MEAL-002).
 */
@Serializable
enum class CarbSource {
    USDA,      // Primary tier: US Department of Agriculture FoodData Central
    GEMINI_AI, // Secondary tier: AI estimation, tagged "[AI Estimated]"
    MANUAL,    // Final tier: user-entered value
}

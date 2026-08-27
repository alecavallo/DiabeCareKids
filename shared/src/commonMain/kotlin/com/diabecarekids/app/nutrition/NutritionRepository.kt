package com.diabecarekids.app.nutrition

import com.diabecarekids.app.domain.FoodItem

/**
 * Public nutrition facade used by the UI layer.
 */
interface NutritionRepository {

    /** Resolves carbs for [query]; always returns an editable carb value. */
    suspend fun resolveCarbs(query: String): CarbResolution

    /** USDA suggestion cards for [query]; empty when offline or unknown. */
    suspend fun suggestFoods(query: String): List<FoodItem>
}

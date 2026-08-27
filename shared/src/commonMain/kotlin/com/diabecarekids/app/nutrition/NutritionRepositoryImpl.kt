package com.diabecarekids.app.nutrition

import com.diabecarekids.app.domain.FoodItem

/**
 * Composes the carb-resolution engine with the USDA datasource for suggestion
 * cards. Suggestion lookup never throws — failures surface as an empty list
 * (empty when offline).
 */
class NutritionRepositoryImpl(
    private val engine: CarbResolutionEngine,
    private val usda: UsdaDataSource,
) : NutritionRepository {

    override suspend fun resolveCarbs(query: String): CarbResolution = engine.resolve(query)

    override suspend fun suggestFoods(query: String): List<FoodItem> = try {
        usda.searchFoods(query)
    } catch (_: Exception) {
        emptyList()
    }
}

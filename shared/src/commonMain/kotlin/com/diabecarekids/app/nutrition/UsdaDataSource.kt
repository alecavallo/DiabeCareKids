package com.diabecarekids.app.nutrition

import com.diabecarekids.app.domain.FoodItem

/**
 * USDA FoodData Central lookup. Implementations are Ktor-backed
 * ([UsdaApiClient]) in production or fakes in tests.
 */
interface UsdaDataSource {
    /**
     * Searches USDA for foods matching [query]. Returns empty when the query
     * is unknown, the source returns 404/empty, or the source is unreachable.
     */
    suspend fun searchFoods(query: String): List<FoodItem>
}

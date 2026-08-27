package com.diabecarekids.app.nutrition

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.FoodItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Thin Ktor adapter over [UsdaDataSource] for the USDA FoodData Central API.
 * Used for both single-food carb resolution (top hit) and suggestion cards.
 *
 * JSON is handled explicitly (no ContentNegotiation on the injected client) so
 * tests can drive it with a plain MockEngine client. Responses use
 * [json] = `ignoreUnknownKeys` so extra fields don't break parsing.
 */
class UsdaApiClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : UsdaDataSource {

    override suspend fun searchFoods(query: String): List<FoodItem> {
        val response = client.get("$baseUrl/foods/search") {
            parameter("api_key", apiKey)
            parameter("query", query)
        }
        if (response.status == HttpStatusCode.NotFound) return emptyList()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("USDA lookup failed: ${response.status}")
        }
        val body = json.decodeFromString<UsdaSearchResponse>(response.bodyAsText())
        return body.foods.mapNotNull { food ->
            val carbs = food.carbohydrateGrams ?: return@mapNotNull null
            FoodItem(name = food.description, carbsGrams = carbs, source = CarbSource.USDA)
        }
    }

    @Serializable
    internal data class UsdaSearchResponse(val foods: List<UsdaFood> = emptyList())

    @Serializable
    internal data class UsdaFood(
        val fdcId: Long = 0,
        val description: String = "",
        val foodNutrients: List<UsdaNutrient> = emptyList(),
    ) {
        /** "Carbohydrate, by difference" = nutrientId 1005 (FDC v1; 205 was the legacy NDB nutrientNumber). */
        val carbohydrateGrams: Double?
            get() = foodNutrients.firstOrNull { it.nutrientId == CARB_NUTRIENT_ID }?.value
    }

    @Serializable
    internal data class UsdaNutrient(val nutrientId: Int? = null, val value: Double? = null)

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.nal.usda.gov/fdc/v1"
        const val CARB_NUTRIENT_ID = 1005
    }
}

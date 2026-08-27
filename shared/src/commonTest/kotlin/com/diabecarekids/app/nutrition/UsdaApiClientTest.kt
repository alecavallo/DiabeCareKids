package com.diabecarekids.app.nutrition

import com.diabecarekids.app.domain.FoodItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for [UsdaApiClient] via Ktor MockEngine — no network.
 * Verifies URL/query mapping and USDA JSON → FoodItem decoding.
 */
class UsdaApiClientTest {

    @Test
    fun buildsUrlAndParsesCarbNutrient() = runTest {
        val engine = MockEngine { request ->
            assertTrue(request.url.toString().contains("/v1/foods/search"))
            assertEquals("test-key", request.url.parameters["api_key"])
            assertEquals("banana", request.url.parameters["query"])
            respond(
                content = """
                    {
                      "foods": [
                        {
                          "fdcId": 173944,
                          "description": "Bananas, raw",
                          "foodNutrients": [
                            { "nutrientId": 205, "nutrientName": "Carbohydrate, by difference", "value": 22.84 }
                          ]
                        }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val api = UsdaApiClient(client, apiKey = "test-key")

        val foods = api.searchFoods("banana")

        assertEquals(1, foods.size)
        assertEquals("Bananas, raw", foods[0].name)
        assertEquals(22.84, foods[0].carbsGrams)
    }

    @Test
    fun notFoundReturnsEmptyList() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val client = HttpClient(engine)
        val api = UsdaApiClient(client, apiKey = "test-key")

        val result = api.searchFoods("zzz")

        assertEquals(emptyList<FoodItem>(), result)
    }

    @Test
    fun serverErrorThrowsForFailDown() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        val client = HttpClient(engine)
        val api = UsdaApiClient(client, apiKey = "test-key")

        try {
            api.searchFoods("banana")
            throw AssertionError("expected exception to fail down the chain")
        } catch (expected: IllegalStateException) {
            // Exceptions must propagate so the engine falls through to Gemini.
            assertTrue(expected.message.orEmpty().contains("USDA lookup failed"))
        }
    }
}

package com.diabecarekids.app.nutrition

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [GeminiApiClient] via Ktor MockEngine — no network.
 * Verifies URL/query mapping and response JSON → carb value decoding.
 */
class GeminiApiClientTest {

    @Test
    fun buildsUrlAndParsesNumberFromResponse() = runTest {
        val engine = MockEngine { request ->
            assertTrue(request.url.toString().contains(":generateContent"))
            assertEquals("test-key", request.url.parameters["key"])
            respond(
                content = """
                    {
                      "candidates": [
                        { "content": { "parts": [ { "text": "35 grams" } ] } }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val api = GeminiApiClient(client, apiKey = "test-key")

        assertEquals(35.0, api.estimateCarbs("comida"))
    }

    @Test
    fun failureReturnsNull() = runTest {
        val engine = MockEngine {
            respond("""{"error":"boom"}""", HttpStatusCode.InternalServerError)
        }
        val client = HttpClient(engine)
        val api = GeminiApiClient(client, apiKey = "test-key")

        assertNull(api.estimateCarbs("comida"))
    }

    @Test
    fun notFoundReturnsNull() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val client = HttpClient(engine)
        val api = GeminiApiClient(client, apiKey = "test-key")

        assertNull(api.estimateCarbs("comida"))
    }
}

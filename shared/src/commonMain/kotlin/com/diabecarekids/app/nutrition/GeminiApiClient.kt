package com.diabecarekids.app.nutrition

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Thin Ktor adapter over [GeminiDataSource] for the Gemini generative API.
 * Estimates carbs for foods USDA could not resolve.
 *
 * JSON is handled explicitly (no ContentNegotiation on the injected client) so
 * tests can drive it with a plain MockEngine client.
 */
class GeminiApiClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : GeminiDataSource {

    override suspend fun estimateCarbs(query: String): Double? {
        val prompt = "Estimate the carbohydrate content in grams of: \"$query\". " +
            "Respond with only a number (grams)."
        val response = client.post("$baseUrl/models/gemini-1.5-flash:generateContent") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            parameter("key", apiKey)
            setBody(
                json.encodeToString(
                    GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))))
                )
            )
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) return null
        val body = json.decodeFromString<GeminiResponse>(response.bodyAsText())
        val text = body.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return null
        return parseGrams(text)
    }

    private fun parseGrams(text: String): Double? {
        val match = Regex("""\d+(\.\d+)?""").find(text) ?: return null
        return match.value.toDoubleOrNull()
    }

    @Serializable
    internal data class GeminiRequest(val contents: List<GeminiContent>)

    @Serializable
    internal data class GeminiContent(val parts: List<GeminiPart>)

    @Serializable
    internal data class GeminiPart(val text: String)

    @Serializable
    internal data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

    @Serializable
    internal data class GeminiCandidate(val content: GeminiContent? = null)

    private companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }
}

package com.diabecarekids.app.nutrition

/**
 * Gemini-based carbohydrate estimation. Implementations are Ktor-backed
 * ([GeminiApiClient]) in production or fakes in tests.
 */
interface GeminiDataSource {
    /** Returns an estimated carb value in grams, or null when estimation fails. */
    suspend fun estimateCarbs(query: String): Double?
}

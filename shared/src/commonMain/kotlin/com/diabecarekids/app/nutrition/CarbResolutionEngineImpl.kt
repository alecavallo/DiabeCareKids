package com.diabecarekids.app.nutrition

import com.diabecarekids.app.domain.CarbSource

/**
 * Hybrid carb-resolution engine (REQ-MEAL-002):
 *
 *   USDA (primary) → Gemini AI (secondary, tagged "[AI Estimated]") → ManualRequired
 *
 * A tier is skipped when its API key is null/blank. Any exception thrown by a
 * tier fails down the chain (USDA error → try Gemini; Gemini error →
 * ManualRequired). All resolved values remain editable before persistence.
 */
class CarbResolutionEngineImpl(
    private val config: ApiConfig,
    private val usda: UsdaDataSource,
    private val gemini: GeminiDataSource,
) : CarbResolutionEngine {

    override suspend fun resolve(foodQuery: String): CarbResolution {
        if (!config.usdaKey.isNullOrBlank()) {
            val foods = try {
                usda.searchFoods(foodQuery)
            } catch (_: Exception) {
                emptyList()
            }
            foods.firstOrNull()?.let {
                return CarbResolution.Resolved(it.carbsGrams, CarbSource.USDA)
            }
        }

        if (!config.geminiKey.isNullOrBlank()) {
            val carbs = try {
                gemini.estimateCarbs(foodQuery)
            } catch (_: Exception) {
                null
            }
            if (carbs != null) {
                return CarbResolution.Resolved(carbs, CarbSource.GEMINI_AI, AI_ESTIMATED_LABEL)
            }
        }

        return CarbResolution.ManualRequired
    }

    companion object {
        const val AI_ESTIMATED_LABEL = "[AI Estimated]"
    }
}

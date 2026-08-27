package com.diabecarekids.app.nutrition

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.FoodItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fallback-chain tests (REQ-MEAL-002) using fake datasources — zero network.
 */
class FallbackChainTest {

    /** Fake USDA source: returns a fixed food list, or throws to simulate a down source. */
    class FakeUsdaDataSource(
        var foods: List<FoodItem> = emptyList(),
        var throwOnSearch: Boolean = false,
    ) : UsdaDataSource {
        override suspend fun searchFoods(query: String): List<FoodItem> {
            if (throwOnSearch) throw IllegalStateException("USDA down")
            return foods
        }
    }

    /** Fake Gemini source: returns a fixed carb value, or throws/null to simulate a failure. */
    class FakeGeminiDataSource(
        var carbs: Double? = null,
        var throwOnEstimate: Boolean = false,
    ) : GeminiDataSource {
        override suspend fun estimateCarbs(query: String): Double? {
            if (throwOnEstimate) throw IllegalStateException("Gemini down")
            return carbs
        }
    }

    private fun engine(
        config: ApiConfig,
        usda: FakeUsdaDataSource = FakeUsdaDataSource(),
        gemini: FakeGeminiDataSource = FakeGeminiDataSource(),
    ): CarbResolutionEngineImpl = CarbResolutionEngineImpl(config, usda, gemini)

    @Test
    fun usdaHitReturnsUsdaValueAndIsEditable() = runTest {
        val usda = FakeUsdaDataSource(foods = listOf(FoodItem("Banana", 27.0, CarbSource.USDA)))
        val engine = engine(ApiConfig(usdaKey = "u", geminiKey = "g"), usda)

        val result = engine.resolve("banana")

        assertIs<CarbResolution.Resolved>(result)
        assertEquals(CarbSource.USDA, result.source)
        assertEquals(27.0, result.carbsGrams)
        // Editable (INV-002): the resolved value can be reassigned before save.
        val edited = result.copy(carbsGrams = 20.0)
        assertEquals(20.0, edited.carbsGrams)
    }

    @Test
    fun usdaEmptyFallsBackToGeminiWithAiTag() = runTest {
        // USDA empty/404 → Gemini, tagged "[AI Estimated]", still editable.
        val usda = FakeUsdaDataSource(foods = emptyList())
        val gemini = FakeGeminiDataSource(carbs = 35.0)
        val engine = engine(ApiConfig(usdaKey = "u", geminiKey = "g"), usda, gemini)

        val result = engine.resolve("comida rara")

        assertIs<CarbResolution.Resolved>(result)
        assertEquals(CarbSource.GEMINI_AI, result.source)
        assertEquals(CarbResolutionEngineImpl.AI_ESTIMATED_LABEL, result.label)
        assertEquals(35.0, result.carbsGrams)
    }

    @Test
    fun usdaExceptionFailsDownToGemini() = runTest {
        val usda = FakeUsdaDataSource(throwOnSearch = true)
        val gemini = FakeGeminiDataSource(carbs = 22.0)
        val engine = engine(ApiConfig(usdaKey = "u", geminiKey = "g"), usda, gemini)

        val result = engine.resolve("anything")

        assertIs<CarbResolution.Resolved>(result)
        assertEquals(CarbSource.GEMINI_AI, result.source)
        assertEquals(22.0, result.carbsGrams)
    }

    @Test
    fun allSourcesFailReturnsManualRequired() = runTest {
        val usda = FakeUsdaDataSource(throwOnSearch = true)
        val gemini = FakeGeminiDataSource(throwOnEstimate = true)
        val engine = engine(ApiConfig(usdaKey = "u", geminiKey = "g"), usda, gemini)

        assertEquals(CarbResolution.ManualRequired, engine.resolve("anything"))
    }

    @Test
    fun tierSkippedWhenKeyMissing() = runTest {
        // No USDA key → USDA tier skipped even though it would have matched.
        val usda = FakeUsdaDataSource(foods = listOf(FoodItem("Banana", 27.0, CarbSource.USDA)))
        val gemini = FakeGeminiDataSource(carbs = 30.0)
        val engine = engine(ApiConfig(usdaKey = null, geminiKey = "g"), usda, gemini)

        val result = engine.resolve("banana")

        assertIs<CarbResolution.Resolved>(result)
        assertEquals(CarbSource.GEMINI_AI, result.source)
    }

    @Test
    fun noKeysAtAllReturnsManualRequired() = runTest {
        val usda = FakeUsdaDataSource(foods = listOf(FoodItem("Banana", 27.0, CarbSource.USDA)))
        val gemini = FakeGeminiDataSource(carbs = 30.0)
        val engine = engine(ApiConfig(usdaKey = null, geminiKey = null), usda, gemini)

        assertEquals(CarbResolution.ManualRequired, engine.resolve("banana"))
    }

    @Test
    fun repositorySuggestionLookupNeverThrows() = runTest {
        val usda = FakeUsdaDataSource(throwOnSearch = true)
        val repo = NutritionRepositoryImpl(
            engine = engine(ApiConfig(usdaKey = "u", geminiKey = "g"), usda),
            usda = usda,
        )

        assertTrue(repo.suggestFoods("banana").isEmpty())
    }
}

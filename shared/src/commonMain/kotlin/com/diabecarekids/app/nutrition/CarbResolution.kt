package com.diabecarekids.app.nutrition

import com.diabecarekids.app.domain.CarbSource

/** Result of a carbohydrate resolution attempt (REQ-MEAL-002). */
sealed interface CarbResolution {

    /**
     * A resolvable carb value. [carbsGrams] is editable by the user before
     * persistence regardless of [source] (INV-002). [label] is non-null only
     * for AI-estimated results (tagged "[AI Estimated]").
     */
    data class Resolved(
        val carbsGrams: Double,
        val source: CarbSource,
        val label: String? = null,
    ) : CarbResolution

    /** No automated source produced a value — the UI must prompt manual input. */
    data object ManualRequired : CarbResolution
}

/** Resolves a free-text food query into an editable carb estimate. */
interface CarbResolutionEngine {
    suspend fun resolve(foodQuery: String): CarbResolution
}

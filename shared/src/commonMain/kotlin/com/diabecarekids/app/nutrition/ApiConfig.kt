package com.diabecarekids.app.nutrition

/**
 * API credentials for the hybrid carb-resolution tiers (REQ-MEAL-002).
 *
 * Keys are env-injected and never committed to the repo. A resolution tier is
 * skipped when its key is null/blank. Env var names: [USDA_API_KEY_ENV] /
 * [GEMINI_API_KEY_ENV].
 */
data class ApiConfig(
    val usdaKey: String? = null,
    val geminiKey: String? = null,
) {
    companion object {
        /** Environment variable names — secrets, never committed. */
        const val USDA_API_KEY_ENV = "USDA_API_KEY"
        const val GEMINI_API_KEY_ENV = "GEMINI_API_KEY"

        /** Builds a config from an env lookup, e.g. `System::getenv`. */
        fun fromEnvironment(env: (String) -> String?): ApiConfig =
            ApiConfig(
                usdaKey = env(USDA_API_KEY_ENV),
                geminiKey = env(GEMINI_API_KEY_ENV),
            )
    }
}

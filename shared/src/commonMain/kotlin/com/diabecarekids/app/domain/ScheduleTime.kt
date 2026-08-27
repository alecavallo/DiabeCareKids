package com.diabecarekids.app.domain

/**
 * A validated wall-clock time of day, independent of any platform calendar.
 *
 * Parsed strictly from a "HH:mm" string ([parse]) so invalid inputs fail fast
 * at the engine boundary instead of surfacing as confusing trigger times.
 * Used to map the habitual meal times in [ConfiguracionHorarios] into epoch
 * millis via the [com.diabecarekids.app.platform.todayAtLocalTimeMillis] seam.
 */
data class LocalTimeOfDay(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23) { "hour must be in 0..23, was $hour" }
        require(minute in 0..59) { "minute must be in 0..59, was $minute" }
    }

    companion object {
        // Strict 24h "HH:mm": leading zero required, valid hour/minute ranges.
        private val HH_MM = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")

        /**
         * Parses a strict "HH:mm" string into a [LocalTimeOfDay], throwing
         * [IllegalArgumentException] for any non-conforming input.
         */
        fun parse(value: String): LocalTimeOfDay {
            val match = HH_MM.matchEntire(value)
                ?: throw IllegalArgumentException("Invalid 'HH:mm' time: \"$value\"")
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            return LocalTimeOfDay(hour, minute)
        }
    }
}

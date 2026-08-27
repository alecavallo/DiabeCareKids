package com.diabecarekids.app.format

/**
 * Shared presentation formatters (CAP-004, design DECISION: lift to shared so
 * the PDF export builder and the UI produce identical strings for the same
 * record — report values must equal screen values).
 *
 * [formatEpochMillis] is a minimal epoch-millis -> wall-clock UTC formatter
 * (design decision: no kotlinx-datetime dependency; records are stored as epoch
 * millis and treated as wall-clock-as-UTC). Pure arithmetic (civil-from-days,
 * Howard Hinnant's algorithm) so it runs in commonMain with no platform date
 * APIs. Format: "yyyy-MM-dd HH:mm".
 */
fun formatEpochMillis(millis: Long): String {
    val days = Math.floorDiv(millis, 86_400_000L)
    val secondsOfDay = Math.floorMod(millis, 86_400_000L) / 1000L
    val hour = (secondsOfDay / 3600L).toInt()
    val minute = ((secondsOfDay % 3600L) / 60L).toInt()
    val (year, month, day) = civilFromDays(days)
    return "%04d-%02d-%02d %02d:%02d".format(year, month, day, hour, minute)
}

/** Formats a gram value for display, rounding to 1 decimal to avoid float
 *  artifacts like 19.413999999999998 rendering as-is (ID-ROUND). */
fun formatGrams(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

/** Howard Hinnant's civil_from_days: days since epoch -> (year, month, day). */
private fun civilFromDays(z: Long): Triple<Int, Int, Int> {
    val shifted = z + 719468L
    val era = (if (shifted >= 0) shifted else shifted - 146096L) / 146097L
    val dayOfEra = shifted - era * 146097L // [0, 146096]
    val yearOfEra = (dayOfEra - dayOfEra / 1460L + dayOfEra / 36524L - dayOfEra / 146096L) / 365L // [0, 399]
    val year = yearOfEra + era * 400L
    val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L) // [0, 365]
    val monthPrime = (5L * dayOfYear + 2L) / 153L // [0, 11]
    val day = dayOfYear - (153L * monthPrime + 2L) / 5L + 1L // [1, 31]
    val month = if (monthPrime < 10L) monthPrime + 3L else monthPrime - 9L // [1, 12]
    val adjustedYear = if (month <= 2L) year + 1L else year
    return Triple(adjustedYear.toInt(), month.toInt(), day.toInt())
}

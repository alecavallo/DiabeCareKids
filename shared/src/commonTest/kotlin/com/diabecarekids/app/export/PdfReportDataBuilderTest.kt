package com.diabecarekids.app.export

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.TipoComida
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [buildReportData] (CAP-004). Pure logic, no coroutines:
 * inclusive range bounds, ascending sort, null->"—" mapping, grams rounding
 * and the shared date format.
 */
class PdfReportDataBuilderTest {

    private fun reg(
        id: String,
        at: Long,
        initialBg: Double = 120.0,
        twoHourBg: Double? = 200.0,
        realCarbs: Double? = 50.0,
    ) = RegistroComida(
        id = id,
        fecha_hora_inicio = at,
        tipo_comida = TipoComida.ALMUERZO,
        glicemia_inicial = initialBg,
        nombre_alimento = "food $id",
        carbohidratos_estimados = 50.0,
        fuente_carbohidratos = CarbSource.MANUAL,
        porcentaje_consumido = 100,
        carbohidratos_reales = realCarbs,
        es_registro_historico = true,
        creado_por_usuario_id = "local",
        ultima_modificacion = at,
    )

    @Test
    fun filtersOnlyInRangeInclusive() {
        val base = 1_699_999_980_000L // 2023-11-14 22:13 UTC (minute-aligned)
        val from = base
        val to = base + 86_400_000L // +1 day
        val records = listOf(
            reg("before", at = from - 86_400_000L),
            reg("atFrom", at = from), // inclusive lower bound
            reg("inside", at = base + 3_600_000L),
            reg("atTo", at = to), // inclusive upper bound
            reg("after", at = to + 86_400_000L),
        )

        val data = buildReportData(records, from, to)

        val epochs = data.rows.map { parseFormattedEpochMillis(it.dateText) }
        assertEquals(
            listOf(from, base + 3_600_000L, to),
            epochs,
            "only records within [fromMillis, toMillis] inclusive must be exported",
        )
    }

    @Test
    fun sortsAscendingByFechaHoraInicio() {
        val base = 1_699_999_980_000L
        val records = listOf(
            reg("newest", at = base + 7_200_000L),
            reg("oldest", at = base),
            reg("middle", at = base + 3_600_000L),
        )

        val data = buildReportData(records, 0L, Long.MAX_VALUE)

        // Rows don't carry ids, so assert the chronological order via the parsed epoch
        // (the format string is fixed: yyyy-MM-dd HH:mm).
        val epochs = data.rows.map { row -> parseFormattedEpochMillis(row.dateText) }
        assertEquals(
            listOf(base, base + 3_600_000L, base + 7_200_000L),
            epochs,
            "rows must be sorted by fecha_hora_inicio ascending (oldest -> newest)",
        )
    }

    @Test
    fun mapsNullOptionalFieldsToDash() {
        val records = listOf(
            reg("nulls", at = 100L, twoHourBg = null, realCarbs = null),
        )

        val data = buildReportData(records, 0L, Long.MAX_VALUE)

        assertEquals("—", data.rows.single().twoHourBgText)
        assertEquals("—", data.rows.single().realCarbsText)
    }

    @Test
    fun roundsGramsToOneDecimal() {
        val records = listOf(
            reg("round", at = 100L, initialBg = 19.413999999999998, realCarbs = 30.0),
        )

        val data = buildReportData(records, 0L, Long.MAX_VALUE)

        assertEquals("19.4", data.rows.single().preMealBgText)
        assertEquals("30", data.rows.single().realCarbsText)
    }

    @Test
    fun formatsDateAsYyyyMmDdHhMm() {
        val records = listOf(reg("dated", at = 1_700_000_000_000L))

        val data = buildReportData(records, 0L, Long.MAX_VALUE)

        // 2023-11-14 22:13:20 UTC (epoch 1700000000000), minutes -> 22:13.
        assertEquals("2023-11-14 22:13", data.rows.single().dateText)
    }

    /** Inverse of formatEpochMillis, for asserting chronological order without platform date APIs. */
    private fun parseFormattedEpochMillis(text: String): Long {
        val (datePart, timePart) = text.split(" ")
        val (y, mo, d) = datePart.split("-").map { it.toLong() }
        val (h, mi) = timePart.split(":").map { it.toLong() }
        val days = daysFromCivil(y, mo, d)
        return days * 86_400_000L + h * 3_600_000L + mi * 60_000L
    }

    /** days_from_civil (Hinnant) inverse of civil_from_days, for test assertion. */
    private fun daysFromCivil(y: Long, m: Long, d: Long): Long {
        val adjustedY = if (m <= 2) y - 1 else y
        val era = (if (adjustedY >= 0) adjustedY else adjustedY - 399) / 400
        val yoe = adjustedY - era * 400 // [0, 399]
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1 // [0, 365]
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy // [0, 146096]
        return era * 146097 + doe - 719468
    }
}

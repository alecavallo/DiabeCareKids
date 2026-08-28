package com.diabecarekids.app.reminder

import com.diabecarekids.app.domain.ConfiguracionHorarios
import com.diabecarekids.app.domain.ReminderScheduleEngine
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.persistence.RecentRecordWindowCheck
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MealReminderRunnerTest {

    private fun minute(hour: Int, minute: Int): Long = hour * 60L + minute

    private val config = ConfiguracionHorarios(
        horario_desayuno = "08:00",
        horario_almuerzo = "12:30",
        horario_merienda = "17:00",
        horario_cena = "21:00",
        ventana_anticipacion_minutos = 15,
        recordatorios_activos = true,
    )

    private fun engine(nowMin: Long) = ReminderScheduleEngine(
        now = { nowMin * 60_000 },
        todayAt = { it.hour * 60L * 60_000 + it.minute * 60_000 },
        dayAt = { time, offset ->
            (time.hour * 60L + time.minute + offset * 1440L) * 60_000
        },
    )

    private fun noRecent(): RecentRecordWindowCheck = RecentRecordWindowCheck { _, _, _ -> false }

    // --- ID-WORKER-CRASH: graceful-skip branch (never throws) ---

    @Test
    fun skipWhenConfigUnavailable() = runTest {
        // Models a process where the composition root never ran: no config can be
        // loaded. The runner must SKIP gracefully — no fire, no re-arm, no throw.
        var shown = 0
        val runner = MealReminderRunner(
            engine = engine(nowMin = minute(12, 15)),
            loadConfig = { null },
            recentCheck = noRecent(),
            showReminder = { shown++ },
        )

        val run = runner.run(TipoComida.ALMUERZO)

        assertEquals(MealReminderRun.Skip, run)
        assertEquals(0, shown, "no notification should fire when config is unavailable")
    }

    @Test
    fun skipWhenGloballyDisabled() = runTest {
        var shown = 0
        val runner = MealReminderRunner(
            engine = engine(nowMin = minute(12, 15)),
            loadConfig = { config.copy(recordatorios_activos = false) },
            recentCheck = noRecent(),
            showReminder = { shown++ },
        )

        val run = runner.run(TipoComida.ALMUERZO)

        assertEquals(MealReminderRun.Skip, run)
        assertEquals(0, shown)
    }

    // --- ID-REARM: fires and re-arms for the next day; suppressed re-arms without firing ---

    @Test
    fun firesNowAndRearmsForTomorrow() = runTest {
        var shown = 0
        val runner = MealReminderRunner(
            engine = engine(nowMin = minute(12, 15)), // at ALMUERZO trigger, no recent record
            loadConfig = { config },
            recentCheck = noRecent(),
            showReminder = { shown++ },
        )

        val run = runner.run(TipoComida.ALMUERZO)

        assertTrue(run is MealReminderRun.Rearm, "expected a re-arm, got $run")
        assertEquals(true, run.fired, "a Fire decision must show the notification now")
        assertEquals(1, shown)
        // Re-armed to tomorrow's ALMUERZO trigger (12:15 + 24h).
        assertEquals((minute(12, 15) + 1440) * 60_000, run.nextTriggerAt)
    }

    @Test
    fun suppressedRearmsWithoutFiring() = runTest {
        var shown = 0
        val recent = RecentRecordWindowCheck { tipo, from, until ->
            tipo == TipoComida.ALMUERZO && minute(12, 30) * 60_000 in from..until
        }
        val runner = MealReminderRunner(
            engine = engine(nowMin = minute(12, 45)), // late delivery within grace
            loadConfig = { config },
            recentCheck = recent,
            showReminder = { shown++ },
        )

        val run = runner.run(TipoComida.ALMUERZO)

        assertTrue(run is MealReminderRun.Rearm, "expected a re-arm, got $run")
        assertFalse(run.fired, "a Suppressed decision must NOT fire a notification")
        assertEquals(0, shown, "suppressed meal must not notify")
        assertEquals((minute(12, 15) + 1440) * 60_000, run.nextTriggerAt, "still re-arms for tomorrow")
    }
}

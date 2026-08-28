package com.diabecarekids.app.reminder

import com.diabecarekids.app.domain.ConfiguracionHorarios
import com.diabecarekids.app.domain.ReminderDecision
import com.diabecarekids.app.domain.ReminderScheduleEngine
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.persistence.HorariosStore
import com.diabecarekids.app.persistence.InMemoryHorariosStore
import com.diabecarekids.app.persistence.RecentRecordWindowCheck
import com.diabecarekids.app.platform.MealReminderScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Records scheduler calls for [MealReminderOrchestrator] tests. */
class FakeMealReminderScheduler : MealReminderScheduler {
    val scheduled = mutableListOf<Pair<TipoComida, Long>>()
    var cancelAllCalls = 0

    override fun schedule(tipo: TipoComida, triggerAt: Long) {
        scheduled += tipo to triggerAt
    }

    override fun cancelAll() {
        cancelAllCalls++
    }
}

class MealReminderOrchestratorTest {

    private fun minute(hour: Int, minute: Int): Long = hour * 60L + minute

    private fun orchestrator(
        nowMin: Long,
        config: ConfiguracionHorarios,
        recentCheck: RecentRecordWindowCheck,
        horariosStore: HorariosStore = InMemoryHorariosStore(config),
    ): Pair<MealReminderOrchestrator, FakeMealReminderScheduler> {
        val engine = ReminderScheduleEngine(
            now = { nowMin * 60_000 },
            todayAt = { it.hour * 60L * 60_000 + it.minute * 60_000 },
        )
        val scheduler = FakeMealReminderScheduler()
        val orchestrator = MealReminderOrchestrator(engine, horariosStore, recentCheck, scheduler)
        return orchestrator to scheduler
    }

    private fun noRecent(): RecentRecordWindowCheck = RecentRecordWindowCheck { _, _, _ -> false }

    private val defaultConfig = ConfiguracionHorarios(
        horario_desayuno = "08:00",
        horario_almuerzo = "12:30",
        horario_merienda = "17:00",
        horario_cena = "21:00",
        ventana_anticipacion_minutos = 15,
        recordatorios_activos = true,
    )

    @Test
    fun schedulesFutureMealTriggers() = runTest {
        // Now = 07:00 → every meal is before its trigger → 4 SCHEDULE calls.
        val (orchestrator, scheduler) = orchestrator(nowMin = minute(7, 0), config = defaultConfig, recentCheck = noRecent())

        orchestrator.refresh()

        assertEquals(4, scheduler.scheduled.size)
        assertEquals(0, scheduler.cancelAllCalls)
        // Spot-check DESAYUNO trigger = 07:45.
        assertTrue((TipoComida.DESAYUNO to minute(7, 45) * 60_000) in scheduler.scheduled)
    }

    @Test
    fun firesWhenWithinGraceAndNoRecentRecord() = runTest {
        // Now = 12:15 (ALMUERZO trigger); no recent record → FIRE → still scheduled.
        val (orchestrator, scheduler) = orchestrator(nowMin = minute(12, 15), config = defaultConfig, recentCheck = noRecent())

        orchestrator.refresh()

        assertTrue((TipoComida.ALMUERZO to minute(12, 15) * 60_000) in scheduler.scheduled)
        assertEquals(0, scheduler.cancelAllCalls)
    }

    @Test
    fun disabledGlobalGateCancelsAllAndSchedulesNothing() = runTest {
        val disabled = defaultConfig.copy(recordatorios_activos = false)
        val (orchestrator, scheduler) = orchestrator(nowMin = minute(7, 0), config = disabled, recentCheck = noRecent())

        orchestrator.refresh()

        assertEquals(1, scheduler.cancelAllCalls)
        assertEquals(0, scheduler.scheduled.size)
    }

    @Test
    fun suppressedDecisionSchedulesNothingForThatMeal() = runTest {
        // ALMUERZO suppressed (recent record); others before trigger → scheduled.
        val recent = RecentRecordWindowCheck { tipo, _, _ -> tipo == TipoComida.ALMUERZO }
        val (orchestrator, scheduler) = orchestrator(nowMin = minute(12, 15), config = defaultConfig, recentCheck = recent)

        orchestrator.refresh()

        assertTrue(scheduler.scheduled.none { it.first == TipoComida.ALMUERZO })
        assertEquals(0, scheduler.cancelAllCalls)
    }
}

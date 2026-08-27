package com.diabecarekids.app.domain

import com.diabecarekids.app.persistence.RecentRecordWindowCheck
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderScheduleEngineTest {

    /** Epoch scale: minutes-from-midnight. [LocalTimeOfDay] maps directly. */
    private fun minute(hour: Int, minute: Int): Long = hour * 60L + minute

    private fun config(
        desayuno: String = "08:00",
        almuerzo: String = "12:30",
        merienda: String = "17:00",
        cena: String = "21:00",
        window: Int = 15,
        activos: Boolean = true,
    ) = ConfiguracionHorarios(
        horario_desayuno = desayuno,
        horario_almuerzo = almuerzo,
        horario_merienda = merienda,
        horario_cena = cena,
        ventana_anticipacion_minutos = window,
        recordatorios_activos = activos,
    )

    private fun engine(nowMin: Long) = ReminderScheduleEngine(
        now = { nowMin * 60_000 },
        todayAt = { it.hour * 60L * 60_000 + it.minute * 60_000 },
    )

    private fun noRecent(): RecentRecordWindowCheck =
        RecentRecordWindowCheck { _, _, _ -> false }

    // --- Trigger Time Computation (Trigger = habitual − window) ---

    @Test
    fun desayunoTriggerIsHabitualMinusWindow() = runTest {
        // 08:00 − 15min → 07:45. Eval before trigger → SCHEDULE with that trigger.
        val e = engine(nowMin = minute(7, 0))
        val decision = e.evaluateFor(TipoComida.DESAYUNO, config(), noRecent())!!
        assertEquals(ReminderDecision.Schedule(TipoComida.DESAYUNO, minute(7, 45) * 60_000), decision)
    }

    @Test
    fun almuerzoTriggerIsHabitualMinusWindow() = runTest {
        // 12:30 − 15min → 12:15
        val e = engine(nowMin = minute(12, 0))
        val decision = e.evaluateFor(TipoComida.ALMUERZO, config(), noRecent())!!
        assertEquals(ReminderDecision.Schedule(TipoComida.ALMUERZO, minute(12, 15) * 60_000), decision)
    }

    @Test
    fun meriendaTriggerIsHabitualMinusWindow() = runTest {
        // 17:00 − 15min → 16:45
        val e = engine(nowMin = minute(16, 0))
        val decision = e.evaluateFor(TipoComida.MERIENDA, config(), noRecent())!!
        assertEquals(ReminderDecision.Schedule(TipoComida.MERIENDA, minute(16, 45) * 60_000), decision)
    }

    @Test
    fun cenaTriggerIsHabitualMinusWindow() = runTest {
        // 21:00 − 15min → 20:45
        val e = engine(nowMin = minute(20, 0))
        val decision = e.evaluateFor(TipoComida.CENA, config(), noRecent())!!
        assertEquals(ReminderDecision.Schedule(TipoComida.CENA, minute(20, 45) * 60_000), decision)
    }

    // --- Fire / grace (D8: FIRE only when now ∈ [trigger, trigger+60min]) ---

    @Test
    fun nowAtTriggerFiresWithoutRecentRecord() = runTest {
        val e = engine(nowMin = minute(12, 15))
        val decision = e.evaluateFor(TipoComida.ALMUERZO, config(), noRecent())!!
        assertEquals(ReminderDecision.Fire(TipoComida.ALMUERZO, minute(12, 15) * 60_000), decision)
    }

    @Test
    fun nowInsideGraceFires() = runTest {
        val e = engine(nowMin = minute(12, 45)) // 30min after trigger, within 60min grace
        val decision = e.evaluateFor(TipoComida.ALMUERZO, config(), noRecent())!!
        assertTrue(decision is ReminderDecision.Fire)
    }

    @Test
    fun nowPastGraceIsMissed() = runTest {
        val e = engine(nowMin = minute(13, 16)) // 61min after trigger
        val decision = e.evaluateFor(TipoComida.ALMUERZO, config(), noRecent())!!
        assertEquals(ReminderDecision.Missed(TipoComida.ALMUERZO), decision)
    }

    // --- Post-Logging Suppression (D3/D4) ---

    @Test
    fun suppressedWhenRecordWithinTwoHours() = runTest {
        // Reminder eval at 12:15; record at 11:30 is inside [10:15, 12:15].
        val e = engine(nowMin = minute(12, 15))
        val recent = RecentRecordWindowCheck { tipo, from, until ->
            tipo == TipoComida.ALMUERZO && minute(11, 30) * 60_000 in from..until
        }
        val decision = e.evaluateFor(TipoComida.ALMUERZO, config(), recent)!!
        assertEquals(ReminderDecision.Suppressed(TipoComida.ALMUERZO), decision)
    }

    @Test
    fun firesWhenLastRecordOutsideTwoHours() = runTest {
        // Reminder eval at 12:15; record at 08:30 is outside [10:15, 12:15].
        val e = engine(nowMin = minute(12, 15))
        val recent = RecentRecordWindowCheck { tipo, from, until ->
            tipo == TipoComida.ALMUERZO && minute(8, 30) * 60_000 in from..until
        }
        val decision = e.evaluateFor(TipoComida.ALMUERZO, config(), recent)!!
        assertTrue(decision is ReminderDecision.Fire)
    }

    @Test
    fun recordOfDifferentMealTypeDoesNotSuppress() = runTest {
        // Record is for CENA, not ALMUERZO → no suppression.
        val e = engine(nowMin = minute(12, 15))
        val recent = RecentRecordWindowCheck { tipo, _, _ -> tipo == TipoComida.CENA }
        val decision = e.evaluateFor(TipoComida.ALMUERZO, config(), recent)!!
        assertTrue(decision is ReminderDecision.Fire)
    }

    // --- Eligibility Gate (recordatorios_activos) ---

    @Test
    fun disabledGlobalGateYieldsOnlyDisabled() = runTest {
        val e = engine(nowMin = minute(7, 0))
        val decisions = e.evaluate(config(activos = false), noRecent())
        assertEquals(listOf<ReminderDecision>(ReminderDecision.Disabled), decisions)
    }

    @Test
    fun enabledGlobalGateYieldsPerMealDecisions() = runTest {
        val e = engine(nowMin = minute(7, 0)) // before every trigger → all Schedule
        val decisions = e.evaluate(config(activos = true), noRecent())
        assertEquals(4, decisions.size)
        assertTrue(decisions.all { it is ReminderDecision.Schedule })
        assertTrue(ReminderDecision.Disabled !in decisions)
    }

    // --- INV-006 lock (structural, no COLACION) ---

    @Test
    fun primaryMealsAreExactlyTheFourMealTypes() {
        assertEquals(
            listOf(TipoComida.DESAYUNO, TipoComida.ALMUERZO, TipoComida.MERIENDA, TipoComida.CENA),
            ReminderScheduleEngine.PRIMARY_MEALS,
        )
        // COLACION is unrepresentable in TipoComida by design; this locks the set.
        assertEquals(listOf("DESAYUNO", "ALMUERZO", "MERIENDA", "CENA"),
            ReminderScheduleEngine.PRIMARY_MEALS.map { it.name })
    }

    @Test
    fun evaluateForReturnsDecisionForEveryPrimaryMeal() = runTest {
        // Every primary meal must produce a decision (never null).
        val e = engine(nowMin = minute(7, 0))
        for (tipo in ReminderScheduleEngine.PRIMARY_MEALS) {
            assertTrue(e.evaluateFor(tipo, config(), noRecent()) != null)
        }
    }
}

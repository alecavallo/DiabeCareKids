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
        dayAt = { time, offset ->
            (time.hour * 60L + time.minute + offset * 1440L) * 60_000
        },
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

    // --- ID-SUPPRESS: late-delivery suppression (window upper bound = now) ---

    @Test
    fun recordLoggedBetweenTriggerAndLateDeliverySuppresses() = runTest {
        // Trigger 12:15; the worker is delivered LATE at 12:45 (within grace);
        // the meal was logged at 12:30, i.e. after the trigger but before delivery.
        // Old window ended at trigger (12:15) → would FIRE; now it must SUPPRESS.
        val e = engine(nowMin = minute(12, 45))
        val recent = RecentRecordWindowCheck { tipo, from, until ->
            tipo == TipoComida.ALMUERZO && minute(12, 30) * 60_000 in from..until
        }
        val decision = e.evaluateFor(TipoComida.ALMUERZO, config(), recent)!!
        assertEquals(ReminderDecision.Suppressed(TipoComida.ALMUERZO), decision)
    }

    @Test
    fun lateDeliveryWithoutRecentRecordStillFires() = runTest {
        // Late delivery at 12:45 but no record in [10:15, 12:45] → still FIRE.
        val e = engine(nowMin = minute(12, 45))
        val decision = e.evaluateFor(TipoComida.ALMUERZO, config(), noRecent())!!
        assertTrue(decision is ReminderDecision.Fire)
    }

    // --- ID-TZ-TIMELINE: suppression on the wall-clock-as-UTC axis in a non-UTC tz ---

    @Test
    fun utcMinus3LoggedMealSuppressesAlmuerzoOnWallClockAxis() = runTest {
        // Live-meal records are stored on the wall-clock-as-UTC axis
        // (fecha_hora_inicio = wallClockAsUtcEpochNow()). A device at UTC−3 whose
        // local wall clock reads 12:10 stores "12:10" read-as-UTC — the SAME numeric
        // value as UTC+0, i.e. it lives on the wall-clock-as-UTC axis, NOT true UTC
        // (true now would be 15:10 there). The engine's injected now/todayAt/dayAt
        // seams below model those wall-clock-as-UTC platform actuals. With a record
        // logged at wall 12:10 and the ALMUERZO trigger at 12:15 (habitual 12:30 −
        // 15min), the suppression window [10:15, now=12:20] must include the record
        // and SUPPRESS. (On the old true-UTC seam, now=15:20 → window [13:15,15:20]
        // misses the 12:10 record → false fire in every tz except UTC±0.)
        val e = ReminderScheduleEngine(
            now = { minute(12, 20) * 60_000 }, // wall clock 12:20 (within grace after 12:15 trigger)
            todayAt = { it.hour * 60L * 60_000 + it.minute * 60_000 }, // wall-as-UTC: same numeric axis
            dayAt = { time, offset ->
                (time.hour * 60L + time.minute + offset * 1440L) * 60_000
            },
        )
        // Meal logged at local 12:10, stored as wall-clock-as-UTC.
        val recordWallClockAsUtc = minute(12, 10) * 60_000
        val recent = RecentRecordWindowCheck { tipo, from, until ->
            tipo == TipoComida.ALMUERZO && recordWallClockAsUtc in from..until
        }
        val decision = e.evaluateFor(TipoComida.ALMUERZO, config(), recent)!!
        assertEquals(ReminderDecision.Suppressed(TipoComida.ALMUERZO), decision)
    }

    @Test
    fun utcMinus3LoggedMealFalseFiresWhenQueryBoundsOnTrueUtcAxis() = runTest {
        // What the OLD (buggy) seams did for a UTC−3 device at wall 12:20: the record
        // is stored wall-clock-as-UTC (12:10) but the engine's now/trigger bounds were
        // on the TRUE-UTC axis (+3h: now=15:20, ALMUERZO trigger 15:15, window
        // [13:15,15:20]). The 12:10 record ∉ window → the reminder FIRES despite the
        // meal being logged. Pins the false-fire so a reintroduced true-UTC seam fails.
        val e = ReminderScheduleEngine(
            now = { minute(15, 20) * 60_000 }, // true-UTC now (+3h vs wall 12:20)
            todayAt = { it.hour * 60L * 60_000 + it.minute * 60_000 + 180L * 60_000 }, // +3h
            dayAt = { time, offset ->
                (time.hour * 60L + time.minute + offset * 1440L) * 60_000 + 180L * 60_000
            },
        )
        val recordWallClockAsUtc = minute(12, 10) * 60_000 // stored wall-as-UTC (axis-mismatch)
        val recent = RecentRecordWindowCheck { tipo, from, until ->
            tipo == TipoComida.ALMUERZO && recordWallClockAsUtc in from..until
        }
        val decision = e.evaluateFor(TipoComida.ALMUERZO, config(), recent)!!
        // 12:10 ∉ [13:15, 15:20] → NOT suppressed → false fire (the bug).
        assertTrue(decision is ReminderDecision.Fire)
    }

    // --- ID-REARM: nextTriggerAt (daily re-arm, meal past at app-open) ---

    @Test
    fun nextTriggerTodayWhenStillAhead() = runTest {
        // Now 07:00 → DESAYUNO trigger 07:45 is still ahead → today's trigger.
        val e = engine(nowMin = minute(7, 0))
        assertEquals(minute(7, 45) * 60_000, e.nextTriggerAt(TipoComida.DESAYUNO, config()))
    }

    @Test
    fun nextTriggerTomorrowWhenTriggerAlreadyPast() = runTest {
        // Now 13:00 → ALMUERZO trigger 12:15 already past → tomorrow 12:15.
        val e = engine(nowMin = minute(13, 0))
        // Tomorrow = +1440 minutes.
        assertEquals((minute(12, 15) + 1440) * 60_000, e.nextTriggerAt(TipoComida.ALMUERZO, config()))
    }

    @Test
    fun nextTriggerTomorrowWhenNowInsideGraceAfterTrigger() = runTest {
        // Now 12:45 (within grace, after trigger 12:15) → tomorrow's trigger.
        val e = engine(nowMin = minute(12, 45))
        assertEquals((minute(12, 15) + 1440) * 60_000, e.nextTriggerAt(TipoComida.ALMUERZO, config()))
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

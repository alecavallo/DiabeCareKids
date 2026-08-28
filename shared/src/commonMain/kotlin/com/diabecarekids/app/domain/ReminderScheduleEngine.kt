package com.diabecarekids.app.domain

import com.diabecarekids.app.persistence.RecentRecordWindowCheck

/**
 * Pure, clock-injected engine that decides per-meal reminder outcomes for a
 * [ConfiguracionHorarios] at a single point in time.
 *
 * Determinism comes from the injected lambdas (no platform calendar is
 * read here):
 *  - [now] returns the current time as epoch millis.
 *  - [todayAt] maps a [LocalTimeOfDay] to the epoch millis it occurs today
 *    (the [com.diabecarekids.app.platform.todayAtLocalTimeMillis] seam).
 *  - [dayAt] maps a [LocalTimeOfDay] + day offset (0 = today) to the epoch
 *    millis it occurs (the [com.diabecarekids.app.platform.localTimeAtDayOffsetMillis]
 *    seam). Used to re-arm a reminder for the next day (ID-REARM). Must satisfy
 *    `dayAt(time, 0) == todayAt(time)`.
 *
 * ## INV-006 (structural)
 * The engine only ever iterates the fixed [PRIMARY_MEALS] list, and
 * [evaluateFor] returns null for anything outside it. [TipoComida] has no
 * COLACION value by design, so a snack reminder is unrepresentable. The
 * list is locked by a regression test.
 */
class ReminderScheduleEngine(
    private val now: () -> Long,
    private val todayAt: (LocalTimeOfDay) -> Long,
    private val dayAt: (LocalTimeOfDay, Int) -> Long,
) {

    companion object {
        /** Fixed primary meal set. INV-006: never includes COLACION. */
        val PRIMARY_MEALS: List<TipoComida> =
            listOf(TipoComida.DESAYUNO, TipoComida.ALMUERZO, TipoComida.MERIENDA, TipoComida.CENA)

        /** A reminder only fires while now is within [trigger, trigger + grace]. */
        const val MISSED_GRACE_MINUTES: Long = 60

        private const val MINUTE_MILLIS: Long = 60_000
        private const val TWO_HOURS_MILLIS: Long = 2 * 60 * MINUTE_MILLIS
    }

    /**
     * Evaluates the full primary-meal schedule. Returns only [ReminderDecision.Disabled]
     * when [ConfiguracionHorarios.recordatorios_activos] is false (global kill switch).
     */
    suspend fun evaluate(
        config: ConfiguracionHorarios,
        recentCheck: RecentRecordWindowCheck,
    ): List<ReminderDecision> {
        if (!config.recordatorios_activos) return listOf(ReminderDecision.Disabled)
        return PRIMARY_MEALS.mapNotNull { evaluateFor(it, config, recentCheck) }
    }

    /**
     * Decision for a single meal type, or null when [tipo] is not a primary
     * meal (INV-006 — COLACION is unrepresentable in [TipoComida] anyway).
     *
     * Trigger = habitual time − advance window. Decision rules:
     *  - now < trigger        → [ReminderDecision.Schedule]
     *  - trigger ≤ now ≤ trigger + [MISSED_GRACE_MINUTES]:
     *      - recent record in `[trigger − 2h, now]` → [ReminderDecision.Suppressed]
     *      - otherwise                                 → [ReminderDecision.Fire]
     *  - now > trigger + grace                          → [ReminderDecision.Missed]
     *
     * The suppression window's upper bound is `now` (execution time), not the
     * trigger, so a meal logged between the trigger and a late-delivered worker
     * (Doze) still suppresses the reminder (ID-SUPPRESS).
     */
    suspend fun evaluateFor(
        tipo: TipoComida,
        config: ConfiguracionHorarios,
        recentCheck: RecentRecordWindowCheck,
    ): ReminderDecision? {
        if (tipo !in PRIMARY_MEALS) return null
        val triggerAt = triggerAt(tipo, config)
        val current = now()
        return when {
            current < triggerAt -> ReminderDecision.Schedule(tipo, triggerAt)
            current <= triggerAt + MISSED_GRACE_MINUTES * MINUTE_MILLIS -> {
                val suppressed = recentCheck.hasRecent(
                    tipo,
                    from = triggerAt - TWO_HOURS_MILLIS,
                    until = current,
                )
                if (suppressed) ReminderDecision.Suppressed(tipo)
                else ReminderDecision.Fire(tipo, triggerAt)
            }
            else -> ReminderDecision.Missed(tipo)
        }
    }

    /** Epoch millis for the trigger of [tipo]: habitual time minus the advance window. */
    fun triggerAt(tipo: TipoComida, config: ConfiguracionHorarios): Long {
        val habitual = LocalTimeOfDay.parse(config.horarioFor(tipo))
        // Subtract on the epoch timeline so a window crossing midnight lands on
        // the correct (previous) day automatically.
        return todayAt(habitual) - config.ventana_anticipacion_minutos * MINUTE_MILLIS
    }

    /**
     * Epoch millis for the NEXT trigger of [tipo] strictly after [now] — today's
     * trigger when it is still ahead of the current time, otherwise tomorrow's
     * trigger. Enables daily re-arm: a meal already past at app-open still gets
     * scheduled for the following day, and reminders keep firing day-after-day
     * without the app being reopened (ID-REARM).
     */
    fun nextTriggerAt(tipo: TipoComida, config: ConfiguracionHorarios): Long {
        val habitual = LocalTimeOfDay.parse(config.horarioFor(tipo))
        val windowMillis = config.ventana_anticipacion_minutos * MINUTE_MILLIS
        val todayTrigger = todayAt(habitual) - windowMillis
        val current = now()
        return if (todayTrigger > current) todayTrigger else dayAt(habitual, 1) - windowMillis
    }
}

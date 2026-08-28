package com.diabecarekids.app.reminder

import com.diabecarekids.app.domain.ConfiguracionHorarios
import com.diabecarekids.app.domain.ReminderDecision
import com.diabecarekids.app.domain.ReminderScheduleEngine
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.persistence.RecentRecordWindowCheck

/**
 * Outcome of running one meal reminder at execution time (ID-WORKER-CRASH / ID-REARM).
 */
sealed interface MealReminderRun {
    /** Reminder is unavailable (no config) or globally disabled — do nothing, do NOT re-arm. */
    data object Skip : MealReminderRun

    /** Re-arm [tipo] for [nextTriggerAt]; [fired] is true when a notification was shown now. */
    data class Rearm(val nextTriggerAt: Long, val fired: Boolean) : MealReminderRun
}

/**
 * Execution-time core for a single meal reminder, shared by the Android worker
 * (and unit tests). It is intentionally free of any platform/WorkManager types so
 * it can be tested offline in commonTest.
 *
 * Behaviour:
 *  - If the configuration cannot be loaded (e.g. dependencies never populated —
 *    the graceful-skip branch of ID-WORKER-CRASH), or reminders are globally
 *    disabled, it returns [MealReminderRun.Skip]. It NEVER throws.
 *  - Otherwise it re-runs the shared engine's [ReminderScheduleEngine.evaluateFor]
 *    against the CURRENT time (re-checks post-logging suppression), fires via
 *    [showReminder] when the decision is [ReminderDecision.Fire], and always
 *    returns [MealReminderRun.Rearm] with the next-day trigger so the worker can
 *    re-arm the reminder for tomorrow (daily re-arm, ID-REARM).
 */
class MealReminderRunner(
    private val engine: ReminderScheduleEngine,
    private val loadConfig: suspend () -> ConfiguracionHorarios?,
    private val recentCheck: RecentRecordWindowCheck,
    private val showReminder: (TipoComida) -> Unit,
) {
    suspend fun run(tipo: TipoComida): MealReminderRun {
        val config = loadConfig() ?: return MealReminderRun.Skip
        if (!config.recordatorios_activos) return MealReminderRun.Skip
        val decision = engine.evaluateFor(tipo, config, recentCheck) ?: return MealReminderRun.Skip
        val nextTriggerAt = engine.nextTriggerAt(tipo, config)
        val fired = decision is ReminderDecision.Fire
        if (fired) showReminder(tipo)
        return MealReminderRun.Rearm(nextTriggerAt, fired)
    }
}

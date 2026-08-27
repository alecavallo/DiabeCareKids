package com.diabecarekids.app.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.diabecarekids.app.domain.ReminderDecision
import com.diabecarekids.app.domain.TipoComida

/**
 * Executes a scheduled meal reminder (design D6).
 *
 * On execution the worker re-loads the configuration via
 * [MealReminderDependencies] and re-runs the shared engine's
 * [com.diabecarekids.app.domain.ReminderScheduleEngine.evaluateFor] against the
 * CURRENT time. This execution-time re-check satisfies Post-Logging Suppression
 * — if the user has since logged the meal within the 2h window the decision is
 * [ReminderDecision.Suppressed] and no notification is shown. Only a
 * [ReminderDecision.Fire] produces a local notification.
 */
class MealScheduleReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tipo = inputData.getString(WorkManagerMealReminderScheduler.KEY_MEAL_TYPE)
            ?.let { name -> runCatching { TipoComida.valueOf(name) }.getOrNull() }
            ?: return Result.failure()

        val deps = MealReminderDependencies
        val config = deps.horariosStore().load()
        val decision = deps.engine().evaluateFor(tipo, config, deps.recentCheck())

        if (decision is ReminderDecision.Fire) {
            deps.notifier().showReminder(tipo)
        }
        // Suppressed / Missed / Schedule are intentionally no-ops at execution time.
        return Result.success()
    }
}

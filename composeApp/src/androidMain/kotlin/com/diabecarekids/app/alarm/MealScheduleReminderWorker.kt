package com.diabecarekids.app.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.reminder.MealReminderRun
import com.diabecarekids.app.reminder.MealReminderRunner

/**
 * Executes a scheduled meal reminder (design D6).
 *
 * On execution the worker re-loads the configuration via
 * [MealReminderDependencies] and re-runs the shared engine's
 * [com.diabecarekids.app.domain.ReminderScheduleEngine.evaluateFor] against the
 * CURRENT time. This execution-time re-check satisfies Post-Logging Suppression
 * — if the user has since logged the meal within the window the decision is
 * suppressed and no notification is shown. Only a Fire produces a local
 * notification.
 *
 * After handling the current occurrence it RE-ARMS the reminder for the next day
 * (ID-REARM), so reminders keep firing day-after-day without the app being
 * reopened. The decision logic lives in the platform-free [MealReminderRunner]
 * (testable in commonTest); this class only adapts the WorkManager seam to it.
 *
 * Graceful degradation (ID-WORKER-CRASH): WorkManager can start this process
 * directly (device reboot / process killed while backgrounded) with no Activity.
 * [Application.onCreate] ([DiabeCareApp]) populates [MealReminderDependencies]
 * before any worker runs; if it is ever still unpopulated the worker skips
 * (Result.success) instead of throwing and losing the reminder silently.
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
        // Graceful skip: never throw when the composition root never ran.
        if (!deps.isPopulated) return Result.success()

        val runner = MealReminderRunner(
            engine = deps.engine(),
            loadConfig = { deps.horariosStore().load() },
            recentCheck = deps.recentCheck(),
            showReminder = { deps.notifier().showReminder(it) },
        )

        when (val run = runner.run(tipo)) {
            MealReminderRun.Skip -> Unit
            is MealReminderRun.Rearm -> {
                // Daily re-arm (ID-REARM): schedule the next occurrence so the chain
                // continues without MainActivity / orchestrator.refresh().
                WorkManagerMealReminderScheduler(applicationContext).schedule(tipo, run.nextTriggerAt)
            }
        }
        return Result.success()
    }
}

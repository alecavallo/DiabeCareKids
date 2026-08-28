package com.diabecarekids.app.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.platform.MealReminderScheduler
import com.diabecarekids.app.platform.epochMillisNow
import java.util.concurrent.TimeUnit

/**
 * [MealReminderScheduler] backed by WorkManager (design D6, offline local
 * notification). Schedules a unique one-time [MealScheduleReminderWorker] for
 * each primary meal type with `enqueueUniqueWork` REPLACE so re-evaluations
 * (app start / settings change) idempotently replace the pending work.
 *
 * Cancellation uses a shared [TAG_MEAL_REMINDER] so the global
 * [MealReminderScheduler.cancelAll] path (triggered by the orchestrator when
 * reminders are disabled) clears every pending meal reminder at once.
 */
class WorkManagerMealReminderScheduler(
    private val context: Context,
) : MealReminderScheduler {

    override fun schedule(tipo: TipoComida, triggerAt: Long) {
        val delayMillis = (triggerAt - epochMillisNow()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<MealScheduleReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_MEAL_TYPE to tipo.name))
            .addTag(TAG_MEAL_REMINDER)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(tipo),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_MEAL_REMINDER)
    }

    private fun uniqueWorkName(tipo: TipoComida) = "${WORK_NAME_PREFIX}${tipo.name}"

    companion object {
        /** Input-data key carrying the [TipoComida] name to [MealScheduleReminderWorker]. */
        const val KEY_MEAL_TYPE = "meal_type"
        const val TAG_MEAL_REMINDER = "meal_reminder"
        const val WORK_NAME_PREFIX = "meal_reminder_"
    }
}

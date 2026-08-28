package com.diabecarekids.app.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.platform.MealReminderScheduler
import com.diabecarekids.app.platform.reminderDelayMillis
import com.diabecarekids.app.platform.wallClockAsUtcOffsetAtWallTimeMillis
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
        // triggerAt is on the wall-clock-as-UTC axis (ID-TZ-TIMELINE). WorkManager's
        // setInitialDelay is REAL ELAPSED time (System.currentTimeMillis axis), so the
        // delay must be the true-UTC span between now and the trigger, not the nominal
        // wall-clock span. We convert the trigger to its true-UTC epoch using the device
        // offset at the trigger's OWN wall time (DST-aware) — across a spring-forward /
        // fall-back transition that offset differs from the current one, and only the
        // trigger-time offset keeps the delay real-elapsed correct (ID-DST-DELAY).
        val delayMillis = reminderDelayMillis(
            triggerAt = triggerAt,
            triggerUtcOffsetMillis = wallClockAsUtcOffsetAtWallTimeMillis(triggerAt),
            realNowMillis = System.currentTimeMillis(),
        )
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

package com.diabecarekids.app.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.diabecarekids.app.platform.PostprandialAlarmScheduler
import java.util.concurrent.TimeUnit

/**
 * [PostprandialAlarmScheduler] backed by WorkManager.
 *
 * Schedules a unique one-time worker ~2h after a T0 save so the app can prompt
 * the T2 postprandial follow-up (MEAL-002). The reminder notification body is
 * out of scope for this slice — the worker occupies the schedule slot.
 */
class WorkManagerAlarmScheduler(
    private val context: Context,
) : PostprandialAlarmScheduler {

    override fun schedule(mealId: String) {
        val request = OneTimeWorkRequestBuilder<PostprandialReminderWorker>()
            .setInitialDelay(DELAY_HOURS, TimeUnit.HOURS)
            .setInputData(workDataOf(KEY_MEAL_ID to mealId))
            .addTag(TAG_POSTPRANDIAL)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(mealId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel(mealId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(mealId))
    }

    private fun uniqueWorkName(mealId: String) = "${WORK_NAME_PREFIX}$mealId"

    companion object {
        const val KEY_MEAL_ID = "meal_id"
        const val TAG_POSTPRANDIAL = "postprandial"
        const val DELAY_HOURS = 2L
        const val WORK_NAME_PREFIX = "postprandial_"
    }
}

/**
 * Executes the 2h follow-up reminder. Notification delivery is deferred to a
 * later change; the worker resolves successfully so the schedule slot is taken.
 */
class PostprandialReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // TODO(DM1-followup): deliver the T2 notification. The schedule slot is
        // reserved here so WorkManager owns the 2h timing.
        return Result.success()
    }
}

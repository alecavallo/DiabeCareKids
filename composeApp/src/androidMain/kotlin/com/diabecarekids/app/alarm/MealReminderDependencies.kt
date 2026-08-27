package com.diabecarekids.app.alarm

import com.diabecarekids.app.domain.ReminderScheduleEngine
import com.diabecarekids.app.persistence.HorariosStore
import com.diabecarekids.app.persistence.RecentRecordWindowCheck

/**
 * Process-singleton dependency holder giving [MealScheduleReminderWorker] access
 * to the shared stores/engine (design D7).
 *
 * A CoroutineWorker is constructed by WorkManager via reflection with only
 * (Context, WorkerParameters), so it cannot receive constructor-injected stores.
 * MainActivity populates this holder (composition root) before scheduling; the
 * worker re-loads the config and re-checks the 2h window against the SAME
 * in-memory stores the app uses — fresh stores would always be empty and would
 * wrongly over-fire (design D7 rationale).
 */
object MealReminderDependencies {

    private var horariosStore: HorariosStore? = null
    private var engine: ReminderScheduleEngine? = null
    private var recentCheck: RecentRecordWindowCheck? = null
    private var notifier: MealReminderNotifier? = null

    /** Called once from the composition root (MainActivity) before any worker runs. */
    fun populate(
        horariosStore: HorariosStore,
        engine: ReminderScheduleEngine,
        recentCheck: RecentRecordWindowCheck,
        notifier: MealReminderNotifier,
    ) {
        this.horariosStore = horariosStore
        this.engine = engine
        this.recentCheck = recentCheck
        this.notifier = notifier
    }

    val isPopulated: Boolean get() = horariosStore != null

    internal fun horariosStore(): HorariosStore =
        checkNotNull(horariosStore) { "MealReminderDependencies not populated — call populate() from the composition root" }

    internal fun engine(): ReminderScheduleEngine =
        checkNotNull(engine) { "MealReminderDependencies not populated — call populate() from the composition root" }

    internal fun recentCheck(): RecentRecordWindowCheck =
        checkNotNull(recentCheck) { "MealReminderDependencies not populated — call populate() from the composition root" }

    internal fun notifier(): MealReminderNotifier =
        checkNotNull(notifier) { "MealReminderDependencies not populated — call populate() from the composition root" }
}

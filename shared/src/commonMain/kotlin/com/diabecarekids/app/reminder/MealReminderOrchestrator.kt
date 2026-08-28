package com.diabecarekids.app.reminder

import com.diabecarekids.app.domain.ReminderDecision
import com.diabecarekids.app.domain.ReminderScheduleEngine
import com.diabecarekids.app.persistence.HorariosStore
import com.diabecarekids.app.persistence.RecentRecordWindowCheck
import com.diabecarekids.app.platform.MealReminderScheduler

/**
 * Ties the engine, stores, and scheduler together (design data flow).
 *
 * `refresh()` loads the configuration, evaluates the schedule, and maps the
 * decisions to scheduler calls:
 *  - [ReminderDecision.Disabled]  → [MealReminderScheduler.cancelAll]
 *  - [ReminderDecision.Schedule] / [ReminderDecision.Fire] → schedule at trigger
 *  - [ReminderDecision.Missed] → schedule the NEXT day's trigger (daily re-arm,
 *    ID-REARM — a meal already past at app-open still gets scheduled tomorrow)
 *  - [ReminderDecision.Suppressed] → no-op
 */
class MealReminderOrchestrator(
    private val engine: ReminderScheduleEngine,
    private val horariosStore: HorariosStore,
    private val recentCheck: RecentRecordWindowCheck,
    private val scheduler: MealReminderScheduler,
) {
    suspend fun refresh() {
        val config = horariosStore.load()
        val decisions = engine.evaluate(config, recentCheck)

        if (decisions.any { it is ReminderDecision.Disabled }) {
            scheduler.cancelAll()
            return
        }

        for (decision in decisions) {
            when (decision) {
                is ReminderDecision.Schedule -> scheduler.schedule(decision.mealType, decision.triggerAt)
                is ReminderDecision.Fire -> scheduler.schedule(decision.mealType, decision.triggerAt)
                is ReminderDecision.Missed ->
                    scheduler.schedule(decision.mealType, engine.nextTriggerAt(decision.mealType, config))
                is ReminderDecision.Suppressed -> Unit
                ReminderDecision.Disabled -> Unit
            }
        }
    }
}

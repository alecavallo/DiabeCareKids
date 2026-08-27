package com.diabecarekids.app.domain

/**
 * Outcome of evaluating one meal type (or the whole schedule) at a point in time.
 */
sealed interface ReminderDecision {
    /**
     * Now is before the trigger; schedule a future reminder for [triggerAt].
     */
    data class Schedule(val mealType: TipoComida, val triggerAt: Long) : ReminderDecision

    /**
     * Now is within the grace window and no recent record suppresses it;
     * the reminder should fire now.
     */
    data class Fire(val mealType: TipoComida, val triggerAt: Long) : ReminderDecision

    /** A recent record for [mealType] suppresses the reminder (Post-Logging Suppression). */
    data class Suppressed(val mealType: TipoComida) : ReminderDecision

    /** Now is past the fire grace window; the reminder is too late to fire. */
    data class Missed(val mealType: TipoComida) : ReminderDecision

    /** Global reminders are disabled; nothing should be scheduled. */
    data object Disabled : ReminderDecision
}

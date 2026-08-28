package com.diabecarekids.app

import android.app.Application
import com.diabecarekids.app.alarm.MealReminderDependencies
import com.diabecarekids.app.alarm.MealReminderNotifier
import com.diabecarekids.app.domain.LocalTimeOfDay
import com.diabecarekids.app.domain.ReminderScheduleEngine
import com.diabecarekids.app.persistence.InMemoryHorariosStore
import com.diabecarekids.app.persistence.InMemoryPersistenceStore
import com.diabecarekids.app.persistence.PersistenceRecentRecordWindowCheck
import com.diabecarekids.app.platform.localTimeAtDayOffsetMillis
import com.diabecarekids.app.platform.todayAtLocalTimeMillis
import com.diabecarekids.app.platform.wallClockAsUtcEpochNow

/**
 * Application entry point, declared in the manifest's `<application android:name>`.
 *
 * ## ID-WORKER-CRASH (critical)
 * WorkManager can start this process directly (device reboot / process killed
 * while backgrounded) with no Activity ever being created, so MainActivity's
 * composition-root wiring never runs. `Application.onCreate` is guaranteed to run
 * before ANY WorkManager worker, so we populate the process-singleton
 * [MealReminderDependencies] holder here with FRESH stores/engine/notifier. In a
 * fresh process the app's in-memory stores are empty anyway (this slice uses
 * in-memory persistence), so "fresh" is accurate. When the app runs normally,
 * [MainActivity] overwrites these with the app's live stores via
 * [MealReminderDependencies.populate] (populate is idempotent; the later call wins).
 *
 * Documented over-fire concern (accepted): a fresh process's recent-check sees an
 * empty record set, so a worker firing after a restart cannot observe a record
 * logged before the restart. With the daily re-arm (ID-REARM) + the execution-time
 * suppression re-check this is acceptable per ID-WORKER-CRASH.
 */
class DiabeCareApp : Application() {

    override fun onCreate() {
        super.onCreate()
        populateMealReminderDependencies()
    }

    private fun populateMealReminderDependencies() {
        val store = InMemoryPersistenceStore()
        val horariosStore = InMemoryHorariosStore()
        val recentCheck = PersistenceRecentRecordWindowCheck(store)
        val engine = ReminderScheduleEngine(
            // Wall-clock-as-UTC axis (ID-TZ-TIMELINE), matching stored records.
            now = { wallClockAsUtcEpochNow() },
            todayAt = { time: LocalTimeOfDay -> todayAtLocalTimeMillis(time.hour, time.minute) },
            dayAt = { time: LocalTimeOfDay, offset: Int ->
                localTimeAtDayOffsetMillis(time.hour, time.minute, offset)
            },
        )
        MealReminderDependencies.populate(
            horariosStore = horariosStore,
            engine = engine,
            recentCheck = recentCheck,
            notifier = MealReminderNotifier(applicationContext),
        )
    }
}

# Delta for Meal Reminders

## ADDED Requirements

### Requirement: Reminder Eligibility Gate
The system MUST respect the global reminder setting in `ConfiguracionHorarios`.

#### Scenario: Global reminders disabled
- GIVEN `ConfiguracionHorarios.recordatorios_activos` is false
- WHEN evaluating meal reminders
- THEN no reminders are generated or scheduled

#### Scenario: Global reminders enabled
- GIVEN `ConfiguracionHorarios.recordatorios_activos` is true
- WHEN evaluating meal reminders
- THEN reminders are generated according to the schedule

### Requirement: Trigger Time Computation
The engine MUST compute a reminder trigger time as the habitual meal time minus a configurable advance window (default 15 minutes).

#### Scenario: Morning breakfast reminder (DESAYUNO)
- GIVEN the habitual time for DESAYUNO is 08:00 and advance window is 15 minutes
- WHEN the trigger time is computed
- THEN the trigger time is 07:45

#### Scenario: Lunch reminder (ALMUERZO)
- GIVEN the habitual time for ALMUERZO is 12:30 and advance window is 15 minutes
- WHEN the trigger time is computed
- THEN the trigger time is 12:15

#### Scenario: Afternoon snack reminder (MERIENDA)
- GIVEN the habitual time for MERIENDA is 17:00 and advance window is 15 minutes
- WHEN the trigger time is computed
- THEN the trigger time is 16:45

#### Scenario: Dinner reminder (CENA)
- GIVEN the habitual time for CENA is 21:00 and advance window is 15 minutes
- WHEN the trigger time is computed
- THEN the trigger time is 20:45

### Requirement: Exclusion of Non-Primary Meals (INV-006)
The system MUST NOT schedule or generate reminders for COLACION or other non-primary meals.

#### Scenario: Colacion reminder attempt
- GIVEN a meal type of COLACION
- WHEN evaluating reminder eligibility
- THEN the engine MUST NOT schedule a reminder

### Requirement: Post-Logging Suppression
A scheduled reminder for a specific meal type MUST be suppressed if the user has logged a record for that meal type within the preceding 2 hours.

#### Scenario: Record logged within window
- GIVEN a reminder is scheduled for ALMUERZO at 12:15
- AND a record for ALMUERZO exists at 11:30 (within 2 hours)
- WHEN evaluating the reminder at 12:15
- THEN the reminder MUST be suppressed

#### Scenario: No recent record logged
- GIVEN a reminder is scheduled for ALMUERZO at 12:15
- AND the last record for ALMUERZO was at 08:30 (outside 2 hours)
- WHEN evaluating the reminder at 12:15
- THEN the reminder MUST fire

### Requirement: Offline Local Notification Scheduling
The `MealReminderScheduler` MUST schedule offline background tasks that trigger local notifications, using dependency injection for time and storage to allow offline testing.

#### Scenario: Scheduler triggers local worker
- GIVEN the engine determines a reminder should fire
- WHEN `MealReminderScheduler` processes this decision
- THEN it MUST schedule a `MealScheduleReminderWorker` for local notification
- AND it MUST NOT rely on FCM/push or a live Firestore connection
# Proposal: Proactive Scheduled Meal Reminders (CAP-006)

## Intent

Provide proactive, reliable meal reminders for the 4 primary structured meals (DESAYUNO, ALMUERZO, MERIENDA, CENA) based on family schedule settings (habitual time minus 15 min advance window), avoiding redundant prompts if a meal was already logged within the last 2 hours, and strictly excluding COLACION (INV-006).

## Scope

### In Scope
- `ConfiguracionHorarios` domain model and `HorariosStore` (default seed settings: DESAYUNO 08:00, ALMUERZO 12:30, MERIENDA 17:00, CENA 21:00; 15-minute advance window; active toggle).
- Pure, clock-injected `ReminderScheduleEngine` in `shared/commonMain` computing reminder eligibility, trigger timestamps, 2h log silence check, and COLACION exclusion.
- `MealReminderScheduler` platform interface in `commonMain` and Android `WorkManager` scheduler + `MealScheduleReminderWorker` delivering local notifications.
- Comprehensive unit tests covering schedule calculation, 2h suppression window, and invariant enforcement.

### Out of Scope
- Remote push notifications via Firebase Cloud Messaging (local notifications only).
- Remote Firestore persistence (in-memory / default seed store).
- Complex timezone migration rules or multi-profile family synchronization.
- Settings configuration UI (deferred to subsequent slice; uses default seed with store interface).
- Scheduling reminders for COLACION (strictly forbidden per INV-006).

## Capabilities

### New Capabilities
- `meal-reminders`: Proactive scheduled meal reminders based on family schedule settings, advance window, 2h log silence suppression, and local notifications.

### Modified Capabilities
None.

## Approach

Implement a decoupled pure engine (Option 2 from exploration):
1. **Domain Engine (`shared/commonMain`)**: `ReminderScheduleEngine` evaluates `ConfiguracionHorarios`, current timestamp via an injected `Clock`, and recent `RegistroComida` records to compute pending trigger decisions. If a meal of that type was logged in the preceding 2 hours, the reminder is suppressed.
2. **Settings Model & Store (`shared/commonMain`)**: `ConfiguracionHorarios` data model + `HorariosStore` abstraction (with `InMemoryHorariosStore` default seed).
3. **Platform Execution (`composeApp/androidMain`)**: `MealReminderScheduler` interface with Android `WorkManager` worker enqueuing `MealScheduleReminderWorker` to trigger `NotificationManager` local notifications.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `shared/src/commonMain/.../domain/` | New | `ConfiguracionHorarios`, `ReminderScheduleEngine`, `MealReminder` |
| `shared/src/commonMain/.../persistence/` | New | `HorariosStore`, `InMemoryHorariosStore` |
| `shared/src/commonMain/.../platform/` | New | `MealReminderScheduler` interface |
| `composeApp/src/androidMain/.../alarm/` | New/Modified | `MealScheduleReminderWorker`, `WorkManagerMealReminderScheduler` |
| `shared/src/commonTest/...` | New | Unit tests for schedule math, 2h silence, and INV-006 |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Device clock drift or timezone changes | Low | Inject `Clock` abstraction; compute delays relative to epoch millis |
| WorkManager delays from OS battery optimization | Med | Use standard unique periodic/one-time WorkManager requests with reasonable tolerance |
| Data consistency for 2h check | Low | Query `PersistenceStore` directly by meal type and timestamp range |

## Rollback Plan

Revert added classes in `shared` and `composeApp`. Cancel active WorkManager unique work tags for scheduled meal reminders.

## Dependencies

- Existing `RegistroComida` and `PersistenceStore` (from `meal-logging`).
- AndroidX `WorkManager` and Android `NotificationManager`.

## Success Criteria

- [ ] Engine calculates accurate trigger times (`habitual_time - ventana_anticipacion_minutos`) for DESAYUNO, ALMUERZO, MERIENDA, CENA.
- [ ] Reminders for COLACION are strictly rejected and never scheduled (INV-006).
- [ ] Reminder is suppressed if a matching meal was logged within the preceding 2 hours.
- [ ] WorkManager worker triggers local notification when criteria are met.
- [ ] Unit tests pass offline in CI with 100% coverage on engine decisions.

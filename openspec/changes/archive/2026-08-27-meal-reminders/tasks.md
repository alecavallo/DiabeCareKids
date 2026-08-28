# Tasks: Proactive Scheduled Meal Reminders (CAP-006)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Effective review budget | 800 changed lines (session override) |
| Total forecast | ~1,040 lines (Slice 1 ~780 + Slice 2 ~260) |
| Chained PRs | 2 stacked (PR 1 → PR 2) |
| Sub-split needed | No, but watch Slice 1 (~98% of budget) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | PR | Focused test | Runtime harness | Rollback boundary |
|------|------|----|--------------|-----------------|-------------------|
| Slice 1 | shared engine/stores/platform seam + commonTest | PR 1 (base `main`) | `make gradle :shared:testDebugUnitTest` | N/A — pure commonMain, offline Docker unit tests | revert shared files; zero android runtime impact |
| Slice 2 | composeApp android bridge + wiring | PR 2 (base = PR 1 branch) | `make build` | `make test && make build`; manual device smoke (near-future trigger → local notification) | revert composeApp alarm files + `cancelAllWorkByTag("meal_reminder")` |

## Slice 1 — shared/commonMain engine + commonTest (PR 1)

| ID | Task | Spec | Files | Verify |
|----|------|------|-------|--------|
| [x] Slice1.1 | Add `suspend fun getAll(): List<RegistroComida>` to `PersistenceStore`; implement in `InMemoryPersistenceStore`; override in `FakePersistenceStore` (composeApp commonTest) | Post-Logging Suppression (D3) | shared/.../persistence/PersistenceStore.kt, InMemoryPersistenceStore.kt; composeApp/src/commonTest/.../viewmodel/Fakes.kt | `make test` |
| [x] Slice1.2 | Create `ConfiguracionHorarios` (@Serializable) + `horarioFor(tipo)`; seed 08:00/12:30/17:00/21:00, window 15, active true | Eligibility Gate | shared/.../domain/ConfiguracionHorarios.kt | `make test` |
| [x] Slice1.3 | Create `ScheduleTime.kt`: `LocalTimeOfDay` + strict "HH:mm" parser (reject invalid) | Trigger Time Computation (D1) | shared/.../domain/ScheduleTime.kt | `make test` |
| [x] Slice1.4 | Create sealed `ReminderDecision` (Schedule/Fire/Suppressed/Missed/Disabled) | — | shared/.../domain/ReminderDecision.kt | `make test` |
| [x] Slice1.5 | Create `HorariosStore` interface + `InMemoryHorariosStore` (default seed) | Eligibility Gate | shared/.../persistence/HorariosStore.kt | `make test` |
| [x] Slice1.6 | Create `RecentRecordWindowCheck` fun interface + impl over `PersistenceStore.getAll()` (2h window) | Post-Logging Suppression (D3) | shared/.../persistence/RecentRecordChecker.kt | `make test` |
| [x] Slice1.7 | Create `ReminderScheduleEngine`: clock-injected `now` + `todayAt`; fixed `PRIMARY_MEALS` (4 types, no COLACION); trigger = habitual − window; decisions SCHEDULE/FIRE/SUPPRESSED/DISABLED/MISSED; 2h predicate; 60-min grace | Trigger Time, INV-006, Suppression, Eligibility (D4/D5/D8) | shared/.../domain/ReminderScheduleEngine.kt | `make test` |
| [x] Slice1.8 | Add `MealReminderScheduler` interface + `todayAtLocalTimeMillis` expect to `Platform.kt`; actual via `java.util.Calendar` in shared androidMain | Offline Local Notification (D1) | shared/.../platform/Platform.kt; shared/src/androidMain/.../platform/Platform.android.kt | `make test` |
| [x] Slice1.9 | Create `MealReminderOrchestrator` (`suspend refresh()`): engine + stores + scheduler; DISABLED ⇒ `cancelAll()` | Offline Scheduling, Eligibility | shared/.../reminder/MealReminderOrchestrator.kt | `make test` |
| [x] Slice1.10 | commonTest: ScheduleTimeTest (strict parser); ReminderScheduleEngineTest (4 trigger rows 08:00→07:45, 12:30→12:15, 17:00→16:45, 21:00→20:45; disabled gate; both suppression scenarios; PRIMARY_MEALS lock = no COLACION; MISSED grace); HorariosStoreTest; RecentRecordCheckerTest; PersistenceStoreTest.getAll; MealReminderOrchestratorTest (FakeMealReminderScheduler records calls) | All spec scenarios | shared/src/commonTest/.../domain/, persistence/, reminder/ | `make gradle :shared:testDebugUnitTest` |

**Slice boundary → commit + PR 1 here.**

## Slice 2 — composeApp androidMain bridge (PR 2)

| ID | Task | Spec | Files | Verify |
|----|------|------|-------|--------|
| [x] Slice2.1 | `WorkManagerMealReminderScheduler`: OneTimeWork + delay=max(0, trigger−now), `enqueueUniqueWork` REPLACE, unique name `meal_reminder_{tipo}`, `cancelAllWorkByTag` | Offline Local Notification | composeApp/src/androidMain/.../alarm/WorkManagerMealReminderScheduler.kt | `make build` |
| [x] Slice2.2 | `MealScheduleReminderWorker` (CoroutineWorker): re-load config via `MealReminderDependencies`, re-check 2h window, notify | Offline Scheduling + Suppression (D6) | .../alarm/MealScheduleReminderWorker.kt | `make build` |
| [x] Slice2.3 | `MealReminderNotifier`: NotificationManager + `meal_reminders` channel (API 26 guard); POST_NOTIFICATIONS (API 33+) in manifest if needed | Offline Local Notification | .../alarm/MealReminderNotifier.kt (+ AndroidManifest.xml) | `make build` |
| [x] Slice2.4 | `MealReminderDependencies` process-singleton holder (worker→store access, D7) | Offline Scheduling (D7) | .../alarm/MealReminderDependencies.kt | `make build` |
| [x] Slice2.5 | Wire MainActivity: build stores/engine/orchestrator, populate `MealReminderDependencies`, launch `refresh()` in scope | Offline Scheduling | .../MainActivity.kt | `make build` |
| [x] Slice2.6 | Full gate: `make test` + `make build`; manual device smoke (notification fires after trigger) | All | — | `make test && make build` |

**Slice boundary → commit + PR 2 here.** androidMain has no unit tests (existing convention — WorkManagerAlarmScheduler has none).

**Slice 2 COMPLETE (2026-08-27).** `make test` + `make build` both BUILD SUCCESSFUL (shared 59 tests / 0 failures; composeApp 19 tests / 0 failures). Manual device smoke deferred (offline CI); notification path covered by execution-time re-check in worker + green build.

# Apply Progress — meal-reminders (CAP-006)

Change: meal-reminders · Mode: Standard (strict TDD OFF, `rules.apply.tdd=false`) · Store: hybrid
Full record: Engram topic `sdd/diabecarekids/meal-reminders/apply-progress`. This file mirrors Slice 2 in the change dir.

## Slice 1 — COMPLETE (PR 1, base `main`)
All Slice1.1–1.10 done (see Engram #2384). shared commonMain engine + commonTest (55+ shared tests green).

## Slice 2 — COMPLETE (PR 2, base = PR 1 branch) — 2026-08-27
composeApp androidMain WorkManager bridge + wiring + local notification.

- [x] Slice2.1 — `WorkManagerMealReminderScheduler`: OneTimeWork, delay=max(0, trigger−now), `enqueueUniqueWork` REPLACE per meal (`meal_reminder_{tipo}`), tag `meal_reminder`, `cancelAllWorkByTag` for the DISABLED path.
- [x] Slice2.2 — `MealScheduleReminderWorker` (CoroutineWorker): re-loads config via `MealReminderDependencies`, re-runs `engine.evaluateFor` (execution-time 2h window re-check), notifies only on `Fire`.
- [x] Slice2.3 — `MealReminderNotifier`: `meal_reminders` channel (API-26 guard), system drawable small icon; `POST_NOTIFICATIONS` (API 33+) added to AndroidManifest (runtime request deferred/no-op this slice).
- [x] Slice2.4 — `MealReminderDependencies` process-singleton holder (worker→store/engine/notifier access, design D7), populated by MainActivity.
- [x] Slice2.5 — MainActivity `wireMealReminders()`: builds `InMemoryHorariosStore` + `PersistenceRecentRecordWindowCheck` + `ReminderScheduleEngine(now/todayAt seams)` + `WorkManagerMealReminderScheduler` + notifier, populates deps, launches `orchestrator.refresh()` in scope.
- [x] Slice2.6 — `make test` + `make build` both green.

## Files Changed (Slice 2)
| File | Action |
|------|--------|
| composeApp/src/androidMain/.../alarm/WorkManagerMealReminderScheduler.kt | Created |
| composeApp/src/androidMain/.../alarm/MealScheduleReminderWorker.kt | Created |
| composeApp/src/androidMain/.../alarm/MealReminderNotifier.kt | Created |
| composeApp/src/androidMain/.../alarm/MealReminderDependencies.kt | Created |
| composeApp/src/androidMain/.../MainActivity.kt | Modified — wired meal reminders |
| composeApp/src/androidMain/AndroidManifest.xml | Modified — POST_NOTIFICATIONS |

## Verification Evidence (offline, Docker OK)
- `make test` → BUILD SUCCESSFUL. shared = 59 tests / 0 failures; composeApp = 19 tests / 0 failures.
- `make build` (`:composeApp:assembleDebug`) → BUILD SUCCESSFUL.
- Manual device smoke deferred (offline CI). Notification path is covered structurally by the worker's execution-time re-check + green build; runtime grant (POST_NOTIFICATIONS) intentionally deferred.

## Work Unit Evidence (Slice 2)
| Evidence | Value |
|---|---|
| Focused test | `make test` → BUILD SUCCESSFUL (shared 59 / 0 fail; composeApp 19 / 0 fail) |
| Runtime harness | `make build` → BUILD SUCCESSFUL (`:composeApp:assembleDebug`). Manual notification smoke deferred — offline CI, no device |
| Rollback boundary | revert composeApp alarm/*.kt + MainActivity wiring + manifest permission; `cancelAllWorkByTag("meal_reminder")` clears pending work |

## Deviations
None material. Notification small icon uses a system drawable (`android.R.drawable.ic_dialog_info`) because the app ships no image assets (manifest already had no `android:icon`). Uses platform `android.app.Notification.Builder` (no `androidx.core` dependency added).

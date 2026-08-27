# Design: Proactive Scheduled Meal Reminders (CAP-006)

## Technical Approach

Pure clock-injected `ReminderScheduleEngine` in `shared/commonMain` computes per-meal reminder decisions; `composeApp/androidMain` bridges them to WorkManager + NotificationManager. Follows existing seams (`PostprandialAlarmScheduler` in `platform/Platform.kt`, `epochMillisNow` expect/actual, manual DI in MainActivity). **No settings UI this change** — engine consumes the seeded default `ConfiguracionHorarios` from `InMemoryHorariosStore` (UI explicitly deferred per proposal).

## Architecture Decisions

| # | Decision | Choice | Alternatives | Rationale |
|---|---|---|---|---|
| D1 | "HH:mm" → epoch | Pure common `HhMm` parser → `LocalTimeOfDay`; new expect/actual `todayAtLocalTimeMillis(hour, minute)` | kotlinx-datetime dependency | Mirrors `epochMillisNow` seam; zero new deps; timezone/DST resolved by platform truth; math unit-tested via injected lambda |
| D2 | Clock | Engine takes `now: () -> Long` + `todayAt: (LocalTimeOfDay) -> Long`; prod = existing seams | kotlinx-datetime `Clock.System` | Deterministic tests, no flakiness, offline CI |
| D3 | 2h record query | Add `PersistenceStore.getAll()` + dedicated `RecentRecordWindowCheck` fun interface | separate meal-record store | One in-memory source of truth; swappable for Firestore later |
| D4 | Suppression reference | Window `[trigger−2h, trigger]`; worker re-checks `[now−2h, now]` | window relative to schedule-time now | Matches spec scenarios (12:15 eval, 11:30 record); identical when now≈trigger |
| D5 | INV-006 | Engine iterates fixed `PRIMARY_MEALS` (4 types); `evaluateFor` returns null otherwise | add COLACION to enum | Enum has no COLACION → invariant is structural; test locks the list |
| D6 | Worker job | One worker re-runs engine, then notifies via `MealReminderNotifier` (channel `meal_reminders`, API-26 guard) | separate workers; AlarmManager | WorkManager owns Doze-tolerant timing; execution-time re-check satisfies suppression spec |
| D7 | Worker→store access | `MealReminderDependencies` object in androidMain, populated by MainActivity | Hilt/Koin; worker-owned fresh stores | Manual DI is repo convention; fresh stores would always be empty → wrong suppression |
| D8 | Missed window | FIRE only when now ∈ `[trigger, trigger+60min]`; else MISSED | no grace (stale all-day reminders) | Prevents the 08:00 reminder firing at 20:00 |

## Data Flow (sequence)

```
MainActivity ──refresh()──▶ MealReminderOrchestrator
                                │ engine.evaluate(config, now, todayAt, recentCheck)
                                ▼
                decisions: SCHEDULE | FIRE | SUPPRESSED | MISSED | DISABLED
                                │ (disabled ⇒ scheduler.cancelAll())
              SCHEDULE/FIRE ────┴──▶ MealReminderScheduler.schedule(tipo, triggerAt)
                                          │ OneTimeWork + delay=max(0, trigger−now) + enqueueUniqueWork REPLACE
                                          ▼
                                  MealScheduleReminderWorker.doWork()
                                          │ re-load config, re-check 2h window
                                          ▼
                                  MealReminderNotifier.showReminder() ─▶ NotificationManager
```

## File Changes

| File | Action | Description |
|---|---|---|
| shared/.../domain/ConfiguracionHorarios.kt | Create | @Serializable config; seed 08:00/12:30/17:00/21:00 + 15 + true; `horarioFor(tipo)` |
| shared/.../domain/ScheduleTime.kt | Create | `LocalTimeOfDay` + strict "HH:mm" parser |
| shared/.../domain/ReminderDecision.kt | Create | sealed decision model |
| shared/.../domain/ReminderScheduleEngine.kt | Create | pure engine; `PRIMARY_MEALS`; trigger = habitual − window |
| shared/.../persistence/HorariosStore.kt | Create | interface + `InMemoryHorariosStore` |
| shared/.../persistence/RecentRecordChecker.kt | Create | fun interface + impl over `PersistenceStore` |
| shared/.../persistence/PersistenceStore.kt + InMemory | Modify | add `suspend fun getAll()` |
| shared/.../platform/Platform.kt (+androidMain actual) | Modify | `MealReminderScheduler` interface + `todayAtLocalTimeMillis` expect/actual (java.util.Calendar) |
| shared/.../reminder/MealReminderOrchestrator.kt | Create | ties engine/scheduler/stores; `suspend fun refresh()` |
| composeApp/.../alarm/WorkManagerMealReminderScheduler.kt | Create | unique work per meal (`meal_reminder_{tipo}`), REPLACE, `cancelAllWorkByTag` |
| composeApp/.../alarm/MealScheduleReminderWorker.kt | Create | CoroutineWorker: re-check + notify |
| composeApp/.../alarm/MealReminderNotifier.kt | Create | NotificationManager + channel (API 26 guard) |
| composeApp/.../alarm/MealReminderDependencies.kt | Create | process-singleton holder for worker access |
| composeApp/.../MainActivity.kt | Modify | wire stores/engine/orchestrator; launch `refresh()` |
| shared/src/commonTest + composeApp commonTest | Create/Modify | engine, parser, checker, store, orchestrator tests; extend Fakes |

## Interfaces / Contracts

```kotlin
@Serializable
data class ConfiguracionHorarios(
    val horario_desayuno: String, val horario_almuerzo: String,
    val horario_merienda: String, val horario_cena: String,
    val ventana_anticipacion_minutos: Int = 15,
    val recordatorios_activos: Boolean = true,
)

sealed interface ReminderDecision {
    data class Schedule(val mealType: TipoComida, val triggerAt: Long) : ReminderDecision
    data class Fire(val mealType: TipoComida, val triggerAt: Long) : ReminderDecision
    data class Suppressed(val mealType: TipoComida) : ReminderDecision
    data class Missed(val mealType: TipoComida) : ReminderDecision
    data object Disabled : ReminderDecision
}

interface MealReminderScheduler { fun schedule(t: TipoComida, triggerAt: Long); fun cancelAll() }
interface HorariosStore { suspend fun load(): ConfiguracionHorarios; suspend fun save(c: ConfiguracionHorarios) }
fun interface RecentRecordWindowCheck { suspend fun hasRecent(t: TipoComida, from: Long, until: Long): Boolean }
expect fun todayAtLocalTimeMillis(hour: Int, minute: Int): Long
```

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (commonTest) | 4 trigger rows (08:00→07:45, 12:30→12:15, 17:00→16:45, 21:00→20:45); disabled gate; both suppression scenarios; PRIMARY_MEALS lock (INV-006); parser strictness; orchestrator maps decisions→scheduler calls | kotlin.test + runTest + injected lambdas/fakes |
| Platform | WorkManager enqueue/cancel, worker re-check, notification | Manual/instrumented later — androidMain untested today (existing convention: WorkManagerAlarmScheduler has no tests) |

## Risks / Tradeoffs

| Risk | Mitigation |
|---|---|
| Timezone/DST flakiness | D1/D2 — injected `todayAt`; tests use fixed lambdas, zero clock dependence |
| WorkManager battery delays | One-time + REPLACE idempotent; worker re-checks state at execution, not schedule time |
| 2h check consistency | In-memory stores reset on process death → worker re-check sees empty store → may over-fire. Accepted for this slice; fixed when Firestore store + time-indexed query lands |
| Notification channel | Simple `meal_reminders` channel (default importance), API-26+ guard; polish deferred |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. WorkManager/NotificationManager are in-process platform APIs, not shelled processes.

## Migration / Rollout

No migration. Rollback: revert classes + `cancelAllWorkByTag("meal_reminder")`. Kill switch exists via `recordatorios_activos = false` (orchestrator then calls `cancelAll()`).

## Slice Split

| Area | Prod | Tests |
|---|---|---|
| shared commonMain (domain, persistence, platform, orchestrator) | ~400 | — |
| shared commonTest | — | ~360 |
| composeApp androidMain (+ MainActivity, Fakes) | ~230 | ~30 |
| **Total** | **~630** | **~390** |

Forecast ~1,000 authored lines exceeds the 800-line session review budget → **split into 2 chained PRs**: PR#1 = shared engine/stores/orchestrator + commonTest (~770 lines, autonomously verified, zero platform risk); PR#2 = android bridge (scheduler/worker/notifier/wiring, ~260 lines). Single PR only if a review-budget exception is granted.

## Open Questions

- Grace window fixed at 60 min constant vs configurable — team taste; constant is tunable.
- `PRIMARY_MEALS` lock test is the enforceable INV-006 guard today (COLACION is unrepresentable in `TipoComida` by design).

# Design: Advanced View and Historical Record Management (CAP-005)

## Technical Approach

New `advanced-history` capability layered on the existing meal-logging baseline. No domain-model changes: `RegistroComida` already has `es_registro_historico`, nullable photo URLs, and epoch-millis timestamps. The only shared-module change is `PersistenceStore.getAll()`. Three ViewModel+Screen pairs (`HistoryScreen`, `AddPastRecordScreen`, `EditRecordScreen`) wire into the existing sealed-`Route` manual navigation. Sorting, recalculation, and validation live in ViewModels — unit-testable without Compose.

## Architecture Decisions

### Decision: Store stays dumb; sorting in ViewModel
| Option | Tradeoff | Decision |
|---|---|---|
| Sort inside `getAll()` | Store encodes display policy | ❌ |
| Sort in `HistoryViewModel` (`sortedByDescending { fecha_hora_inicio }`) | Testable, store reusable | ✅ |

### Decision: Naming follows launch scope
`History`/`AddPastRecord`/`EditRecord` (routes) and `AddPastRecordViewModel` replace proposal's `HistoryTimeline`/`PastRecordFormViewModel`.

### Decision: Epoch millis, no kotlinx-datetime
| Option | Tradeoff | Decision |
|---|---|---|
| kotlinx-datetime | True tz math; new dependency outside stack | ❌ |
| `Long` epoch millis + Material3 DatePicker/TimePicker | Wall-clock-as-UTC; consistent sorting, zero deps | ✅ |

Picker selection stays UI-side; the ViewModel only sees the resulting `Long`.

### Decision: Edit flips `fuente_carbohidratos` to `MANUAL` when carbs change
Edited estimates are no longer USDA/Gemini data — provenance stays honest (one-line copy).

### Decision: Refresh via per-navigation ViewModel creation
`App.kt` builds `graph.historyViewModel()` inside `remember` on the History branch (FollowUpViewModel pattern). Leaving the branch disposes it; returning always reloads the store.

## Data Flow

    HistoryScreen → HistoryViewModel → store.getAll() → sorted desc
        │ tap item                            │ FAB
        ▼                                     ▼
    EditRecordScreen → EditRecordViewModel → store.update()
    AddPastRecordScreen → AddPastRecordViewModel → store.save()
                                     │ optional lookup
                                     ▼
                            NutritionRepository (manual always allowed)

Save flow: validate → `RegistroComida(id=Uuid, fecha_hora_inicio=pickerMillis, foto_*=null, es_registro_historico=true, carbohidratos_reales=calcularCarbohidratosReales(est,%), ultima_modificacion=epochMillisNow())` → `store.save` → `saved` signal → History. Edit flow: recalc reals on any carb/% change → `store.update(copy(...))`.

## File Changes

| File | Action | Description |
|---|---|---|
| `shared/.../persistence/PersistenceStore.kt` | Modify | + `suspend fun getAll(): List<RegistroComida>` |
| `shared/.../persistence/InMemoryPersistenceStore.kt` | Modify | mutex-guarded `records.values.toList()` |
| `shared/.../commonTest/.../PersistenceStoreTest.kt` | Modify | getAll tests |
| `composeApp/.../commonTest/.../Fakes.kt` | Modify | FakePersistenceStore implements `getAll()` |
| `composeApp/.../viewmodel/{History,AddPastRecord,EditRecord}ViewModel.kt` | Create | Timeline load+sort; historical form+save; edit+recalc+update |
| `composeApp/.../ui/{History,AddPastRecord,EditRecord}Screen.kt` | Create | Timeline list+FAB; form+pickers; edit form |
| `composeApp/.../navigation/Route.kt` | Modify | + `History`, `AddPastRecord`, `EditRecord(registro)` |
| `composeApp/.../navigation/AppGraph.kt` | Modify | + factory methods for 3 ViewModels |
| `composeApp/.../App.kt` | Modify | + 3 `when` branches (back = History/T0) |
| `composeApp/.../ui/MealFormScreen.kt` | Modify | TopAppBar action "Historial" |
| `composeApp/.../commonTest/.../viewmodel/` | Create | 3 ViewModel test suites |

## Interfaces / Contracts

```kotlin
// PersistenceStore — new method (insertion order irrelevant; callers sort)
suspend fun getAll(): List<RegistroComida>

// AddPastRecordState (core fields)
data class AddPastRecordState(
    val dateTimeEpochMillis: Long = epochMillisNow(),
    val mealType: TipoComida = TipoComida.ALMUERZO,
    val foodQuery: String = "",
    val carbInput: String = "",
    val source: CarbSource? = null,
    val sourceLabel: String? = null,
    val bgInitial: String = "",
    val consumedPercent: Int = 100,
    val isSaving: Boolean = false,
    val error: String? = null,
)
```

EditRecordState mirrors `FollowUpState`: `registro`, `carbInput`, `consumedPercent`, `bgPost2h`, `realCarbsPreview`, `isSaving`, `error`. Validation copies existing patterns (non-empty name, carbs/bg `toDoubleOrNull() >= 0`); edit keeps 2h-BG unchanged when blank. Recalc only via `calcularCarbohidratosReales` — never reimplemented.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (shared) | `getAll` empty/many/isolation | `PersistenceStoreTest`, `runTest` |
| Unit (composeApp) | Descending sort; save sets `es_registro_historico=true` + null photos + reals; edit recalc + `update`; manual-only save; validation errors | `runTest` + Fakes pattern — no Compose, no picker |
| UI | Rendering/back-nav | Manual verification only (no UI test infra) |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. In-app sealed-route navigation + in-memory persistence; NutritionRepository HTTP is pre-existing and unchanged.

## Migration / Rollout

No migration — additive; schema and store stay backwards-compatible. Rollback = revert commits.

## Delivery Forecast

Proposal estimates ~710 lines. Per-module: shared store+tests ~60, ViewModels ~250, composeApp tests ~180, UI screens ~280, navigation wiring ~60. Single design artifact; no design-level split. Review budget (400 lines) ⇒ tasks emit 2 chained PR slices: PR1 foundation+ViewModels+tests (~350), PR2 UI screens+navigation (~360). `auto-chain` strategy resolves this — no decision needed before apply.

## Open Questions

- [ ] Reject future-dated "past" records at save? Spec silent; currently allowed.
- [ ] Accept wall-clock-as-UTC convention vs adding kotlinx-datetime later?

# Tasks: Advanced View & Historical Record Management (CAP-005)

Package root `com.diabecarekids.app`. Budget **800 lines** (session override; default 400). Delivery `auto-chain` → 2 stacked PRs. Threat matrix: **N/A** (no routing/shell/process boundary).

## Review Workload Forecast

- **Slice 1 (PR 1)**: ~490 lines — shared store+tests ~60, ViewModels ~250, VM tests ~180.
- **Slice 2 (PR 2)**: ~340 lines — screens ~280, nav/DI ~60.
- Each slice under 800; no sub-split. Slice 1 (~490) exceeds the 400 default — the reason for the split.

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: Low

## Suggested Work Units

| Unit | Goal | PR | Focused test | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | `getAll()` + 3 ViewModels + VM tests | PR 1 | `make test` | `make test` (shared+compose unit) | Revert shared store + VMs + tests |
| 2 | 3 screens + routes + DI + entry point | PR 2 | `make test` | `make build` (`:composeApp:assembleDebug`) | Revert composeApp ui/nav/App |

## Slice 1 — Foundation + ViewModels + tests (PR 1)

| id | Task | Spec | Files | Verify |
|---|---|---|---|---|
| S1.1 | Add `suspend fun getAll(): List<RegistroComida>` to `PersistenceStore` | R2 | shared/.../persistence/PersistenceStore.kt | make test |
| S1.2 | Implement `getAll()` = mutex-guarded `records.values.toList()` | R2 | shared/.../persistence/InMemoryPersistenceStore.kt | make test |
| S1.3 | Test `getAll` empty/many/isolation (`runTest`) | R2 | shared/src/commonTest/.../persistence/PersistenceStoreTest.kt | make test |
| S1.4 | Implement `getAll()` on `FakePersistenceStore` | R2 | composeApp/src/commonTest/.../viewmodel/Fakes.kt | make test |
| S1.5 | `HistoryViewModel`: load `store.getAll()`, sort `sortedByDescending { fecha_hora_inicio }` | R2 | composeApp/.../viewmodel/HistoryViewModel.kt | make test |
| S1.6 | `AddPastRecordViewModel`: field state + optional `resolveCarbs`/manual carb; `save()` builds RegistroComida (`es_registro_historico=true`, null photos, `carbohidratos_reales=calcularCarbohidratosReales`, `creado_por_usuario_id="local"`, `ultima_modificacion=epochMillisNow()`, `id=Uuid`) | R1, R4 | composeApp/.../viewmodel/AddPastRecordViewModel.kt | make test |
| S1.7 | `EditRecordViewModel`: edit carb/%/2h BG → recalc via `calcularCarbohidratosReales`, flip `fuente_carbohidratos`→MANUAL on carb change, `store.update()` | R3 | composeApp/.../viewmodel/EditRecordViewModel.kt | make test |
| S1.8 | `HistoryViewModelTest`: descending sort, empty, refresh | R2 | composeApp/src/commonTest/.../viewmodel/HistoryViewModelTest.kt | make test |
| S1.9 | `AddPastRecordViewModelTest`: save flags + null photos + reals; manual-only save; validation errors | R1, R4 | composeApp/src/commonTest/.../viewmodel/AddPastRecordViewModelTest.kt | make test |
| S1.10 | `EditRecordViewModelTest`: %50→25g recalc; carb edit flips MANUAL; blank 2h BG unchanged; update persists | R3 | composeApp/src/commonTest/.../viewmodel/EditRecordViewModelTest.kt | make test |

## Slice 2 — Screens + navigation/DI (PR 2)

| id | Task | Spec | Files | Verify |
|---|---|---|---|---|
| S2.1 | `HistoryScreen`: LazyColumn timeline (desc), item tap→Edit, FAB→Add | R2 | composeApp/.../ui/HistoryScreen.kt | make test, make build |
| S2.2 | `AddPastRecordScreen`: Date/Time pickers→epoch millis, meal type, BG, lookup/manual carb, consumed %; save→History | R1, R4 | composeApp/.../ui/AddPastRecordScreen.kt | make test, make build |
| S2.3 | `EditRecordScreen`: carbs/consumed %/2h BG + live reals preview (FollowUpScreen pattern) | R3 | composeApp/.../ui/EditRecordScreen.kt | make test, make build |
| S2.4 | Add routes `History`, `AddPastRecord`, `EditRecord(registro)` | R1–R3 | composeApp/.../navigation/Route.kt | make test, make build |
| S2.5 | AppGraph factories `historyViewModel()` / `addPastRecordViewModel()` / `editRecordViewModel(registro)` | R1–R3 | composeApp/.../navigation/AppGraph.kt | make test, make build |
| S2.6 | Wire 3 `when` branches in `App.kt` (History VM `remember`-scoped; back T0/History) | R1–R3 | composeApp/.../App.kt | make test, make build |
| S2.7 | `MealFormScreen` TopAppBar action "Historial" → History | R2 | composeApp/.../ui/MealFormScreen.kt | make test, make build |

> ⚠ **Verified gap**: `getAll()` (S1.1–S1.4) does NOT yet exist on `feat/advanced-history` (merge-base with `feat/meal-reminders` = main `27f133f`; `getAll()` lives only on `feat/meal-reminders` commit `0e31e0b`). sdd-apply may instead rebase onto `feat/meal-reminders` and drop S1.1–S1.4.

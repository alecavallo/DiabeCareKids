# Proposal: Advanced View and Historical Record Management (CAP-005)

## Intent

Guardians frequently need to log meals retroactively when unable to log in real-time and correct past entries (e.g., updated carbohydrate estimates or 2-hour postprandial BG readings). This change provides an Advanced View to browse an ordered history timeline, add historical records without photos (INV-005), and edit existing records with automatic recalculation of real carbohydrates (`carbohidratos_reales`).

## Scope

### In Scope
- `PersistenceStore.getAll()` addition to query all stored meal records.
- Historical record entry form ("Add Past Record") with date/time selection, meal type, initial BG, food name, carb lookup/manual entry, consumed percentage, setting `es_registro_historico=true` and `foto_antes_url=null`.
- Record editing flow to update estimated carbs, consumed percentage, or 2h postprandial BG, recalculating `carbohidratos_reales` via `CarbMath`.
- Historical timeline screen displaying all records sorted by `fecha_hora_inicio` descending.
- Navigation routes and ViewModels in `composeApp` integrated into `AppGraph`.

### Out of Scope
- Photo capture for historical records (INV-005: photos are omitted/null).
- PDF export generation (deferred to CAP-004).
- Production Firestore deployment (continues with `InMemoryPersistenceStore`).
- Multi-child/family roster switching and advanced settings.

## Capabilities

### New Capabilities
- `advanced-history`: Historical record entry, past record editing with carb recalculation, and timeline visualization.

### Modified Capabilities
- None

## Approach

- Extend `PersistenceStore` with `suspend fun getAll(): List<RegistroComida>`.
- Implement `HistoryViewModel`, `PastRecordFormViewModel`, and `EditRecordViewModel` in `composeApp/src/commonMain`.
- Reuse `NutritionRepository` (USDA/Gemini + manual fallback) and `CarbMath.calcularCarbohidratosReales`.
- Expand sealed `Route` with `HistoryTimeline`, `AddPastRecord`, and `EditRecord(registro)`.
- UI screens built with Compose Multiplatform without platform-specific dependencies.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `shared/src/commonMain/.../persistence/PersistenceStore.kt` | Modified | Add `getAll()` interface method |
| `shared/src/commonMain/.../persistence/InMemoryPersistenceStore.kt` | Modified | Implement `getAll()` in mutex-guarded map |
| `composeApp/src/commonMain/.../navigation/Route.kt` | Modified | Add history, past record, and edit routes |
| `composeApp/src/commonMain/.../navigation/AppGraph.kt` | Modified | Factory methods for new ViewModels |
| `composeApp/src/commonMain/.../viewmodel/` | New | `HistoryViewModel`, `PastRecordFormViewModel`, `EditRecordViewModel` |
| `composeApp/src/commonMain/.../ui/` | New | `HistoryScreen`, `PastRecordFormScreen`, `EditRecordScreen` |
| `composeApp/src/commonTest/.../viewmodel/` | New | Unit tests for new ViewModels and store `getAll` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Timezone inconsistencies across historical dates | Low | Store all timestamps as epoch milliseconds (`Long`) |
| Inaccurate historical carb estimation | Low | Enable manual carb override alongside USDA/Gemini lookup |
| Unsorted timeline entries | Low | Explicitly sort by `fecha_hora_inicio` descending in ViewModel |

## Rollback Plan

Revert the change commits. The domain model `RegistroComida` and `InMemoryPersistenceStore` remain backwards-compatible with existing T0/T2 meal logging.

## Dependencies

- Existing `meal-logging` baseline (`RegistroComida`, `CarbMath`, `PersistenceStore`, `NutritionRepository`).

## Success Criteria

- [ ] Guardians can view all logged meals sorted chronologically descending.
- [ ] Guardians can create a historical record with custom date/time, `es_registro_historico=true`, and no photos.
- [ ] Editing carbs or 2h BG updates the record and recalculates `carbohidratos_reales` correctly.
- [ ] 100% unit test coverage for new ViewModels and persistence methods.

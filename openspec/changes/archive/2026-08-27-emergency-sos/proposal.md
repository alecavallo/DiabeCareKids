# Proposal: Emergency SOS Alert (CAP-001)

## Intent

Provide pediatric Type 1 Diabetes users with a fast, reliable emergency alert mechanism (CAP-001) that prevents accidental triggers (INV-003). A continuous 3.0s hold triggers haptics, captures GPS coordinates, creates an active emergency record, and notifies all guardians.

## Scope

### In Scope
- `SosHoldStateMachine` pure state machine in `shared/commonMain` enforcing INV-003 (3.0s continuous hold to trigger, release before 3.0s resets progress to 0% with no alert).
- `Emergencia` domain model and `EmergenciaStore` interface with `InMemoryEmergenciaStore` implementation.
- Platform abstraction interfaces: `LocationProvider`, `GuardianNotifier`, and `Haptics`.
- UI components: `EmergencySOSButton` with hold gesture / visual progress and `SosScreen` in `composeApp`.
- `SosViewModel` managing SOS lifecycle and `AppGraph` DI integration.
- `androidMain` bridges for GPS (`FusedLocationProviderClient`) and system haptics (`Vibrator` / `VibratorManager`).
- Comprehensive unit tests for `SosHoldStateMachine`, `InMemoryEmergenciaStore`, and `SosViewModel`.

### Out of Scope
- Production Firestore sync and FCM push infrastructure (deferred to future changes).
- Guardian roster management and family invitation flows.
- Continuous background GPS tracking after trigger.

## Capabilities

### New Capabilities
- `emergency-sos`: Emergency alert triggering via continuous 3.0s hold gesture with GPS coordinate capture, emergency record creation, and guardian notification intent (INV-003).

### Modified Capabilities
- None

## Approach

- **KMP-Common-First**: Core logic, hold timing state machine, and persistence interfaces reside entirely in `shared/commonMain` for 100% offline unit-testability without UI instrumentation.
- **Pure State Machine**: `SosHoldStateMachine` accepts clock ticks / hold events and produces deterministic progress / triggered states, strictly enforcing INV-003.
- **Interface-Driven Adapters**: GPS, guardian dispatch, and haptics are exposed via clean interfaces; `androidMain` provides platform bridges while tests use deterministic fakes.
- **Composition Root**: `AppGraph` provides manual constructor injection for `SosViewModel` and registers `Route.Sos` in navigation.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `shared/src/commonMain/kotlin/.../domain/` | New | `Emergencia` model, `EmergenciaEstado`, `UbicacionGps` |
| `shared/src/commonMain/kotlin/.../sos/` | New | `SosHoldStateMachine`, `LocationProvider`, `GuardianNotifier`, `Haptics` |
| `shared/src/commonMain/kotlin/.../persistence/` | New | `EmergenciaStore` interface and `InMemoryEmergenciaStore` |
| `shared/src/androidMain/kotlin/.../platform/` | New | Android GPS and Haptic platform adapters |
| `shared/src/commonTest/kotlin/.../sos/` | New | Unit tests for state machine, timing, and store |
| `composeApp/src/commonMain/kotlin/.../ui/` | New | `EmergencySOSButton`, `SosScreen` composables |
| `composeApp/src/commonMain/kotlin/.../viewmodel/` | New | `SosViewModel` and `SosState` |
| `composeApp/src/commonMain/kotlin/.../navigation/` | Modified | Add `Route.Sos` to `Route.kt` and wire into `AppGraph.kt` & `App.kt` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Pointer gesture cancellation quirks in Compose | Medium | Separate gesture detection from state machine; test state machine logic independently with synthetic events |
| Android GPS permission denial / disabled location | Medium | Handle missing permissions gracefully in `LocationProvider` bridge with null/last-known coordinates |
| Timing inaccuracies in hold duration across devices | Low | State machine accepts configurable clock/timestamp intervals |

## Rollback Plan

Revert the change commit(s) or git checkout to prior `main`. All new files are isolated under SOS domains and no schema migrations exist.

## Dependencies

- None (uses existing KMP, Compose Multiplatform, and Coroutines dependencies).

## Success Criteria

- [ ] Unit tests verify INV-003: releasing before 3.00s emits 0% progress and no emergency record.
- [ ] Unit tests verify holding for >= 3.00s transitions state to `Triggered`, records `Emergencia` with `ACTIVA` status, and invokes `GuardianNotifier` & `Haptics`.
- [ ] Manual testable Compose UI displays progress animation during hold and resets on early release.

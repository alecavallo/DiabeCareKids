# Tasks: Emergency SOS Alert (CAP-001)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1,100–1,200 total (Slice 1 ≈ 500, Slice 2 ≈ 600) |
| 800-line budget risk | Low — each slice stays under 800 |
| Chained PRs recommended | Yes |
| Suggested split | PR #1 (Slice 1) → PR #2 (Slice 2), stacked-to-main |
| Delivery strategy | auto-chain |
| Sub-split needed | No — no single slice exceeds 800 |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|----|----------------------|-----------------|-------------------|
| 1 | shared SOS core (domain + machine + controller + stores/interfaces) + commonTest | 1 | `make gradle :shared:testDebugUnitTest` | `make test` (offline Docker) | revert `shared/src/commonMain` + `shared/src/commonTest`; nothing wired, behavior-inert |
| 2 | composeApp UI/VM/navigation + androidMain bridges + manifest/gradle + VM tests | 2 | `make gradle :composeApp:testDebugUnitTest` | `make test` && `make build` | revert `composeApp/*`, `gradle/libs.versions.toml`; shared untouched |

Package root: `kotlin/com/diabecarekids/app` (both modules).

<!-- ===== SLICE 1 BOUNDARY (work-unit commit 1 → PR #1) ===== -->
## Slice 1 (PR #1) — shared/commonMain + shared/commonTest

| ID | Task | Spec | Files | Verify |
|----|------|------|-------|--------|
| [x] 1.1 | Create `domain/Emergencia.kt`: `@Serializable` `Emergencia` (id, patient id/name, `fecha_hora` epoch-ms, nullable `UbicacionGps`, `estado`), `EmergenciaEstado` (`ACTIVA`), `UbicacionGps` (lat/long/accuracy) | REQ-SOS-002, 003 | `shared/src/commonMain/kotlin/com/diabecarekids/app/domain/Emergencia.kt` | `make gradle :shared:testDebugUnitTest` |
| [x] 1.2 | Create `persistence/EmergenciaStore.kt`: interface (`save`/`get`/`update`) + `InMemoryEmergenciaStore` (Mutex map, mirror `InMemoryPersistenceStore`) | REQ-SOS-002 | `shared/src/commonMain/.../persistence/EmergenciaStore.kt` | `make gradle :shared:testDebugUnitTest` |
| [x] 1.3 | Modify `platform/Platform.kt`: add `LocationProvider` (`suspend fun currentLocation(): UbicacionGps?`) + `Haptics` (`fun vibrateSosTriggered()`) | REQ-SOS-001, 003 | `shared/src/commonMain/.../platform/Platform.kt` | `make gradle :shared:testDebugUnitTest` |
| [x] 1.4 | Create `sos/GuardianNotifier.kt`: interface `notifyAllGuardians(Emergencia)` + `InMemoryGuardianNotifier` | REQ-SOS-004 | `shared/src/commonMain/.../sos/GuardianNotifier.kt` | `make gradle :shared:testDebugUnitTest` |
| [x] 1.5 | Create `sos/SosHoldStateMachine.kt`: pure machine `Idle→Pressing→Arming→Triggered`, injected `clock: () -> Long`, `StateFlow` state, progress 0..1; `holdEnd` no-op after Arming (INV-003) | REQ-SOS-001 | `shared/src/commonMain/.../sos/SosHoldStateMachine.kt` | `make gradle :shared:testDebugUnitTest` |
| [x] 1.6 | Create `sos/SosController.kt`: fire pipeline location→build `Emergencia(ACTIVA, injected id)`→`store.save`→`notifier.notifyAllGuardians`→`haptics.vibrateSosTriggered`→`onTriggerConfirmed` | REQ-SOS-002, 003, 004 | `shared/src/commonMain/.../sos/SosController.kt` | `make gradle :shared:testDebugUnitTest` |
| [x] 1.7 | Create `SosHoldStateMachineTest.kt`: 2.5s reset, 3.0s boundary, release-after-arm ignored, confirm — synthetic clock | REQ-SOS-001 | `shared/src/commonTest/.../sos/SosHoldStateMachineTest.kt` | `make gradle :shared:testDebugUnitTest` |
| [x] 1.8 | Create `SosControllerTest.kt`: pipeline order, null-location → null coords, save+notify+haptics invoked | REQ-SOS-002, 003, 004 | `shared/src/commonTest/.../sos/SosControllerTest.kt` | `make gradle :shared:testDebugUnitTest` |
| [x] 1.9 | Create `EmergenciaStoreTest.kt`: CRUD + `@Serializable` round-trip | REQ-SOS-002 | `shared/src/commonTest/.../persistence/EmergenciaStoreTest.kt` | `make gradle :shared:testDebugUnitTest` |

> No new shared dependency expected: coroutines (Mutex/StateFlow) + kotlinx.serialization already present in `shared`. Leave `shared/build.gradle.kts` untouched unless compilation proves otherwise.

<!-- ===== SLICE 2 BOUNDARY (work-unit commit 2 → PR #2) ===== -->
## Slice 2 (PR #2) — composeApp UI/VM/navigation + androidMain bridges

| ID | Task | Spec | Files | Verify |
|----|------|------|-------|--------|
| [x] 2.1 | Create `viewmodel/SosViewModel.kt`: `SosState` binder; `onHoldStart`/`onTick`/`onHoldEnd`/`reset` forward to `SosController` | REQ-SOS-001 | `composeApp/src/commonMain/.../viewmodel/SosViewModel.kt` | `make gradle :composeApp:testDebugUnitTest` |
| [x] 2.2 | Create `ui/EmergencySOSButton.kt`: `pointerInput` `awaitEachGesture` + `waitForUpOrCancellation` (cancellation resets, never triggers) + Canvas progress ring | REQ-SOS-001 | `composeApp/src/commonMain/.../ui/EmergencySOSButton.kt` | `make gradle :composeApp:testDebugUnitTest` |
| [x] 2.3 | Create `ui/SosScreen.kt`: Route.Sos screen, runtime location permission entry, "Alerta enviada" confirmation state | REQ-SOS-001, 003 | `composeApp/src/commonMain/.../ui/SosScreen.kt` | `make gradle :composeApp:testDebugUnitTest` |
| [x] 2.4 | Modify `navigation/Route.kt`: add `data object Sos : Route` | REQ-SOS-001 | `composeApp/src/commonMain/.../navigation/Route.kt` | `make gradle :composeApp:testDebugUnitTest` |
| [x] 2.5 | Modify `navigation/AppGraph.kt`: lazy `sosViewModel`; build `SosController` with injected location/notifier/haptics | REQ-SOS-001..004 | `composeApp/src/commonMain/.../navigation/AppGraph.kt` | `make gradle :composeApp:testDebugUnitTest` |
| [x] 2.6 | Modify `App.kt`: add `Route.Sos` branch + reset-on-exit | REQ-SOS-001 | `composeApp/src/commonMain/.../App.kt` | `make gradle :composeApp:testDebugUnitTest` |
| [x] 2.7 | Modify `ui/MealFormScreen.kt`: add SOS entry button → `Route.Sos` | REQ-SOS-001 | `composeApp/src/commonMain/.../ui/MealFormScreen.kt` | `make gradle :composeApp:testDebugUnitTest` |
| [x] 2.8 | Create `sos/FusedLocationProvider.kt`: `getCurrentLocation` (API 30+)/`getLastLocation`, null on denial | REQ-SOS-003 | `composeApp/src/androidMain/.../sos/FusedLocationProvider.kt` | `make build` |
| [x] 2.9 | Create `sos/AndroidHaptics.kt`: `VibratorManager`/`Vibrator` | REQ-SOS-001 | `composeApp/src/androidMain/.../sos/AndroidHaptics.kt` | `make build` |
| [x] 2.10 | Modify `MainActivity.kt`: wire `FusedLocationProvider` + `AndroidHaptics` into `AppGraph` | REQ-SOS-003 | `composeApp/src/androidMain/.../MainActivity.kt` | `make build` |
| [x] 2.11 | Modify `AndroidManifest.xml`: add `FINE_LOCATION`/`COARSE_LOCATION` + `VIBRATE` | REQ-SOS-001, 003 | `composeApp/src/androidMain/AndroidManifest.xml` | `make build` |
| [x] 2.12 | Modify `gradle/libs.versions.toml`: add `play-services-location` | REQ-SOS-003 | `gradle/libs.versions.toml` | `make build` |
| [x] 2.13 | Modify `composeApp/build.gradle.kts`: add `play-services-location` to `androidMain.dependencies` | REQ-SOS-003 | `composeApp/build.gradle.kts` | `make build` |
| [x] 2.14 | Modify `viewmodel/Fakes.kt`: add location/notifier/haptics fakes | REQ-SOS-001..004 | `composeApp/src/commonTest/.../viewmodel/Fakes.kt` | `make gradle :composeApp:testDebugUnitTest` |
| [x] 2.15 | Create `viewmodel/SosViewModelTest.kt`: event forwarding + reset (coroutines-test `runTest`) | REQ-SOS-001 | `composeApp/src/commonTest/.../viewmodel/SosViewModelTest.kt` | `make gradle :composeApp:testDebugUnitTest` |

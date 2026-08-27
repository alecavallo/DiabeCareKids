# Design: Emergency SOS Alert (CAP-001)

## Technical Approach

KMP-common-first, mirroring meal-logging patterns. INV-003 enforcement lives in a pure `SosHoldStateMachine` (shared/commonMain) driven by injected clock ticks; the UI gesture only reports start/end. `SosController` (shared/commonMain) owns the machine and runs the fire pipeline: location → build `Emergencia` → `store.save` → `notifier.notifyAllGuardians` → haptics. Android bridges (GPS, haptics) live in composeApp/androidMain following the `TakePicturePhotoCapture` precedent; Firestore/FCM deferred behind interfaces.

## Architecture Decisions

| # | Decision | Alternatives rejected | Rationale |
|---|---|---|---|
| 1 | Machine states `Idle → Pressing(progress) → Arming → Triggered`; Arming = point of no return at elapsed ≥ 3.0s; `onTriggerConfirmed()` advances Arming→Triggered | direct Pressing→Triggered; Arming auto-advancing on next tick | Arming encodes the boundary scenario ("regardless of subsequent release"): holdEnd is a no-op once armed. Triggered means pipeline completed — no tick-cadence coupling |
| 2 | Injected `clock: () -> Long` (default `::epochMillisNow`); progress = elapsed/3000ms, exposed 0.0..1.0 | machine reading wall-clock | Synthetic clocks make 2.5s / 3.0s / exact-boundary tests deterministic, fully offline |
| 3 | Interfaces in shared/commonMain; Android adapters in composeApp/androidMain | shared/androidMain (proposal table) | Meal-logging precedent: Context-needing adapters (`TakePicturePhotoCapture`, `WorkManagerAlarmScheduler`) live in composeApp/androidMain; shared/androidMain holds pure expect actuals. Documented deviation from proposal |
| 4 | `InMemoryGuardianNotifier` as production placeholder | no-op impl | Mirrors `InMemoryPersistenceStore`-as-prod precedent; FCM transport later |
| 5 | Dedicated `SosScreen` (Route.Sos) hosting `EmergencySOSButton`; SOS entry button on MealFormScreen | overlay FAB on the scrollable form | MealFormScreen is a LazyColumn: a 3s hold on scrollable surface risks gesture cancellation. Non-scrolling SOS screen is the gesture-stable surface; reusable button keeps ring testable |
| 6 | Runtime location permission requested on SosScreen entry, never at trigger | request at trigger; defer entirely | Spec requires the alert never block on a dialog. Pre-armed screen means trigger-time denial → null coords, alert still fires |
| 7 | 16ms tick loop in UI (`LaunchedEffect` while pressed) | VM-owned tick job | VM stays a pure event-forwarding binder; trigger tests call `onTick()` explicitly with synthetic clock — no coroutine-timing coupling |

## Data Flow

```
EmergencySOSButton ──start/tick/end──→ SosViewModel ──→ SosController ──→ SosHoldStateMachine
      (gesture only)                     (binder)          (owner)             (pure)
LaunchedEffect: while(pressed) { delay(16); onTick() }
At 3.0s: machine → Arming (one-way)
Controller.fire(): currentLocation()? → build Emergencia(ACTIVA)
  → store.save → notifier.notifyAllGuardians → haptics.vibrateSosTriggered
  → machine.onTriggerConfirmed() → Triggered → UI "Alerta enviada"
Release < 3.0s: machine → Idle, progress 0, nothing fires (INV-003)
```

## File Changes

| File | Action | Description |
|---|---|---|
| `shared/commonMain/.../domain/Emergencia.kt` | Create | `Emergencia` (@Serializable, epoch-ms `fecha_hora`, nullable lat/long/precision, `estado`), `EmergenciaEstado`, `UbicacionGps` |
| `shared/commonMain/.../sos/SosHoldStateMachine.kt` | Create | Pure machine, StateFlow state, progress 0..1 |
| `shared/commonMain/.../sos/SosController.kt` | Create | Fire pipeline, Arming-entry detection (guarded, always confirms) |
| `shared/commonMain/.../sos/GuardianNotifier.kt` | Create | Interface + InMemory impl |
| `shared/commonMain/.../persistence/EmergenciaStore.kt` | Create | Interface + InMemoryEmergenciaStore (Mutex map) |
| `shared/commonMain/.../platform/Platform.kt` | Modify | + `LocationProvider`, `Haptics` |
| `shared/commonTest/.../sos/SosHoldStateMachineTest.kt` | Create | INV-003: 2.5s reset, 3.0s boundary, release-after-arm ignored |
| `shared/commonTest/.../sos/SosControllerTest.kt` | Create | Pipeline order, null location, save+notify+haptics |
| `shared/commonTest/.../persistence/EmergenciaStoreTest.kt` | Create | CRUD |
| `composeApp/commonMain/.../viewmodel/SosViewModel.kt` | Create | SosState binder + reset() |
| `composeApp/commonMain/.../ui/EmergencySOSButton.kt` | Create | pointerInput hold + Canvas progress ring |
| `composeApp/commonMain/.../ui/SosScreen.kt` | Create | Route.Sos screen, permission entry, confirmation state |
| `composeApp/commonMain/.../navigation/Route.kt` | Modify | + `Route.Sos` |
| `composeApp/commonMain/.../navigation/AppGraph.kt` | Modify | + lazy `sosViewModel`, builds controller |
| `composeApp/commonMain/.../App.kt` | Modify | + Sos branch, reset-on-exit |
| `composeApp/commonMain/.../ui/MealFormScreen.kt` | Modify | + SOS entry button (required for reachability; deviation from proposal table) |
| `composeApp/commonTest/.../viewmodel/SosViewModelTest.kt` | Create | Event forwarding, reset |
| `composeApp/commonTest/.../viewmodel/Fakes.kt` | Modify | + location/notifier/haptics fakes |
| `composeApp/androidMain/.../sos/FusedLocationProvider.kt` | Create | getCurrentLocation (API 30+)/getLastLocation; null on denial |
| `composeApp/androidMain/.../sos/AndroidHaptics.kt` | Create | VibratorManager/Vibrator |
| `composeApp/androidMain/.../MainActivity.kt` | Modify | Wire providers into AppGraph |
| `composeApp/androidMain/AndroidManifest.xml` | Modify | + FINE/COARSE location, VIBRATE |
| `gradle/libs.versions.toml` | Modify | + play-services-location |
| `composeApp/build.gradle.kts` | Modify | + location dep |

## Interfaces / Contracts

```kotlin
// shared — platform/Platform.kt additions
interface LocationProvider { suspend fun currentLocation(): UbicacionGps? } // null = denied/unavailable
interface Haptics { fun vibrateSosTriggered() }

// shared — sos/GuardianNotifier.kt
interface GuardianNotifier { suspend fun notifyAllGuardians(emergencia: Emergencia) }

// shared — persistence/EmergenciaStore.kt (update reserved for future RESUELTA)
interface EmergenciaStore {
    suspend fun save(emergencia: Emergencia)
    suspend fun get(id: String): Emergencia?
    suspend fun update(emergencia: Emergencia)
}
```

Gesture (non-obvious pattern — cancellation must reset, never trigger):

```kotlin
Modifier.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown()
        onHoldStart()
        try { waitForUpOrCancellation() } finally { onHoldEnd() }
    }
}
```

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (shared/commonTest) | Machine INV-003 (2.5s/3.0s/boundary/release-after-arm/confirm); controller pipeline order + null-location; store | kotlin.test, synthetic clock, fakes |
| Unit (composeApp/commonTest) | SosViewModel forwarding + reset | coroutines-test runTest, Fakes.kt |
| Manual | 3s ring animation, reset on early release, device haptic | success criteria; no UI instrumentation in repo |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process integration. GuardianNotifier is an in-process interface (Firestore/FCM deferred); location is a device API.

## Migration / Rollout

No migration. New code is isolated under SOS domains; rollback = revert commits. Manifest gains runtime permissions (location, vibrate).

## Slice Forecast (auto-chain, 800-line budget)

Total ≈ 1,100–1,200 lines > 800 → chain needed.
- **Slice 1 (PR #1)**: shared/commonMain domain + machine + controller + stores/interfaces + shared/commonTest. ≈ 500 lines. Autonomous, behavior-inert (nothing wired).
- **Slice 2 (PR #2)**: composeApp UI/VM/navigation + androidMain bridges + manifest/gradle + VM tests. ≈ 600 lines.
- Chained PRs recommended: **Yes** · 400-line budget risk: **High** · Decision needed before apply: **No** (auto-chain resolves).

## Open Questions

None blocking. Firestore/FCM transport and guardian roster are out of scope by proposal.

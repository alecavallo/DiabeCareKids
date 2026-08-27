# Proposal: Two-Stage Meal Logging (T0/T2)

## Intent

Implement pediatric Type 1 Diabetes (DM1) two-stage meal logging: pre-meal (T0) carb estimation with 2h postprandial alarm scheduling, and post-meal (T2) intake adjustment. Ensure deterministic/AI hybrid carb counting respecting INV-002 (all carb values editable) and INV-005 (optional photos).

## Scope

### In Scope
- KMP domain models (`RegistroComida`, `FoodItem`, `CarbSource`) in `shared/commonMain` with `kotlinx.serialization`.
- `NutritionRepository` interface with 3-tier carb resolution: USDA deterministic (Ktor) -> Gemini AI estimation (`[AI Estimated]`) -> manual user input.
- Real-carbs calculation: `carbohidratos_reales = carbohidratos_calculados * (%consumido / 100)`.
- `PersistenceStore` interface in `commonMain` with in-memory/fake store for offline CI execution.
- Platform port definitions (interfaces/expect-actual) for CameraX photo capture, WorkManager 2h alarm, and Firestore.
- Compose Multiplatform UI & ViewModels for T0 (BG chips, food cards, editable carbs, 2h alarm) and T2 (% slider, 2h BG, real carbs).
- Unit tests in `shared/commonTest` runnable via Docker zero-host toolchain (`make test`).

### Out of Scope
- Live Firestore cloud deployment (stubbed/fake store for this slice).
- Gemini/USDA production API keys (injected via env vars; mocked in CI/tests).
- Historical meal logs / CRUD editing (CAP-005).
- Standalone meal reminders (CAP-006), SOS emergency alerts (CAP-001), and PDF clinical exports (CAP-004).

## Capabilities

### New Capabilities
- `meal-logging`: Two-stage meal logging covering T0 pre-meal hybrid carb counting with 2h alarm scheduling and T2 postprandial intake adjustment with real-carbs calculation.

### Modified Capabilities
- None

## Approach

KMP-common-first architecture. Core calculation logic, domain models, Ktor HTTP client, and `PersistenceStore` reside in `shared/commonMain`. Platform-specific capabilities (CameraX, WorkManager) are exposed via interfaces/expect-actual with test doubles enabling zero-network CI execution.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `gradle/libs.versions.toml` | Modified | Add Ktor, kotlinx-serialization, coroutines |
| `shared/build.gradle.kts` | Modified | Configure commonMain dependencies |
| `shared/src/commonMain/...` | New | Domain models, repositories, carb calculator, interfaces |
| `shared/src/commonTest/...` | New | Unit tests for fallback chain and real-carbs math |
| `composeApp/src/commonMain/...` | New | T0/T2 Compose UI screens and ViewModels |
| `shared/src/androidMain/...` | New | Platform adapter stubs (CameraX, WorkManager) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| CI network dependency / key leakage | Medium | In-memory `PersistenceStore` and mock HTTP engine in CI |
| Exceeding 800-line review budget | High | Auto-chain split: Slice 1 (Domain/Data/Tests), Slice 2 (UI/Adapters) |
| KMP Ktor/Serialization compatibility | Low | Standardized multiplatform versions in version catalog |

## Rollback Plan

Revert git branch/commits. No database migrations or persistent cloud resources are modified.

## Dependencies

- Baseline infrastructure (`make test` in Docker) from PR #1.

## Success Criteria

- [ ] Hybrid carb resolution (USDA -> Gemini -> manual) verified with unit tests.
- [ ] Postprandial real carb formula (`calculados * %consumido`) verified with unit tests.
- [ ] INV-002 confirmed: carb values remain fully editable at T0 and T2 before save.
- [ ] INV-005 confirmed: flows succeed with or without photo attachments.
- [ ] `make test` passes in Docker without host dependencies or API keys.

# Tasks: Two-Stage Meal Logging (T0/T2)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1150–1250 total |
| Slice 1 estimate | ~520–560 (Low vs 800) |
| Slice 2 estimate | ~620–700 (Medium vs 800) |
| Effective review budget | 800 (session override) |
| Chained PRs recommended | Yes |
| Suggested split | PR1 = Slice 1 → PR2 = Slice 2 (stacked-to-main) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |
| Sub-split needed | No — both slices within 800; S2 is the binding slice |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| Slice 1 | shared module: domain + engine + store + tests | PR 1 | `make test` (runs `:shared:testDebugUnitTest`) | `make test` (Docker kmp-builder, offline) | revert PR1; no UI touched |
| Slice 2 | adapters + Compose UI + ViewModels + nav | PR 2 | `make test` then `make build` | `make test`/`make build` (Docker) | revert PR2; shared unchanged |

## Slice 1 — shared/commonMain domain, data, tests

**Start**: catalog+deps. **Finish**: `make test` green, full S1 suite. No UI, no network at test time.

| ID | Title / Description | Reqs | Files | Verify |
|----|--------------------|------|-------|--------|
| [x] Slice1.1 | Pin deps in version catalog: ktor 3.0.3, kotlinx-serialization 1.7.3, coroutines 1.9.0, androidx-work 2.9.1; add ktor-client-core/okhttp/mock, ktor-serialization-kotlinx-json, kotlinx-serialization-json, coroutines-core/test, work-runtime-ktx, serialization plugin | MEAL-001, 002 | gradle/libs.versions.toml | `make test` (catalog parses) |
| [x] Slice1.2 | Register `kotlinx-serialization` plugin alias `apply false` in root build | MEAL-001 | build.gradle.kts | `make test` |
| [x] Slice1.3 | shared/build.gradle.kts: apply serialization plugin; commonMain deps (ktor-client-core, ktor-serialization-kotlinx-json, kotlinx-serialization-json, coroutines-core); androidMain (ktor-client-okhttp); commonTest (kotlin-test, coroutines-test, ktor-client-mock) | MEAL-001, 002, 005 | shared/build.gradle.kts | `make test` (compiles shared) |
| [x] Slice1.4 | `@Serializable RegistroComida` + `TipoComida`/`CarbSource` enums + `FoodItem` in `com.diabecarekids.app.domain` | MEAL-001 | shared/src/commonMain/.../domain/ | `make test` |
| [x] Slice1.5 | Pure `CarbMath.calcularCarbohidratosReales(estimados, porcentaje)` = estimados * porcentaje / 100 | MEAL-003 | shared/src/commonMain/.../domain/CarbMath.kt | `make test` |
| [x] Slice1.6 | platform: expect `httpClientEngine()` + `epochMillisNow()` (refactor Platform.kt); add `PhotoCapture` + `PostprandialAlarmScheduler` interfaces | MEAL-002, 004 | shared/src/commonMain/.../platform/ | `make test` |
| [x] Slice1.7 | platform androidMain actuals: OkHttp engine + `epochMillisNow` | MEAL-002 | shared/src/androidMain/.../platform/ | `make test` |
| [x] Slice1.8 | `ApiConfig(usdaKey?, geminiKey?)` env-injected + `UsdaDataSource`/`GeminiDataSource` interfaces | MEAL-002 | shared/src/commonMain/.../nutrition/ | `make test` |
| [x] Slice1.9 | Thin Ktor adapters `UsdaApiClient` / `GeminiApiClient` over the datasource interfaces | MEAL-002 | shared/src/commonMain/.../nutrition/ | `make test` |
| [x] Slice1.10 | `CarbResolution` sealed + `CarbResolutionEngine` + `NutritionRepository` + `NutritionRepositoryImpl` (USDA→Gemini "[AI Estimated]"→ManualRequired fallback; exceptions fail down) | MEAL-002 | shared/src/commonMain/.../nutrition/ | `make test` |
| [x] Slice1.11 | `PersistenceStore` + `InMemoryPersistenceStore` (Mutex + Map) — prod store AND offline test double | MEAL-005 | shared/src/commonMain/.../persistence/ | `make test` |
| [x] Slice1.12 | commonTest: fake datasources + serialization round-trip test | MEAL-001 | shared/src/commonTest/... | `make test` |
| [x] Slice1.13 | commonTest: CarbMath real-carbs cases (80% of 50g → 40g) | MEAL-003 | shared/src/commonTest/... | `make test` |
| [x] Slice1.14 | commonTest: fallback chain via fake datasources + Ktor MockEngine (hit / miss→Gemini / all-fail→ManualRequired) | MEAL-002 | shared/src/commonTest/... | `make test` |
| [x] Slice1.15 | commonTest: store save/load + MockEngine URL/JSON mapping | MEAL-005, 002 | shared/src/commonTest/... | `make test` |

## Slice 2 — platform impls + Compose UI + ViewModels + navigation

**Start**: AppGraph + adapter impls. **Finish**: `make test` + `make build` green.

| ID | Title / Description | Reqs | Files | Verify |
|----|--------------------|------|-------|--------|
| [x] Slice2.1 | composeApp/build.gradle.kts: add lifecycle-runtime-compose, coroutines, work-runtime-ktx | MEAL-002, 004 | composeApp/build.gradle.kts | `make build` |
| [x] Slice2.2 | `PhotoCapture` impl via `ActivityResultContracts.TakePicture` + FileProvider (per design decision; "CameraX" name in table is stale — see Risks) | MEAL-004 | composeApp/src/androidMain/... | `make build` |
| [x] Slice2.3 | `WorkManagerAlarmScheduler` (2h postprandial delay) | MEAL-002 | composeApp/src/androidMain/... | `make build` |
| [x] Slice2.4 | MainActivity wiring + manifest (CAMERA permission, FileProvider) | MEAL-004 | composeApp/src/androidMain/... | `make build` |
| [x] Slice2.5 | `AppGraph` composition root + sealed-route navigation state | MEAL-002 | composeApp/src/commonMain/.../navigation | `make build` |
| [x] Slice2.6 | T0 `MealFormViewModel` + state (BG chips, food suggestions, editable carbs, resolve flow) | MEAL-002 | composeApp/src/commonMain/.../viewmodel | `make test` |
| [x] Slice2.7 | T0 `MealFormScreen` Compose UI | MEAL-002 | composeApp/src/commonMain/.../ui | `make build` |
| [x] Slice2.8 | T2 `FollowUpViewModel` + state (intake slider, 2h BG, real-carbs preview) | MEAL-003 | composeApp/src/commonMain/.../viewmodel | `make test` |
| [x] Slice2.9 | T2 `FollowUpScreen` Compose UI | MEAL-003 | composeApp/src/commonMain/.../ui | `make build` |
| [x] Slice2.10 | commonTest: ViewModel tests with fakes (editable carbs, T2 calc, nullable photos) | MEAL-002, 003, 004 | composeApp/src/commonTest/... | `make test` |

## Chained PR Layout (auto-chain, stacked-to-main)

- PR 1 (Slice 1) → merge to main; gate `make test` green (offline).
- PR 2 (Slice 2) → stack on PR 1; gate `make test` + `make build`.
- Per-PR rollback = revert the slice commit.

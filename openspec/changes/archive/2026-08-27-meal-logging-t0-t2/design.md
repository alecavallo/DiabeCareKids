# Design: Two-Stage Meal Logging (T0/T2)

## Technical Approach

KMP common-first. Domain models, carb resolution, and persistence contracts live in `shared/commonMain`; Android-only capabilities (photo, 2h alarm, Firestore later) are `commonMain` interfaces implemented in `composeApp/androidMain`, injected manually. Two chained slices: **S1** = shared module (models, engine, store, tests), **S2** = platform adapters + Compose UI + ViewModels + navigation.

## Architecture Decisions

| Decision | Choice | Alternatives | Rationale |
|---|---|---|---|
| Ktor engine | `ktor-client-core` in commonMain; engine injected via expect/actual | CIO in commonMain | Engine-free commonMain stays platform-agnostic (iOS later); commonTest uses MockEngine → zero network |
| Versions | Ktor 3.0.3, kotlinx-serialization 1.7.3, coroutines 1.9.0, work-runtime-ktx 2.9.1 | Ktor 3.1+ | 3.1+ is Kotlin 2.1-built; 3.0.x is 2.0-built and matches the 2.0.21 toolchain |
| DI | Manual constructor injection; `AppGraph` composition root (S2) | Koin/Kodein | 2 screens, 1 target; a DI lib adds surface with no current payoff; revisit with iOS/scale |
| Navigation | Sealed-route state in `App()` | nav-compose | Two routes only; avoids a dependency |
| IDs/time | `String` id via `kotlin.uuid.Uuid` (opt-in `@ExperimentalUuidApi`); epoch-millis `Long` via expect/actual `epochMillisNow()` | kotlinx-datetime | No extra dependency; Firestore Timestamp conversion deferred to the Firestore adapter |
| Photo capture | `ActivityResultContracts.TakePicture` + FileProvider | CameraX | Zero new dependency; system camera covers the MVP; CameraX later |

## Data Flow & Sequence (carb resolution)

```
ViewModel → CarbResolutionEngine → UsdaDataSource (Ktor) ──hit──► Resolved(USDA)
                                    │ miss/404/empty
                                    ▼
                                  GeminiDataSource (Ktor) ──value──► Resolved(GEMINI_AI, "[AI Estimated]")
                                    │ fail/timeout
                                    ▼
                                  ManualRequired → UI shows manual carb input (INV-002)
T0: form → resolve (editable) → save → InMemoryPersistenceStore
T2: slider% + 2h BG → calcularCarbohidratosReales → update store
```

## Contracts (shared/commonMain)

```kotlin
interface CarbResolutionEngine { suspend fun resolve(foodQuery: String): CarbResolution }
sealed interface CarbResolution {
  data class Resolved(val carbsGrams: Double, val source: CarbSource, val label: String?) : CarbResolution
  data object ManualRequired : CarbResolution
}
interface NutritionRepository { // wraps engine + suggestion cards
  suspend fun resolveCarbs(query: String): CarbResolution
  suspend fun suggestFoods(query: String): List<FoodItem>  // USDA only; empty when offline
}
interface PersistenceStore { suspend fun save(r: RegistroComida); suspend fun get(id: String): RegistroComida?; suspend fun update(r: RegistroComida); suspend fun delete(id: String) }
interface PhotoCapture { suspend fun takePhoto(): String? }            // temp URI or null (INV-005)
interface PostprandialAlarmScheduler { fun schedule(mealId: String); fun cancel(mealId: String) } // 2h delay
```

**Domain** (package `com.diabecarekids.app.domain`): `@Serializable RegistroComida(id, tipo_comida, glicemia_inicial, nombre_alimento, carbohidratos_estimados, fuente_carbohidratos, carbohidratos_reales?, glicemia_postprandial_2h?, porcentaje_consumido?, es_registro_historico, foto_antes_url?, foto_despues_url?, creado_por_usuario_id, ultima_modificacion)`; enums `TipoComida` (DESAYUNO/ALMUERZO/MERIENDA/CENA), `CarbSource` (USDA/GEMINI_AI/MANUAL); `FoodItem(name, carbsGrams, source)`; pure `fun calcularCarbohidratosReales(estimados: Double, porcentaje: Int): Double = estimados * porcentaje / 100.0` in `CarbMath.kt`.

**Fallback contract** (`nutrition/`): tier skipped when its API key is null (`ApiConfig(usdaKey?, geminiKey?)`, env-injected, never committed); USDA 404/empty → Gemini; Gemini fail/timeout → `ManualRequired`; exceptions fail down the chain; all resolved values editable before save; `"[AI Estimated]"` label only on GEMINI_AI. `NutritionRepositoryImpl` composes engine + datasources; `UsdaApiClient`/`GeminiApiClient` are thin Ktor adapters over `UsdaDataSource`/`GeminiDataSource` interfaces.

**Persistence** (`persistence/`): `InMemoryPersistenceStore` (Mutex + Map) is the production store for this change AND the offline test double (REQ-MEAL-005). Firestore adapter deferred to a later change.

## File Changes

| File | Action | Slice |
|---|---|---|
| gradle/libs.versions.toml | Modify | 1 |
| build.gradle.kts (root) | Modify | 1 |
| shared/build.gradle.kts | Modify | 1 |
| shared/src/commonMain/.../domain/ (models, CarbMath) | Create | 1 |
| shared/src/commonMain/.../nutrition/ (engine, repo, Ktor clients, ApiConfig) | Create | 1 |
| shared/src/commonMain/.../persistence/ (PersistenceStore, InMemory) | Create | 1 |
| shared/src/commonMain/.../platform/ (expect httpClientEngine/epochMillisNow, PhotoCapture, AlarmScheduler) | Create | 1 |
| shared/src/androidMain/.../platform/ (actual OkHttp engine, epochMillisNow) | Create | 1 |
| shared/src/commonTest/... (fake datasources, engine chain, CarbMath, serialization round-trip, store, MockEngine mapping) | Create | 1 |
| composeApp/src/commonMain/.../ui,viewmodel,navigation (T0/T2 screens, ViewModels, AppGraph) | Create | 2 |
| composeApp/src/androidMain/... (CameraXPhotoCapture, WorkManagerAlarmScheduler, MainActivity wiring, manifest CAMERA + FileProvider) | Create | 2 |
| composeApp/src/commonTest/... (ViewModel tests with fakes) | Create | 2 |

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (S1) | Fallback chain (hit/miss/timeout per tier), carb math, serialization round-trip, store save/load | kotlin-test + runTest (coroutines-test), fake datasources |
| Integration (S1) | Ktor adapters URL/JSON mapping | ktor-client-mock MockEngine — no network |
| Unit (S2) | ViewModels (editable carbs, T2 calc, nullable photos) | fake repo/store in composeApp commonTest |

## Slice Split (auto-chain)

| Slice | Components | Verification |
|---|---|---|
| 1 | Catalog+gradle, domain, engine/repo/Ktor, persistence, expect/actual, interfaces, all S1 tests | `make test` (runs `:shared:testDebugUnitTest`, which compiles commonTest) |
| 2 | CameraX/WorkManager impls, UI, ViewModels, navigation, AppGraph, manifest | ViewModel tests + `make build` |

Commit boundary: S1 → PR1 against the feature branch; S2 → PR2 targeting PR1's branch. Per-PR rollback = revert.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or agent process-integration boundary. HTTP calls are read-only data lookups; WorkManager/CameraX are OS-level app features, not agent-execution paths.

## Migration / Rollout

No data migration. Rollout = merge PR1 → `make test` green → merge PR2. Additive feature; no flag needed.

## Open Questions

- [ ] Rounding for `carbohidratos_reales`: store raw Double, display 1 decimal? (recommended)
- [ ] Exact env var names: `USDA_API_KEY` / `GEMINI_API_KEY`? (confirm at apply time)
- [ ] T2 default `porcentaje_consumido` = 100 on open? (recommended)

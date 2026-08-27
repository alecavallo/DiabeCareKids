# Design: Baseline Infrastructure and Build Pipeline

## Technical Approach

Greenfield bootstrap: a zero-host Docker toolchain (INV-007) wraps the entire KMP/Compose build, so hosts need only Docker + Make. Inside the container, a committed Gradle wrapper drives a two-module build (`shared` = domain/data seam, `composeApp` = UI) with all versions pinned in a catalog. One common unit test proves the shared code path; Terraform is scaffolded but plan-only. Strict TDD stays off until the runner lands; the pipeline then makes it activatable via the existing `make test` command path.

## Architecture Decisions

| Decision | Option A (chosen) | Option B (rejected) | Why A |
|---|---|---|---|
| Module layout | `shared` (logic/data) + `composeApp` (UI) | Single `composeApp` module | Leaves seams for DM1 repos/usecases in `shared` without UI coupling |
| Docker image | Single-stage Temurin 17 + cmdline-tools | Multi-stage builder image | SDK layer caches well; multi-stage adds complexity with no build gain |
| Toolchain pins | Kotlin 2.0.21, AGP 8.5.2, Gradle 8.9, CMP 1.6.11, compileSdk 34, JDK 17 | Newest (Kotlin 2.3/AGP 9) | Matches proposal constraint (JDK 17, Kotlin 2.0.x, AGP 8.5+); known-stable combo |
| Wrapper bootstrap | `gradle wrapper` generated in Docker build stage, committed | Require host JDK | Keeps zero-host promise; wrapper.jar is binary (excluded from line budget) |
| Test home | `composeApp/commonTest` asserts `shared` `Greeting.default` | Test in `shared/commonTest` | Satisfies spec verbatim ("composeApp module contains a baseline test") AND proves shared path |
| Terraform | Modular, plan-only, no backend, hashicorp/terraform Docker for validate | Local Terraform install | Zero-host parity; free-tier module skeleton, no live apply |

## Data Flow

Bootstrap & build sequence:

```
make build ──> docker compose build (Temurin+SDK+licenses) ──> docker compose run
    │                                                              │
    └── wrapper bootstrap stage (gradle wrapper 8.9) ────> ./gradlew :composeApp:assembleDebug
                                                                    │
        shared/commonMain ──> composeApp/commonMain(UI) ──> androidMain(MainActivity)
                                                                    │
        composeApp/build/outputs/apk/debug/*.apk <── host bind mount (read-write)
        ~/.gradle (GRADLE_USER_HOME) <── named volume `gradle-cache`
```

## File Changes

| File | Action | Description |
|---|---|---|
| `docker/Dockerfile.android` | Create | Temurin 17, cmdline-tools, `sdkmanager` install (platforms;android-34, build-tools;34.0.0, platform-tools), `yes \| --licenses`; wrapper bootstrap stage |
| `docker/docker-compose.yml` | Create | `kmp-builder` service: repo bind mount + `gradle-cache` volume |
| `Makefile` | Create | `build`/`test`/`lint`/`infra-validate`/`gradle` targets → `docker compose run --rm` |
| `.github/workflows/ci.yml` | Create | PR job: `make test` + `make build` + `make infra-validate` |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml` | Create | Root Gradle; catalog pins; wrapper files generated in-container |
| `shared/build.gradle.kts`, `shared/src/commonMain/kotlin/.../Greeting.kt`, `shared/src/androidMain/.../Platform.android.kt` | Create | Domain seam + expect/actual proof |
| `composeApp/build.gradle.kts`, `commonMain/App.kt`, `commonTest/GreetingTest.kt`, `androidMain/MainActivity.kt`, `AndroidManifest.xml` | Create | Hello World screen + baseline unit test |
| `infra/terraform/versions.tf`, `variables.tf`, `main.tf`, `outputs.tf`, `terraform.tfvars.example`, `modules/firebase-project/{main,variables,outputs}.tf`, `rules/.gitkeep` | Create | Plan-only Google/Firebase skeleton |

## Interfaces / Contracts

- Namespace: `com.diabecarekids.app`; minSdk 24, compileSdk/targetSdk 34.
- `Greeting` (shared/commonMain):

```kotlin
data class Greeting(val text: String = "Hello World")
```

- expect/actual seam (proves later platform work): `expect fun platformName(): String` with `actual` in androidMain.
- Test contract: `Greeting().text == "Hello World"`.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (common) | `GreetingTest` in composeApp/commonTest asserting shared `Greeting.default` | `make test` → `./gradlew :composeApp:testDebugUnitTest :shared:testDebugUnitTest` |
| Build | APK assembly | `make build` → `:composeApp:assembleDebug`, output on host |
| Infra | `terraform fmt -check` + `validate` | `make infra-validate` via hashicorp/terraform image |
| TDD note | Strict TDD `false` (config.yaml) | `test_command: "make test"` already wired — flipping `tdd: true` later needs no pipeline change |

## Threat Matrix

N/A — no dynamic command composition, VCS/PR automation, executable-file classification, or process-integration boundary. Makefile recipes and CI are static; CI runs only `make test`/`build`/`infra-validate`. No rows applicable.

## Migration / Rollout

No migration required. Rollback: delete scaffolding (`docker/`, `composeApp/`, `shared/`, `infra/`, `gradle/`, `.github/workflows/`) and root Gradle files; revert `Makefile`/`.gitignore` edits.

## Open Questions

- [ ] Track `.terraform.lock.hcl` in git for CI determinism (recommend yes, adds ~15 lines)?
- [ ] Ktor 2.3.12 pinned in catalog for future DM1 networking but unused — acceptable dead pin for now?

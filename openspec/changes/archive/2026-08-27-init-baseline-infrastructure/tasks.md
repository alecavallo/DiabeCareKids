# Tasks: Baseline Infrastructure and Build Pipeline

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~400–500 authored (wrapper/lock boilerplate excluded) |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | auto-chain |
| Chain strategy | pending (stacked-to-main if triggered) |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

> Generated `gradlew`/`gradlew.bat` (~340 lines) and `.terraform.lock.hcl` are
> excluded from authored review count per the generated-artifact rule. Authored
> review burden is ~400–500 lines, within the 800-line budget.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Full baseline vertical slice | PR 1 | `make test` | `make build` → debug APK on host | Delete `docker/`, `shared/`, `composeApp/`, `infra/`, `gradle/`, `.github/workflows/`; revert root Gradle files + `.gitignore` |

## Phase 1: Docker Toolchain (INV-007)

- [x] 1.1 Create `docker/Dockerfile.android` — Temurin 17, cmdline-tools, `sdkmanager` install (platforms;android-34, build-tools;34.0.0, platform-tools), `yes | --licenses`. Verify: `make build`.
- [x] 1.2 Create `docker/docker-compose.yml` — `kmp-builder` service: repo bind mount + `gradle-cache` named volume. Verify: `docker compose config`.
- [x] 1.3 Create `Makefile` — `build`/`test`/`lint`/`infra-validate`/`gradle` targets → `docker compose run --rm`. Verify: `make gradle --version`.

## Phase 2: Root Gradle + shared module

- [x] 2.1 Create root `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml` — pins Kotlin 2.0.21, AGP 8.5.2, Gradle 8.9, CMP 1.6.11, compileSdk/targetSdk 34.
- [x] 2.2 Create `shared/` module — `build.gradle.kts`, `Greeting.kt` (`data class Greeting(val text = "Hello World")`), `Platform.android.kt` expect/actual (namespace `com.diabecarekids.app`).
- [x] 2.3 Bootstrap Gradle wrapper 8.9 in-container; commit `gradlew`, `gradlew.bat`, `gradle/wrapper/*`. Verify: `make gradle wrapper --version`.

## Phase 3: composeApp (Hello World + baseline test)

- [x] 3.1 Create `composeApp/build.gradle.kts` — Android + KMP + Compose Multiplatform, `minSdk 24`, depends on `shared`.
- [x] 3.2 Create `App.kt` Hello World Compose screen + `MainActivity.kt` + `AndroidManifest.xml`.
- [x] 3.3 Create `GreetingTest.kt` (commonTest) asserting `Greeting().text == "Hello World"`. Verify: `make test` (→ `:composeApp:testDebugUnitTest :shared:testDebugUnitTest`), `make build` (→ APK on host).

## Phase 4: Terraform skeleton (plan-only)

- [x] 4.1 Create `infra/terraform/{versions,variables,main,outputs}.tf` + `terraform.tfvars.example` — Google/Firebase free-tier, no backend.
- [x] 4.2 Create `modules/firebase-project/{main,variables,outputs}.tf` + `rules/.gitkeep`.
- [x] 4.3 Verify `make infra-validate` (hashicorp/terraform `fmt -check` + `validate`). No live apply.

## Phase 5: CI + ignore cleanup

- [x] 5.1 Create `.github/workflows/ci.yml` — PR job: `make test` + `make build` + `make infra-validate`.
- [x] 5.2 Update `.gitignore` — add `build/`, `.gradle/`, `local.properties`, `.idea/`, `*.apk`, `.terraform/` (keep `gradle/wrapper/`).

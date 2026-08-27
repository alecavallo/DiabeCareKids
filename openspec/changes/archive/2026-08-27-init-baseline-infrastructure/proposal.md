# Proposal: Baseline Infrastructure and Build Pipeline

## Intent

Establish the foundational development, build, and infrastructure baseline for DiabeCareKids. The repository is currently a greenfield skeleton without build tooling, containers, or test runners. Scaffolding the zero-host Docker toolchain (INV-007), Gradle/KMP configuration, and Terraform skeleton with a verifiable "Hello World" vertical slice is required before implementing domain DM1 features and enabling automated TDD verification gates.

## Scope

### In Scope
- Dockerized zero-host toolchain (`docker/Dockerfile.android`, `Makefile` with `build`, `test`, `lint`, `run` targets).
- Gradle root and build configuration (KMP + Compose Multiplatform skeleton, Kotlin version catalog `libs.versions.toml`).
- Minimal KMP application module with a "Hello World" Compose UI screen and an executable unit test proof.
- Baseline Terraform project structure (`infra/terraform/`) for GCP/Firebase provisioning scaffolding.
- CI/CD workflow definition (`.github/workflows/ci.yml`) executing containerized checks.

### Out of Scope
- Domain DM1 features (SOS trigger, meal logging T0/T2, USDA/Gemini carb estimation, PDF export, reminders).
- Production Firebase/GCP resource provisioning or cloud deployments.
- iOS platform targets and native iOS toolchain configuration.

## Capabilities

### New Capabilities
- `baseline-infrastructure`: Zero-host Docker toolchain, KMP Gradle build pipeline, and executable Hello-World vertical slice.

### Modified Capabilities
None

## Approach

Scaffold the zero-host build container containing JDK 17, Android command-line tools, and SDK licenses. Wire root Gradle files with version catalogs for Kotlin 2.0+ and Compose Multiplatform. Implement a minimal shared module with a single Compose screen and an automated test executed via `make test` inside Docker to prove pipeline correctness. Add a modular Terraform skeleton targeting Google Cloud / Firebase.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `docker/` | New | `Dockerfile.android` containing Android SDK, cmdline-tools, and Gradle dependencies |
| `Makefile` | New | Container-wrapped developer commands (`make build`, `make test`, `make lint`) |
| `gradle/` & `build.gradle.kts` | New | Root build script, wrapper, and `gradle/libs.versions.toml` version catalog |
| `composeApp/` | New | Minimal KMP/Compose entrypoint, Hello World UI, and baseline unit test |
| `infra/terraform/` | New | Baseline Terraform configuration and environment structure |
| `.github/workflows/` | New | CI pipeline running Dockerized validation on pull requests |

## Decision Gaps & Assumptions

- **Package & Namespace**: Default to `com.diabecarekids.app` for Android application ID and namespace.
- **SDK Targets**: Default to `minSdk = 24`, `compileSdk = 34`, `targetSdk = 34`.
- **Toolchain Versions**: JDK 17 (Temurin), Gradle 8.7+, AGP 8.5+, Kotlin 2.0.x.
- **Docker License Acceptance**: Accept Android SDK licenses during Docker image build automatically.
- **Terraform Constraints**: Scope infra config strictly to Google Cloud Free Tier / Firebase Spark plans.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Android SDK license acceptance in Docker | Med | Explicitly accept standard license hashes in `Dockerfile.android` |
| First-time Gradle dependency download latency | High | Mount persistent Gradle cache volume in Docker container |
| Terraform drift on unprovisioned greenfield state | Low | Keep Terraform configs declarative and modular without executing live applies |

## Rollback Plan

Delete generated scaffolding directories (`docker/`, `composeApp/`, `infra/`, `gradle/`) and revert root configuration files (`Makefile`, `build.gradle.kts`, `settings.gradle.kts`).

## Dependencies

- Host requirements: Docker Engine 24+ and GNU Make. No local JDK or Android SDK required on host.

## Success Criteria

- [ ] `make test` executes successfully inside Docker and passes the baseline unit test.
- [ ] `make build` produces an Android debug APK without host SDK dependencies.
- [ ] Terraform syntax validation (`terraform validate` / `terraform fmt`) passes on `infra/terraform/`.
- [ ] Total changeset remains within the 800-line review budget.

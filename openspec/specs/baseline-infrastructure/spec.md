# Delta for baseline-infrastructure

## ADDED Requirements

### Requirement: Zero-Host Docker Toolchain (INV-007)

The system MUST provide a fully containerized build environment containing JDK 17, the Android SDK, and auto-accepted SDK licenses. Host machines MUST NOT be required to install native build toolchains. A `Makefile` MUST wrap all developer commands (`build`, `test`, `lint`) to execute inside the container, mapping persistent volume caches for Gradle to eliminate repeated download latency.

#### Scenario: Developer builds the application
- GIVEN a host machine with only Docker and GNU Make installed
- WHEN the developer executes `make build`
- THEN the system runs a Docker container
- AND compiles the KMP/Compose application inside the container
- AND outputs a debug APK to the host filesystem

#### Scenario: Developer runs the test suite
- GIVEN a host machine with Docker and GNU Make
- WHEN the developer executes `make test`
- THEN the system runs a Docker container
- AND executes the Gradle unit tests inside the container
- AND reports success without host SDK dependencies

### Requirement: KMP and Compose Multiplatform Gradle Pipeline

The system MUST define a root Gradle build using Kotlin DSL (`build.gradle.kts`) and a Kotlin version catalog (`libs.versions.toml`) for dependency management (Kotlin 2.0+, Compose Multiplatform, AGP 8.5+). It MUST define a `composeApp` module containing a "Hello World" Compose screen and at least one passing baseline unit test.

#### Scenario: Executing the baseline unit test
- GIVEN the `composeApp` module contains a baseline test
- WHEN `make test` is executed
- THEN the unit test proves the common Kotlin code path executes successfully

#### Scenario: Assembling the Android UI
- GIVEN the `composeApp` module contains a "Hello World" screen
- WHEN `make build` is executed
- THEN the shared Compose UI compiles into an Android application artifact

### Requirement: CI/CD Pull Request Validation

The system MUST include a GitHub Actions workflow (`.github/workflows/ci.yml`) that automates validation on pull requests. The workflow MUST use the zero-host Docker toolchain to ensure CI parity with local development environments.

#### Scenario: Code is pushed to a pull request
- GIVEN an active pull request
- WHEN new commits are pushed
- THEN the CI/CD pipeline triggers
- AND runs `make test` inside the containerized toolchain
- AND reports a passing status back to GitHub

### Requirement: Terraform Infrastructure Skeleton

The system MUST provide a baseline Terraform configuration structure (`infra/terraform/`) for declarative Google Cloud and Firebase resource definition. The configuration MUST be syntactically valid and plan-able without triggering a live infrastructure apply during this change.

#### Scenario: Validating Terraform configuration
- GIVEN the baseline infrastructure files are present in `infra/terraform/`
- WHEN `terraform validate` (or `make infra-validate` depending on Makefile wrappers) is executed
- THEN the configuration passes syntax and structural validation
- AND no remote resources are provisioned or altered
# Tasks: CI Auto Release on Main Push

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~90 (`release.yml` ~60, `Makefile` ~10, `build.gradle.kts` ~20) |
| Configured line budget | 800 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | auto-chain |
| Chain strategy | pending (stacked-to-main pre-resolved, not engaged) |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Signed release build + auto-release on main | Single PR | `make assemble-release VERSION_CODE=1 VERSION_NAME=0.1.0` | `docker compose -f docker/docker-compose.yml run --rm kmp-builder ./gradlew :composeApp:assembleRelease` | `git revert` — 3 files; `signingConfig` inert without `-P` |

## Phase 1: Foundation (build config)

- [x] 1.1 `composeApp/build.gradle.kts` `defaultConfig`: inject `versionCode`/`versionName` from `-PversionCode`/`-PversionName` via `findProperty(...) as? String` with safe defaults (`1` / `"1.0"`). Spec: REQ-3. Verify: `make build` unchanged.
- [x] 1.2 `composeApp/build.gradle.kts`: add opt-in `signingConfigs { if (findProperty("releaseStorePath") != null) create("releaseCi") { ... } }` reading storePassword/keyAlias/keyPassword from `-P` props. Spec: REQ-4.
- [x] 1.3 `composeApp/build.gradle.kts` `buildTypes.release`: keep `isMinifyEnabled = false`; set `signingConfig = signingConfigs.findByName("releaseCi")` (null → unsigned). Spec: REQ-4, REQ-5.
- [x] 1.4 `Makefile`: add `VERSION_CODE ?= 1`, `VERSION_NAME ?= 1.0-local`, `SIGNING_ARGS ?=`; add `assemble-release` target running `$(COMPOSE) run --rm $(SERVICE) ./gradlew :composeApp:assembleRelease -PversionCode=$(VERSION_CODE) -PversionName=$(VERSION_NAME) $(SIGNING_ARGS)`; add to `.PHONY`. Spec: REQ-2.

## Phase 2: Core workflow

- [x] 2.1 `.github/workflows/release.yml`: `on: push: branches: [main]`, `permissions: contents: write`, `concurrency: { group: release-main, cancel-in-progress: false }`. Spec: REQ-1.
- [x] 2.2 `actions/checkout@v4` with `fetch-depth: 0`; derive `COUNT=$(git rev-list --count HEAD)`, `SHA7=$(git rev-parse --short=7 HEAD)`, `PASS=$(echo -n "$GITHUB_SHA-${{ github.run_id }}" | sha256sum | cut -c1-32)`. Spec: REQ-3.
- [x] 2.3 `keytool -genkeypair -keystore ci-release.keystore -alias ci-release -keyalg RSA -keysize 2048 -validity 10000 -storepass $PASS -keypass $PASS` (job-scoped, never committed). Spec: REQ-4, REQ-5.
- [x] 2.4 Run `make assemble-release VERSION_CODE=$COUNT VERSION_NAME=0.1.$COUNT+$SHA7 SIGNING_ARGS="-PreleaseStorePath=ci-release.keystore -PreleaseStorePassword=$PASS -PreleaseKeyAlias=ci-release -PreleaseKeyPassword=$PASS"`. Spec: REQ-2, REQ-4.
- [x] 2.5 Verify `apksigner verify --print-certs composeApp/build/outputs/apk/release/composeApp-release.apk` in `kmp-builder`; RED-fail if only `-unsigned` exists. Spec: REQ-4.
- [x] 2.6 Publish via `softprops/action-gh-release@v2` SHA-pinned: tag `v$COUNT-$SHA7`, asset renamed `DiabeCareKids-v$COUNT-$SHA7.apk`, notes from `git log`. Spec: REQ-1, REQ-3.

## Phase 3: Verification

- [x] 3.1 RED (threat: push-state/tag creation): re-running same commit fails loudly on existing tag `v$COUNT-$SHA7` (no overwrite); rapid merges serialize via concurrency. Spec: REQ-1, REQ-3.
- [x] 3.2 `aapt dump badging` in container asserts minSdk 24 / targetSdk 34 / universal (no ABI split). Spec: REQ-4.
- [x] 3.3 Fallback: `make build` and keystore-less `make assemble-release` both succeed unsigned (no `-P` signing props). Spec: REQ-5.

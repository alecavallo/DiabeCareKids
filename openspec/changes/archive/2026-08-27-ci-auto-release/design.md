# Design: CI Auto Release on Main Push

## Technical Approach

Single-job workflow on push to `main`: derive a commit-identity version, generate a throwaway keystore, build the **signed, installable release APK** in `kmp-builder` via `make assemble-release` (INV-007), publish GitHub Release `v<count>-<sha7>`. No repo/Actions secrets; `GITHUB_TOKEN`, `contents: write`.

## Architecture Decisions

| Decision | Option A (chosen) | Option B | Rationale |
|---|---|---|---|
| Job layout | Single `release` job | build+release jobs | Split adds steps + cold start, no gain. |
| Version | `git rev-list --count HEAD` + 7-char SHA | `GITHUB_RUN_NUMBER` | Deterministic, monotonic; run numbers drift on re-runs. |
| Publisher | `softprops/action-gh-release@v2` SHA-pinned | `gh release create` | One step covers tag+release+asset+notes. |
| APK flavor | Universal, no ABI splits | Per-ABI splits | One asset, API 24–34; splits deferred. |
| Signing | Throwaway keystore via `keytool` (JDK 17 in builder) | committed keystore / secret / unsigned | Installable for $0, nothing stored; new signature per release — fine for sideload. |
| Signing plumbing | Optional `-P` props via `SIGNING_ARGS` make var | keystore mandatory | `signingConfig` only when `releaseStorePath` set → local/debug builds unaffected; CI opts in. |

## Data Flow

```
push main ─▶ checkout(fetch-depth:0) ─▶ derive COUNT+sha7+PASS=sha256(SHA+run_id)
  ─▶ keytool -genkeypair → ci-release.keystore
  ─▶ make assemble-release VERSION_CODE/NAME SIGNING_ARGS="-PreleaseStorePath/Password -PreleaseKeyAlias/Password"
       └ gradlew :composeApp:assembleRelease -P...
  ─▶ composeApp/build/outputs/apk/release/composeApp-release.apk
  ─▶ softprops: tag v<COUNT>-<sha7>, asset DiabeCareKids-v<COUNT>-<sha7>.apk
```

Tags don't re-trigger (`branches: [main]` filter) — no loop guard.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `.github/workflows/release.yml` | Create | Trigger, permissions, concurrency, derive version+password, keytool step, build, publish |
| `Makefile` | Modify | `assemble-release` target; optional `SIGNING_ARGS` (empty → unsigned) |
| `composeApp/build.gradle.kts` | Modify | `defaultConfig` reads `-PversionCode/-PversionName`; opt-in release `signingConfig` |

## Interfaces / Contracts

```yaml
on: { push: { branches: [main] } }
permissions: { contents: write }
concurrency: { group: release-main, cancel-in-progress: false }
# checkout@v4, fetch-depth:0 — full history for rev-list --count
# PASS=$(echo -n "$GITHUB_SHA-${{ github.run_id }}" | sha256sum | cut -c1-32)
# keytool -genkeypair -keystore ci-release.keystore -alias ci-release
#   -keyalg RSA -keysize 2048 -validity 10000 -storepass/-keypass "$PASS"
```

```make
VERSION_CODE ?= 1
VERSION_NAME ?= 1.0-local
SIGNING_ARGS ?=
assemble-release:
	$(COMPOSE) run --rm $(SERVICE) ./gradlew :composeApp:assembleRelease \
		-PversionCode=$(VERSION_CODE) -PversionName=$(VERSION_NAME) $(SIGNING_ARGS)
```

```kotlin
// defaultConfig
versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
versionName = (project.findProperty("versionName") as? String) ?: "1.0"

// android { } — opt-in release signing (no props → unsigned)
signingConfigs {
    if (project.findProperty("releaseStorePath") != null)
        create("releaseCi") {
            storeFile = file(project.findProperty("releaseStorePath") as String)
            storePassword = project.findProperty("releaseStorePassword") as? String
            keyAlias = project.findProperty("releaseKeyAlias") as? String
            keyPassword = project.findProperty("releaseKeyPassword") as? String
        }
}
buildTypes { getByName("release") {
    isMinifyEnabled = false
    signingConfig = signingConfigs.findByName("releaseCi")  // null → unsigned
} }
```

- **Version**: `versionCode=<count>`, `versionName=0.1.<count>+<sha7>`, tag/asset `v<count>-<sha7>`.
- **APK**: signed → `composeApp-release.apk`; fallback `-unsigned.apk`. Workflow RED-fails if `-unsigned`-only, uploads signed with explicit asset name.
- **Keystore**: ephemeral, job-scoped, never committed; password derived from commit+run identity.

## Testing Strategy

| Layer | What | Approach |
|-------|------|-----------|
| Build | Signed release builds | `make assemble-release` + `SIGNING_ARGS`; failure blocks |
| Signing | APK signed/installable | `apksigner verify --print-certs`; RED: `-unsigned`-only fails |
| Version | Unique, well-formed | Fail-fast asserts in derive step |
| Release | No collision/overwrite | Same-commit re-run fails cleanly |
| APK contract | minSdk 24 / targetSdk 34 / universal | `aapt dump badging` in container |
| Fallback | No keystore → builds pass | `make build` + keystore-less `assemble-release` succeed unsigned |

## Threat Matrix

| Boundary | Applicability | RED tests |
|---|---|---|
| Documentation-like paths | N/A — fixed make/gradle commands only | — |
| Git repository selection | N/A — pinned checkout@v4 | — |
| Commit state | N/A — read-only rev-list | — |
| Push state (tag creation) | Applicable — creates `refs/tags/v<n>-<sha7>` | Immutable commit identity, serialized; re-run fails loudly, never overwrites. RED: rapid-merge + duplicate-release |
| PR commands | N/A — no PR automation | — |

Keytool: fixed invocation — no new boundary.

## Risks & Tradeoffs

1. **New signature per release**: cross-signer upgrades rejected (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); uninstall first. Accepted for dev distribution; Play later needs persistent keystore.
2. **Ephemeral-runner build cost**: image + deps rebuild each run (~minutes); accepted. Future: GHCR builder image; ci.yml duplicates debug cost.
3. Tag churn per merge — reversible via rollback plan.

## Non-Goals

No persistent keystore, no Play/AAB, no secrets in repo/Actions secrets, no committed keystore.

## Migration / Rollout

No data migration. First merge self-triggers signed `v6-<sha7>`. Rollback: delete `release.yml` + Makefile target; `signingConfig` inert without `-P`; `gh release delete` for strays.

## Open Questions

None blocking. Follow-ups: spec delta says "unsigned" in *Publish Universal APK* → MODIFIED "signed" (sdd-spec); proposal superseded (sdd-propose); custom changelog.

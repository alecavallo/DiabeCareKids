# Proposal: CI Auto Release on Main Push

## Intent

Deliver continuous installable Android builds on every merge to `main` without manual intervention. Provide automated GitHub Releases with attached APK artifacts honoring the zero-host Docker toolchain (INV-007) and supporting the last 7 Android versions.

## Scope

### In Scope
- Add `assemble-release` target to `Makefile` invoking `./gradlew :composeApp:assembleRelease` inside Docker (`kmp-builder`).
- Create `.github/workflows/release.yml` triggered on `push: branches: [main]`.
- Automate monotonic version/tag generation (e.g. `v0.1.<run_number>` or commit count) to avoid release collisions.
- Package and attach the universal release APK to each generated GitHub Release.
- Leverage standard `GITHUB_TOKEN` for permissions (`contents: write`).

### Out of Scope
- Keystore management, release signing configs, or production signing secrets (deferred).
- Google Play Store / AAB distribution or publishing APIs.
- Non-Android multiplatform releases (iOS/desktop).

## Capabilities

### New Capabilities
- `release-automation`: Continuous automated GitHub Release generation and APK artifact publication on every push to `main` via zero-host Docker toolchain.

### Modified Capabilities
- `baseline-infrastructure`: Add `assemble-release` target to `Makefile` for zero-host release artifact compilation.

## Approach

1. **Makefile**: Add `assemble-release` target running containerized `./gradlew :composeApp:assembleRelease`.
2. **Workflow**: `.github/workflows/release.yml` triggers on `push` to `main`, checks out repo, generates unique tag, builds APK via `make assemble-release`, and uses `softprops/action-gh-release` or `gh release create` to publish the release with the output APK attached.
3. **Compatibility**: Target SDK 34 with min SDK 24 natively covers Android 7.0 through 14+ (last 7+ versions).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `Makefile` | Modified | Add `assemble-release` target |
| `.github/workflows/release.yml` | New | Automated release workflow on push to `main` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Unsigned APK sideload warning | Med | Document testing install path; plan keystore signing in dedicated change |
| CI build duration & resource usage | Low | Reuse existing containerized Gradle caching pattern |
| Release tag collisions on rapid pushes | Low | Use monotonic run number / commit count for unique semver tags |

## Rollback Plan

Revert `.github/workflows/release.yml` and the `assemble-release` target in `Makefile`.

## Dependencies

- Existing Docker toolchain (`docker/Dockerfile.android`, `docker/docker-compose.yml`).
- Repository default `GITHUB_TOKEN` with `contents: write` permissions.

## Success Criteria

- [ ] `make assemble-release` executes containerized release build producing release APK.
- [ ] Workflow triggers on push to `main` and successfully builds the release APK inside Docker.
- [ ] GitHub Release is created with an automated unique tag and release APK attached.

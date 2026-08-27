# Delta for release-automation

## ADDED Requirements

### Requirement: Trigger Release on Main Push

The system MUST execute the automated release workflow exclusively when commits are pushed to the `main` branch, ensuring exactly one release per merge and enforcing a concurrency guard.

#### Scenario: Code is merged to main
- GIVEN a pull request is merged into the `main` branch
- WHEN the push event is received
- THEN the continuous release workflow is triggered
- AND no release workflow is triggered on pull request branches
- AND a concurrency guard ensures only one release process runs at a time
- AND exactly one release is generated per merge

### Requirement: Zero-Host Docker Build

The system MUST compile the release artifact inside the `kmp-builder` Docker container using `make assemble-release` (INV-007 toolchain).

#### Scenario: Compiling the release APK
- GIVEN the release workflow has started
- WHEN the compilation step runs
- THEN it executes `./gradlew :composeApp:assembleRelease` inside the Docker container
- AND no host SDK dependencies are required or used

### Requirement: Unique Automated Versioning

The system MUST generate a unique monotonic version tag (commit-identity versioning) for each release run to prevent overwriting prior artifacts.

#### Scenario: Rapid merges
- GIVEN multiple rapid merges to the `main` branch
- WHEN the workflow runs sequentially
- THEN it automatically generates a unique commit-identity tag (e.g., run number or short SHA)
- AND creates the release without tag collision

### Requirement: Publish Universal APK

The system MUST produce a signed universal APK installable on Android 7.0 (API 24) through 14+ (API 34) and attach it to the GitHub Release.

#### Scenario: Artifact distribution
- GIVEN a throwaway keystore is generated in the Docker builder
- AND Gradle signing is opted-in via -P properties
- WHEN the containerized build succeeds and the publishing step executes
- THEN a SIGNED universal APK is attached to the GitHub Release
- AND the APK is installable on Android 7.0–14+ (minSdk 24 / targetSdk 34)

### Requirement: Release Constraints (Non-Goals)

The automated release workflow MUST enforce strict security and scope limits.

#### Scenario: Scope limits
- GIVEN the release workflow executes
- THEN there is no persistent or production keystore used
- AND there are no secrets stored in the repository or GitHub Actions
- AND there is no Google Play Store or AAB submission
- AND there is no manual git tagging required

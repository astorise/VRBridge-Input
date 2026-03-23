# ci-cd-pipeline Specification

## Requirements

### Requirement: Automated APK Build on Push
The CI pipeline SHALL automatically build the debug APK whenever code is pushed to or a pull request targets the `main` branch.

#### Scenario: Developer pushes code to main
- WHEN a commit is pushed to the `main` branch
- THEN the GitHub Actions `build-apk.yml` workflow runs `./gradlew assembleDebug` and uploads the resulting APK as a downloadable artifact

#### Scenario: Pull request is opened against main
- WHEN a pull request targeting `main` is opened or updated
- THEN the `build-apk.yml` workflow runs and the APK artifact is available for download on the workflow run

### Requirement: Manual Unit Test Execution
The CI pipeline SHALL allow developers to manually trigger the unit test suite via `workflow_dispatch`.

#### Scenario: Developer triggers tests manually
- WHEN a developer triggers the `manual-unit-tests.yml` workflow via the GitHub Actions UI
- THEN the workflow runs `./gradlew testDebugUnitTest` and reports success or failure

### Requirement: Testable Key Mapping Logic
The application's AZERTY-to-QWERTY key mapping SHALL be exercisable as a local JVM unit test without requiring Bluetooth hardware or an Android device.

#### Scenario: Unit test verifies AZERTY key remapping
- WHEN `AzertyToQwertyMapperTest` is executed in a standard JVM environment
- THEN it asserts that AZERTY-specific keys (A, Z, Q, W, M) produce the correct QWERTY HID usage codes

### Requirement: Testable HID Descriptor Configuration
The composite HID descriptor byte array SHALL be verifiable as a local JVM unit test.

#### Scenario: Unit test validates HID descriptor
- WHEN `HidReportConfigTest` is executed
- THEN it asserts the `COMPOSITE_HID_DESCRIPTOR` has the expected byte array length and critical index values

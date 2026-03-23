# Implementation Tasks

- [x] **Task 1: Setup Testing Dependencies**
  - Add `junit` and `mockk` to the `app/build.gradle.kts` dependencies (`testImplementation`).

- [x] **Task 2: Refactor for Testability**
  - Create the `IBluetoothSender` interface.
  - Modify `InputInterceptorActivity` or the data processing classes to accept `IBluetoothSender` instead of direct Bluetooth API calls.

- [x] **Task 3: Write Key Mapper Unit Tests**
  - Create `AzertyToQwertyMapperTest.kt` in the `src/test/` directory.
  - Write test cases for standard keys, AZERTY-specific keys (A/Q, Z/W, M), and numbers.

- [x] **Task 4: Write HID Descriptor Unit Tests**
  - Create `HidReportConfigTest.kt`.
  - Validate the `COMPOSITE_HID_DESCRIPTOR` byte array length and specific critical indices.

- [x] **Task 5: Create Automated Build Workflow**
  - Create `.github/workflows/build-apk.yml`.
  - Configure the build steps and artifact upload for the APK.

- [x] **Task 6: Create Manual Test Workflow**
  - Create `.github/workflows/manual-unit-tests.yml`.
  - Configure the `workflow_dispatch` trigger and the `./gradlew testDebugUnitTest` command.
# Proposal: CI/CD Pipeline & Core Logic Unit Testing

## What
Introduce a robust unit testing suite for the application's core logic and establish GitHub Actions workflows. The CI/CD pipeline will feature an automated workflow for building the Android APK and a manual workflow for running unit tests.

## Why
Since Bluetooth HID communication cannot be easily emulated or tested in a Docker/CI environment, we must decouple the hardware interaction from the data processing logic. This ensures our core business logic (e.g., AZERTY to QWERTY remapping and HID Report array generation) is flawless. Automated APK builds will streamline the testing process for sideloading onto the Meta Quest 3 via SideQuest.

## Requirements
- **Testing Framework:** JUnit 4/5 and MockK (for Kotlin mocking).
- **GitHub Actions (Build):** Automatically build the debug APK on `push` and `pull_request` to the `main` branch and upload it as a downloadable artifact.
- **GitHub Actions (Tests):** Allow developers to manually trigger the unit tests suite using `workflow_dispatch`.
- **Architecture Refactoring:** Introduce a `BluetoothSender` interface to abstract `BluetoothHidDevice` calls, making the mappers and interceptors 100% testable.
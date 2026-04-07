# foreground-service-persistence Specification

## Purpose
Keep the Bluetooth HID connection alive in the background using an Android Foreground Service, with resilience against aggressive OEM battery management (e.g., Nubia/ZTE ROM).

## Requirements

### Requirement: Maintain Bluetooth Connection in Background
The application SHALL use an Android Foreground Service of type `connectedDevice` to keep the Bluetooth HID connection alive when the app is minimized or the screen is off.

#### Scenario: User minimizes the app
- WHEN the user presses Home or switches to another app
- THEN `HidForegroundService` continues running and the Bluetooth HID connection to the Quest 3 remains active

#### Scenario: Screen turns off
- WHEN the Android screen turns off
- THEN the foreground service prevents the system from terminating the Bluetooth connection

### Requirement: Display a Persistent Notification
The application SHALL show a persistent notification while `HidForegroundService` is running, reflecting the current connection state.

#### Scenario: Service starts (disconnected)
- WHEN the foreground service is started
- THEN a notification is displayed with "Waiting for Quest 3 connection…"

#### Scenario: Quest 3 connects
- WHEN the HID host connects
- THEN the notification text is updated to "Connected to {device name}"

#### Scenario: Quest 3 disconnects
- WHEN the HID host disconnects
- THEN the notification text reverts to "Waiting for Quest 3 connection…"

### Requirement: Crash Resilience
The application SHALL install a global uncaught exception handler that writes crash stacktraces to `filesDir/crash.log`, enabling post-mortem diagnosis on devices that suppress logcat for third-party apps (e.g., Nubia Z60 Ultra).

#### Scenario: Uncaught exception occurs
- WHEN an unhandled exception crashes the app
- THEN the stacktrace is written to `files/crash.log` in the app's internal storage before the process terminates

### Requirement: START_STICKY Restart Policy
The service SHALL use `START_STICKY` return value from `onStartCommand` so that Android restarts the service if it is killed by the system.

#### Scenario: System kills the service
- WHEN the Android OS kills the foreground service due to memory pressure
- THEN the system automatically restarts the service

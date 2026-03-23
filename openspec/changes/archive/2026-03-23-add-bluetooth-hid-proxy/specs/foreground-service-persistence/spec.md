# Spec: Foreground Service Persistence

## ADDED Requirements

### Requirement: Maintain Bluetooth Connection in Background
The application SHALL use an Android Foreground Service of type `connectedDevice` to keep the Bluetooth HID connection alive when the app is minimized or the screen is off.

#### Scenario: User minimizes the app
- WHEN the user presses Home or switches to another app
- THEN `HidForegroundService` continues running and the Bluetooth HID connection to the Quest 3 remains active

#### Scenario: Screen turns off
- WHEN the Android screen turns off
- THEN the foreground service prevents the system from terminating the Bluetooth connection

### Requirement: Display a Persistent Notification
The application SHALL show a persistent notification while `HidForegroundService` is running, as required by Android 14+ foreground service rules.

#### Scenario: Service starts
- WHEN the foreground service is started
- THEN a notification is displayed indicating the HID proxy is active

#### Scenario: Service stops
- WHEN the user disconnects or stops the service
- THEN the persistent notification is removed

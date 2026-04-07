# bluetooth-hid-connection Specification

## Purpose
Establish and maintain a Bluetooth HID Device connection between the Android phone and the Meta Quest 3, registering the phone as a composite keyboard+mouse HID device.

## Requirements

### Requirement: Establish HID Device Profile
The application SHALL register itself as a Bluetooth HID device using `BluetoothProfile.HID_DEVICE` and connect to the Meta Quest 3 as the host.

#### Scenario: User initiates connection
- WHEN the user launches the app and grants Bluetooth permissions (BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE)
- THEN the app obtains a `BluetoothHidDevice` profile proxy, registers with the composite HID descriptor (keyboard Report ID 1 + mouse Report ID 2), and waits for the Quest 3 to connect

#### Scenario: Host connects
- WHEN the Meta Quest 3 accepts the HID device connection
- THEN `onConnectionStateChanged` fires with `STATE_CONNECTED`, the app stores the connected host, updates the notification and UI status bar (green, bottom of screen)

### Requirement: Handle Connection State Changes
The application SHALL handle `BluetoothHidDevice.Callback` state transitions and surface connection status to the user via a status bar at the bottom of the screen.

#### Scenario: Connection is established
- WHEN the Quest 3 connects
- THEN the status bar turns green with the device name, and the virtual keyboard EditText becomes visible (if no physical keyboard is attached)

#### Scenario: Connection is lost
- WHEN the Bluetooth connection to the Quest 3 is interrupted
- THEN the app sets the status bar to red ("Disconnected"), hides the virtual keyboard EditText, and stops sending HID reports

### Requirement: Reply to GET_REPORT Requests
The application SHALL respond to incoming HID `GET_REPORT` requests from the host with zero-filled reports of the requested buffer size.

#### Scenario: Host requests a report
- WHEN the Quest 3 sends a GET_REPORT request
- THEN `onGetReport` replies with a zero-filled `ByteArray` of the requested size

### Requirement: Diagnostic Logging
The application SHALL log critical HID lifecycle events (adapter state, profile proxy, registerApp result, connection state changes, report send results) to both Android `Log.d` and an internal file (`files/vrbridge.log`) to support debugging on devices that suppress logcat for third-party apps.

#### Scenario: App starts on a device with restricted logcat
- WHEN the app initializes the HID service
- THEN each step (adapter check, getProfileProxy, registerApp, onAppStatusChanged, onConnectionStateChanged) is logged to `filesDir/vrbridge.log` with timestamps

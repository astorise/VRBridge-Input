# Spec: Bluetooth HID Connection

## ADDED Requirements

### Requirement: Establish HID Device Profile
The application SHALL register itself as a Bluetooth HID device using `BluetoothProfile.HID_DEVICE` and connect to the Meta Quest 3 as the host.

#### Scenario: User initiates connection
- WHEN the user launches the app and grants Bluetooth permissions
- THEN the app registers a HID device app with a Composite Report Descriptor (Keyboard + Mouse) and waits for the Quest 3 to connect

#### Scenario: Host connects
- WHEN the Meta Quest 3 accepts the HID device connection
- THEN the app transitions to a connected state and enables input forwarding

### Requirement: Handle Connection State Changes
The application SHALL handle `BluetoothHidDevice.Callback` state transitions and surface connection status to the user.

#### Scenario: Connection is lost
- WHEN the Bluetooth connection to the Quest 3 is interrupted
- THEN the app updates its UI to reflect the disconnected state and stops sending HID reports

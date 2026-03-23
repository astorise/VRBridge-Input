# Spec: Keyboard HID Proxy

## ADDED Requirements

### Requirement: Intercept Physical Keyboard Events
The application SHALL intercept all physical keyboard `KeyEvent`s via `dispatchKeyEvent` in the foreground activity.

#### Scenario: Key press on physical keyboard
- WHEN the user presses a key on a physical keyboard connected to the phone
- THEN the app captures the `KeyEvent` before it reaches the system and processes it for remapping

### Requirement: Remap AZERTY Keys to QWERTY HID Usage Codes
The application SHALL translate Android `KeyEvent.KEYCODE_*` values from an AZERTY layout to the corresponding USB HID Usage IDs (Page 0x07) expected by a QWERTY host.

#### Scenario: AZERTY-specific key is pressed
- WHEN the user presses an AZERTY key (e.g., A, Z, Q, W, M, or number row keys)
- THEN `AzertyToQwertyMapper` returns the QWERTY HID usage code for that key

#### Scenario: Common key is pressed
- WHEN the user presses a key that is identical in AZERTY and QWERTY (e.g., Space, Enter, Backspace)
- THEN the mapper returns the standard HID usage code unchanged

### Requirement: Send Keyboard HID Reports
The application SHALL send 8-byte keyboard HID reports (Report ID 1) via `BluetoothHidDevice.sendReport`.

#### Scenario: Key is held down
- WHEN one or more keys are pressed simultaneously (up to 6 keys)
- THEN the app sends a report with the modifier byte and up to 6 key usage codes populated

#### Scenario: All keys are released
- WHEN all keys are released
- THEN the app sends an empty keyboard report to signal key-up to the host

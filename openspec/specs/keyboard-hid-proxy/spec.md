# keyboard-hid-proxy Specification

## Purpose
Translate physical keyboard events and virtual keyboard input into HID keyboard reports forwarded to the Quest 3 over Bluetooth, with automatic switching between physical and virtual keyboard modes.

## Requirements

### Requirement: Intercept Physical Keyboard Events
The application SHALL intercept all physical keyboard `KeyEvent`s via `dispatchKeyEvent` in the foreground activity, except when the virtual keyboard EditText has focus and no physical keyboard is connected.

#### Scenario: Key press on physical keyboard
- WHEN the user presses a key on a physical keyboard connected to the phone
- THEN the app captures the `KeyEvent` before it reaches the system and processes it for remapping

#### Scenario: Virtual keyboard is active
- WHEN no physical keyboard is connected AND the EditText input field has focus
- THEN `dispatchKeyEvent` passes key events through to the system (handled via TextWatcher instead)

### Requirement: Remap AZERTY Keys to QWERTY HID Usage Codes
The application SHALL translate Android `KeyEvent.KEYCODE_*` values from an AZERTY layout to the corresponding USB HID Usage IDs (Page 0x07) expected by a QWERTY host.

#### Scenario: AZERTY-specific key is pressed
- WHEN the user presses an AZERTY key (e.g., A↔Q, Z↔W swaps)
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

### Requirement: Virtual Keyboard Input via EditText
The application SHALL provide an `EditText` field that appears when the Quest 3 is connected and no physical keyboard is detected, allowing the user to type via the Android virtual keyboard.

#### Scenario: Virtual keyboard input field appears
- WHEN the Quest 3 is connected AND no physical keyboard is attached (`Configuration.KEYBOARD_QWERTY` not detected)
- THEN an EditText field becomes visible above the status bar with a hint text

#### Scenario: User types a character via virtual keyboard
- WHEN a character is added to the EditText via the Android IME
- THEN the `TextWatcher` detects the new character, maps it to a HID usage code via `charToHidUsage()`, and sends a key-down + key-up report pair (with Shift modifier if needed)

#### Scenario: User deletes a character via virtual keyboard
- WHEN a character is removed from the EditText
- THEN the TextWatcher sends a Backspace HID keystroke (usage 0x2A) for each deleted character

#### Scenario: Physical keyboard is plugged in
- WHEN a physical keyboard with `KEYBOARD_TYPE_ALPHABETIC` is connected
- THEN the EditText field is hidden and `dispatchKeyEvent` resumes direct interception

### Requirement: Detect Physical Keyboard Hot-Plug
The application SHALL detect physical keyboard connection/disconnection via `InputManager.InputDeviceListener` and `Configuration.KEYBOARD_QWERTY`.

#### Scenario: Physical keyboard is connected
- WHEN a non-virtual device with `SOURCE_KEYBOARD` and `KEYBOARD_TYPE_ALPHABETIC` is added
- THEN the virtual keyboard EditText is hidden

#### Scenario: Physical keyboard is disconnected
- WHEN the physical keyboard is removed
- THEN the app re-scans and shows the virtual keyboard EditText if the Quest 3 is still connected

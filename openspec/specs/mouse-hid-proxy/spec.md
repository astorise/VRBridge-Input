# mouse-hid-proxy Specification

## Purpose
Translate touchscreen gestures, USB mouse input, and physical mouse events into HID mouse reports forwarded to the Quest 3 over Bluetooth, with automatic prioritization of USB mice over the virtual trackpad.

## Requirements

### Requirement: Forward Touch Events as Trackpad Input
The application SHALL intercept touch events on the UI via `onTouchEvent` and translate them into relative X/Y mouse HID reports, only when no external USB mouse is connected.

#### Scenario: User swipes on the trackpad area
- WHEN the user drags a finger across the touch screen area
- AND no external USB mouse is connected
- THEN the app calculates the delta X/Y movement and sends a 4-byte mouse HID report (Report ID 2)

#### Scenario: User taps on the trackpad area
- WHEN the user performs a short tap gesture (< 200ms, < 10px movement)
- AND no external USB mouse is connected
- THEN the app sends a left-click button report followed by a button-release report

#### Scenario: Trackpad is disabled when USB mouse is connected
- WHEN an external USB mouse is connected
- THEN touch events on the screen are consumed but not forwarded as mouse HID reports

### Requirement: USB Mouse Priority over Trackpad
The application SHALL detect external USB mice via `InputManager.InputDeviceListener` and automatically disable the virtual trackpad when one is connected.

#### Scenario: USB mouse is plugged in
- WHEN a non-virtual device with `SOURCE_MOUSE` is added
- THEN the `externalMouseConnected` flag is set to true and the trackpad is disabled

#### Scenario: USB mouse is unplugged
- WHEN the external mouse is removed
- THEN the app re-scans remaining devices, clears the flag, and the trackpad resumes

### Requirement: Forward USB Mouse Drag Events
The application SHALL intercept mouse-sourced touch events (button held + move) in `onTouchEvent` and forward them as HID reports with button state and relative deltas.

#### Scenario: Mouse button pressed and dragged
- WHEN a USB mouse button is held and the mouse is moved
- THEN `onTouchEvent` detects `SOURCE_MOUSE`, computes delta from last position, and sends a report with the button byte and X/Y deltas

#### Scenario: Mouse button released
- WHEN the mouse button is released
- THEN a zero report (no buttons, no movement) is sent

### Requirement: Forward Physical Mouse Motion via Generic Motion Events
The application SHALL intercept physical mouse motion events via `onGenericMotionEvent` using `AXIS_RELATIVE_X` and `AXIS_RELATIVE_Y` (API 34+) for correct relative delta reporting.

#### Scenario: Physical mouse is moved
- WHEN a USB or Bluetooth mouse connected to the phone is moved
- THEN the app reads `AXIS_RELATIVE_X`/`AXIS_RELATIVE_Y` and sends a mouse HID report with relative deltas

#### Scenario: Physical mouse button is clicked
- WHEN a mouse button is pressed or released
- THEN the app sends a mouse HID report with the corresponding button bits (left=0x01, right=0x02, middle=0x04)

### Requirement: Support Scroll Wheel
The application SHALL include scroll wheel delta in the 4-byte mouse HID report (Byte 3), inverted from `AXIS_VSCROLL`.

#### Scenario: User scrolls with mouse wheel
- WHEN a vertical scroll gesture is detected via `AXIS_VSCROLL`
- THEN the app encodes the negated scroll delta in Byte 3 of the mouse report and sends it

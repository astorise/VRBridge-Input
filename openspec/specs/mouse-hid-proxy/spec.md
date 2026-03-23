# mouse-hid-proxy Specification

## Purpose
TBD - created by archiving change add-bluetooth-hid-proxy. Update Purpose after archive.
## Requirements
### Requirement: Forward Touch Events as Trackpad Input
The application SHALL intercept touch events on the UI via `onTouchEvent` and translate them into relative X/Y mouse HID reports.

#### Scenario: User swipes on the trackpad area
- WHEN the user drags a finger across the touch screen area
- THEN the app calculates the delta X/Y movement and sends a 4-byte mouse HID report (Report ID 2)

#### Scenario: User taps on the trackpad area
- WHEN the user performs a short tap gesture
- THEN the app sends a left-click button report followed by a button-release report

### Requirement: Forward Physical Mouse Events as HID Reports
The application SHALL intercept physical mouse events via `onGenericMotionEvent` for devices with `InputDevice.SOURCE_MOUSE` and forward them as relative mouse HID reports.

#### Scenario: Physical mouse is moved
- WHEN a USB-C or Bluetooth mouse connected to the phone is moved
- THEN the app captures the relative motion event and sends a corresponding mouse HID report to the Quest 3

#### Scenario: Physical mouse button is clicked
- WHEN a mouse button is pressed or released
- THEN the app sends a mouse HID report with the corresponding button bit set or cleared

### Requirement: Support Scroll Wheel
The application SHALL include scroll wheel delta in the 4-byte mouse HID report (Byte 3).

#### Scenario: User scrolls with mouse wheel or two-finger swipe
- WHEN a vertical scroll gesture is detected
- THEN the app encodes the scroll delta in Byte 3 of the mouse report and sends it


# Design: Meta Quest 3 Bluetooth HID Proxy

## Architecture
The application will be built entirely in Kotlin using the Android SDK.

- **`BluetoothHidManager`**: A class handling the `BluetoothAdapter.getProfileProxy()` lifecycle for `BluetoothProfile.HID_DEVICE`.
- **`HidForegroundService`**: A Foreground Service of type `connectedDevice` to ensure the Bluetooth connection survives when the app is minimized or the screen is turned off (strict Android 14/15 requirement).
- **`HidReportConfig`**: Contains the hardcoded Composite HID Report Descriptor (Byte Array) defining Interface 1 (Keyboard) and Interface 2 (Mouse).
- **`InputInterceptorActivity`**: The visible UI acting as the trackpad area. It overrides `dispatchKeyEvent` (for keyboards) and `onTouchEvent` / `onGenericMotionEvent` (for touch/mouse).
- **`AzertyToQwertyMapper`**: A mapping utility translating Android `KeyEvent.KEYCODE_*` to standard USB HID Usage Tables (Page 0x07).

## Data Structures
### Composite HID Report Descriptor
The application will use a single Report Descriptor declaring two Report IDs to the Quest 3:
- **`Report ID 1` (Keyboard):** 8 bytes (Byte 0: Modifiers, Byte 1: Reserved, Bytes 2-7: Array of up to 6 pressed keys).
- **`Report ID 2` (Mouse):** 4 bytes (Byte 0: Button states, Byte 1: X delta, Byte 2: Y delta, Byte 3: Wheel).

## Permissions & Manifest Requirements
- `android.permission.BLUETOOTH_CONNECT` (Requires runtime permission)
- `android.permission.BLUETOOTH_ADVERTISE` (Requires runtime permission)
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`
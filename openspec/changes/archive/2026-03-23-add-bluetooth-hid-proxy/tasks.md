# Implementation Tasks

- [x] **Task 1: Setup Android Manifest and Permissions**
  - Add Bluetooth and Foreground Service permissions to `AndroidManifest.xml`.
  - Declare `HidForegroundService` with `android:foregroundServiceType="connectedDevice"`.

- [x] **Task 2: Define the HID Report Descriptor**
  - Create `HidReportConfig.kt`.
  - Define a `ByteArray` containing the standard Composite HID descriptor (Keyboard + Mouse with distinct Report IDs).

- [x] **Task 3: Implement the Key Mapper**
  - Create `AzertyToQwertyMapper.kt`.
  - Map Android `KeyEvent` codes (specifically focusing on AZERTY layout nuances like A/Q, Z/W, M, and numbers) to corresponding USB HID Usage IDs.

- [x] **Task 4: Implement the Bluetooth HID Service**
  - Create `HidForegroundService.kt`.
  - Implement `BluetoothHidDevice.Callback`.
  - Handle `getProfileProxy`, register the app using `BluetoothHidDevice.registerApp` with `HidReportConfig`, and manage device connection states.
  - Expose `sendKeyboardReport(byteArray)` and `sendMouseReport(byteArray)` methods.

- [x] **Task 5: Implement Input Interception UI**
  - Create `InputInterceptorActivity.kt`.
  - Override `dispatchKeyEvent` to capture physical keyboard presses, map them using `AzertyToQwertyMapper`, and send them via the Service.
  - Override `onTouchEvent` to calculate delta X/Y and simulate trackpad clicks (tap to click).
  - Override `onGenericMotionEvent` to capture physical mouse movements (`InputDevice.SOURCE_MOUSE`).

- [x] **Task 6: Edge-to-Edge UI and Polish**
  - Handle Window Insets for Android 15 Edge-to-Edge display to prevent system gesture conflicts on the trackpad view.
  - Add a simple UI displaying the Bluetooth connection status to the Quest 3.
package com.vbridge.input

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings

object HidReportConfig {

    const val REPORT_ID_KEYBOARD: Byte = 1
    const val REPORT_ID_MOUSE: Byte = 2

    /**
     * Composite HID Report Descriptor declaring:
     *   - Report ID 1: Keyboard (8 bytes: modifiers, reserved, 6 key codes)
     *   - Report ID 2: Mouse (4 bytes: buttons, X delta, Y delta, wheel)
     */
    val DESCRIPTOR: ByteArray = byteArrayOf(
        // ---- Keyboard (Report ID 1) ----
        0x05.toByte(), 0x01.toByte(),  // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),  // USAGE (Keyboard)
        0xA1.toByte(), 0x01.toByte(),  // COLLECTION (Application)
        0x85.toByte(), 0x01.toByte(),  //   REPORT_ID (1)

        // Modifier keys (8 bits: L-Ctrl, L-Shift, L-Alt, L-GUI, R-Ctrl, R-Shift, R-Alt, R-GUI)
        0x05.toByte(), 0x07.toByte(),  //   USAGE_PAGE (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(),  //   USAGE_MINIMUM (Keyboard Left Control = 0xE0)
        0x29.toByte(), 0xE7.toByte(),  //   USAGE_MAXIMUM (Keyboard Right GUI = 0xE7)
        0x15.toByte(), 0x00.toByte(),  //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(),  //   LOGICAL_MAXIMUM (1)
        0x75.toByte(), 0x01.toByte(),  //   REPORT_SIZE (1)
        0x95.toByte(), 0x08.toByte(),  //   REPORT_COUNT (8)
        0x81.toByte(), 0x02.toByte(),  //   INPUT (Data, Variable, Absolute) — 1 modifier byte

        // Reserved byte
        0x75.toByte(), 0x08.toByte(),  //   REPORT_SIZE (8)
        0x95.toByte(), 0x01.toByte(),  //   REPORT_COUNT (1)
        0x81.toByte(), 0x01.toByte(),  //   INPUT (Constant) — 1 reserved byte

        // Key code array (up to 6 simultaneous keys)
        0x05.toByte(), 0x07.toByte(),  //   USAGE_PAGE (Keyboard/Keypad)
        0x19.toByte(), 0x00.toByte(),  //   USAGE_MINIMUM (0)
        0x29.toByte(), 0xFF.toByte(),  //   USAGE_MAXIMUM (255)
        0x15.toByte(), 0x00.toByte(),  //   LOGICAL_MINIMUM (0)
        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), // LOGICAL_MAXIMUM (255)
        0x75.toByte(), 0x08.toByte(),  //   REPORT_SIZE (8)
        0x95.toByte(), 0x06.toByte(),  //   REPORT_COUNT (6)
        0x81.toByte(), 0x00.toByte(),  //   INPUT (Data, Array) — 6 key code bytes
        0xC0.toByte(),                 // END_COLLECTION

        // ---- Mouse (Report ID 2) ----
        0x05.toByte(), 0x01.toByte(),  // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x02.toByte(),  // USAGE (Mouse)
        0xA1.toByte(), 0x01.toByte(),  // COLLECTION (Application)
        0x85.toByte(), 0x02.toByte(),  //   REPORT_ID (2)
        0x09.toByte(), 0x01.toByte(),  //   USAGE (Pointer)
        0xA1.toByte(), 0x00.toByte(),  //   COLLECTION (Physical)

        // Buttons (3 bits: left, right, middle)
        0x05.toByte(), 0x09.toByte(),  //     USAGE_PAGE (Button)
        0x19.toByte(), 0x01.toByte(),  //     USAGE_MINIMUM (Button 1 = left)
        0x29.toByte(), 0x03.toByte(),  //     USAGE_MAXIMUM (Button 3 = middle)
        0x15.toByte(), 0x00.toByte(),  //     LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(),  //     LOGICAL_MAXIMUM (1)
        0x95.toByte(), 0x03.toByte(),  //     REPORT_COUNT (3)
        0x75.toByte(), 0x01.toByte(),  //     REPORT_SIZE (1)
        0x81.toByte(), 0x02.toByte(),  //     INPUT (Data, Variable, Absolute) — 3 button bits

        // Padding (5 bits to complete the byte)
        0x95.toByte(), 0x01.toByte(),  //     REPORT_COUNT (1)
        0x75.toByte(), 0x05.toByte(),  //     REPORT_SIZE (5)
        0x81.toByte(), 0x01.toByte(),  //     INPUT (Constant) — 5 pad bits

        // Relative X, Y axes and scroll wheel (signed bytes)
        0x05.toByte(), 0x01.toByte(),  //     USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),  //     USAGE (X)
        0x09.toByte(), 0x31.toByte(),  //     USAGE (Y)
        0x09.toByte(), 0x38.toByte(),  //     USAGE (Wheel)
        0x15.toByte(), 0x81.toByte(),  //     LOGICAL_MINIMUM (-127)
        0x25.toByte(), 0x7F.toByte(),  //     LOGICAL_MAXIMUM (127)
        0x75.toByte(), 0x08.toByte(),  //     REPORT_SIZE (8)
        0x95.toByte(), 0x03.toByte(),  //     REPORT_COUNT (3)
        0x81.toByte(), 0x06.toByte(),  //     INPUT (Data, Variable, Relative)
        0xC0.toByte(),                 //   END_COLLECTION
        0xC0.toByte()                  // END_COLLECTION
    )

    val SDP_SETTINGS by lazy {
        BluetoothHidDeviceAppSdpSettings(
            "VRBridge HID",
            "Bluetooth HID proxy for Meta Quest 3",
            "VRBridge",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            DESCRIPTOR
        )
    }
}

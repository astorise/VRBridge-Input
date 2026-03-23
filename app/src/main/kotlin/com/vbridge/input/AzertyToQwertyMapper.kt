package com.vbridge.input

import android.view.KeyEvent

/**
 * Maps Android KeyEvent keycodes to USB HID Usage IDs (Usage Page 0x07 — Keyboard/Keypad).
 *
 * AZERTY remapping: Android reports logical character keycodes even on AZERTY keyboards.
 * AZERTY 'A' (physical position = QWERTY 'Q') arrives as KEYCODE_A, but the Quest 3 running
 * QWERTY expects HID usage 0x14 (Q key) for that physical position.  The swap table below
 * corrects the 5 physical positions that differ between AZERTY and QWERTY:
 *
 *   AZERTY A ↔ QWERTY Q   (KEYCODE_A → 0x14,  KEYCODE_Q → 0x04)
 *   AZERTY Z ↔ QWERTY W   (KEYCODE_Z → 0x1A,  KEYCODE_W → 0x1D)
 *   AZERTY M is at QWERTY semicolon row but Android still reports KEYCODE_M — no swap needed.
 */
object AzertyToQwertyMapper {

    // HID Usage ID 0x00 means "no key"
    private const val HID_NONE: Byte = 0x00

    // Modifier bit masks (byte 0 of keyboard report)
    private const val MOD_LEFT_CTRL: Byte  = 0x01
    private const val MOD_LEFT_SHIFT: Byte = 0x02
    private const val MOD_LEFT_ALT: Byte   = 0x04
    private const val MOD_LEFT_GUI: Byte   = 0x08
    private const val MOD_RIGHT_CTRL: Byte  = 0x10.toByte()
    private const val MOD_RIGHT_SHIFT: Byte = 0x20.toByte()
    private const val MOD_RIGHT_ALT: Byte   = 0x40.toByte()
    private const val MOD_RIGHT_GUI: Byte   = 0x80.toByte()

    /**
     * Returns the HID usage ID for the given Android keycode.
     * Returns 0x00 for unrecognised keycodes.
     */
    fun getHidUsage(keyCode: Int): Byte = when (keyCode) {
        // ----- Letters (AZERTY ↔ QWERTY swaps) -----
        KeyEvent.KEYCODE_A    -> 0x14.toByte()  // AZERTY A → QWERTY Q position
        KeyEvent.KEYCODE_Q    -> 0x04.toByte()  // AZERTY Q → QWERTY A position
        KeyEvent.KEYCODE_Z    -> 0x1A.toByte()  // AZERTY Z → QWERTY W position
        KeyEvent.KEYCODE_W    -> 0x1D.toByte()  // AZERTY W → QWERTY Z position

        // ----- Letters (same physical position in both layouts) -----
        KeyEvent.KEYCODE_B    -> 0x05.toByte()
        KeyEvent.KEYCODE_C    -> 0x06.toByte()
        KeyEvent.KEYCODE_D    -> 0x07.toByte()
        KeyEvent.KEYCODE_E    -> 0x08.toByte()
        KeyEvent.KEYCODE_F    -> 0x09.toByte()
        KeyEvent.KEYCODE_G    -> 0x0A.toByte()
        KeyEvent.KEYCODE_H    -> 0x0B.toByte()
        KeyEvent.KEYCODE_I    -> 0x0C.toByte()
        KeyEvent.KEYCODE_J    -> 0x0D.toByte()
        KeyEvent.KEYCODE_K    -> 0x0E.toByte()
        KeyEvent.KEYCODE_L    -> 0x0F.toByte()
        KeyEvent.KEYCODE_M    -> 0x10.toByte()
        KeyEvent.KEYCODE_N    -> 0x11.toByte()
        KeyEvent.KEYCODE_O    -> 0x12.toByte()
        KeyEvent.KEYCODE_P    -> 0x13.toByte()
        KeyEvent.KEYCODE_R    -> 0x15.toByte()
        KeyEvent.KEYCODE_S    -> 0x16.toByte()
        KeyEvent.KEYCODE_T    -> 0x17.toByte()
        KeyEvent.KEYCODE_U    -> 0x18.toByte()
        KeyEvent.KEYCODE_V    -> 0x19.toByte()
        KeyEvent.KEYCODE_X    -> 0x1B.toByte()
        KeyEvent.KEYCODE_Y    -> 0x1C.toByte()

        // ----- Digits -----
        KeyEvent.KEYCODE_1    -> 0x1E.toByte()
        KeyEvent.KEYCODE_2    -> 0x1F.toByte()
        KeyEvent.KEYCODE_3    -> 0x20.toByte()
        KeyEvent.KEYCODE_4    -> 0x21.toByte()
        KeyEvent.KEYCODE_5    -> 0x22.toByte()
        KeyEvent.KEYCODE_6    -> 0x23.toByte()
        KeyEvent.KEYCODE_7    -> 0x24.toByte()
        KeyEvent.KEYCODE_8    -> 0x25.toByte()
        KeyEvent.KEYCODE_9    -> 0x26.toByte()
        KeyEvent.KEYCODE_0    -> 0x27.toByte()

        // ----- Control / whitespace keys -----
        KeyEvent.KEYCODE_ENTER        -> 0x28.toByte()
        KeyEvent.KEYCODE_ESCAPE       -> 0x29.toByte()
        KeyEvent.KEYCODE_DEL          -> 0x2A.toByte()  // Backspace
        KeyEvent.KEYCODE_TAB          -> 0x2B.toByte()
        KeyEvent.KEYCODE_SPACE        -> 0x2C.toByte()
        KeyEvent.KEYCODE_MINUS        -> 0x2D.toByte()
        KeyEvent.KEYCODE_EQUALS       -> 0x2E.toByte()
        KeyEvent.KEYCODE_LEFT_BRACKET -> 0x2F.toByte()
        KeyEvent.KEYCODE_RIGHT_BRACKET-> 0x30.toByte()
        KeyEvent.KEYCODE_BACKSLASH    -> 0x31.toByte()
        KeyEvent.KEYCODE_SEMICOLON    -> 0x33.toByte()
        KeyEvent.KEYCODE_APOSTROPHE   -> 0x34.toByte()
        KeyEvent.KEYCODE_GRAVE        -> 0x35.toByte()
        KeyEvent.KEYCODE_COMMA        -> 0x36.toByte()
        KeyEvent.KEYCODE_PERIOD       -> 0x37.toByte()
        KeyEvent.KEYCODE_SLASH        -> 0x38.toByte()
        KeyEvent.KEYCODE_CAPS_LOCK    -> 0x39.toByte()

        // ----- Function keys -----
        KeyEvent.KEYCODE_F1  -> 0x3A.toByte()
        KeyEvent.KEYCODE_F2  -> 0x3B.toByte()
        KeyEvent.KEYCODE_F3  -> 0x3C.toByte()
        KeyEvent.KEYCODE_F4  -> 0x3D.toByte()
        KeyEvent.KEYCODE_F5  -> 0x3E.toByte()
        KeyEvent.KEYCODE_F6  -> 0x3F.toByte()
        KeyEvent.KEYCODE_F7  -> 0x40.toByte()
        KeyEvent.KEYCODE_F8  -> 0x41.toByte()
        KeyEvent.KEYCODE_F9  -> 0x42.toByte()
        KeyEvent.KEYCODE_F10 -> 0x43.toByte()
        KeyEvent.KEYCODE_F11 -> 0x44.toByte()
        KeyEvent.KEYCODE_F12 -> 0x45.toByte()

        // ----- Navigation / editing -----
        KeyEvent.KEYCODE_INSERT        -> 0x49.toByte()
        KeyEvent.KEYCODE_MOVE_HOME     -> 0x4A.toByte()
        KeyEvent.KEYCODE_PAGE_UP       -> 0x4B.toByte()
        KeyEvent.KEYCODE_FORWARD_DEL   -> 0x4C.toByte()  // Delete (forward)
        KeyEvent.KEYCODE_MOVE_END      -> 0x4D.toByte()
        KeyEvent.KEYCODE_PAGE_DOWN     -> 0x4E.toByte()
        KeyEvent.KEYCODE_DPAD_RIGHT    -> 0x4F.toByte()
        KeyEvent.KEYCODE_DPAD_LEFT     -> 0x50.toByte()
        KeyEvent.KEYCODE_DPAD_DOWN     -> 0x51.toByte()
        KeyEvent.KEYCODE_DPAD_UP       -> 0x52.toByte()

        // Modifier keycodes — these are handled via getModifierByte(); return 0 here
        KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_META_LEFT,
        KeyEvent.KEYCODE_META_RIGHT    -> HID_NONE

        else -> HID_NONE
    }

    /**
     * Builds the modifier byte from an Android KeyEvent metaState bitmask.
     */
    fun getModifierByte(metaState: Int): Byte {
        var mod = 0
        if (metaState and KeyEvent.META_CTRL_LEFT_ON  != 0) mod = mod or MOD_LEFT_CTRL.toInt()
        if (metaState and KeyEvent.META_SHIFT_LEFT_ON != 0) mod = mod or MOD_LEFT_SHIFT.toInt()
        if (metaState and KeyEvent.META_ALT_LEFT_ON   != 0) mod = mod or MOD_LEFT_ALT.toInt()
        if (metaState and KeyEvent.META_META_LEFT_ON  != 0) mod = mod or MOD_LEFT_GUI.toInt()
        if (metaState and KeyEvent.META_CTRL_RIGHT_ON  != 0) mod = mod or MOD_RIGHT_CTRL.toInt()
        if (metaState and KeyEvent.META_SHIFT_RIGHT_ON != 0) mod = mod or MOD_RIGHT_SHIFT.toInt()
        if (metaState and KeyEvent.META_ALT_RIGHT_ON   != 0) mod = mod or MOD_RIGHT_ALT.toInt()
        if (metaState and KeyEvent.META_META_RIGHT_ON  != 0) mod = mod or MOD_RIGHT_GUI.toInt()
        return mod.toByte()
    }
}

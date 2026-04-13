package com.vbridge.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * Background keyboard interceptor using AccessibilityService.
 *
 * When enabled in Settings → Accessibility, this service captures all physical keyboard
 * events even when the app is not in the foreground (e.g. screen off, numpad usage).
 * Each key event is forwarded as a HID report to the connected Quest 3 via the
 * [HidForegroundService].
 *
 * This is "Method 1" of the hybrid interception system. If this service is NOT enabled,
 * [InputInterceptorActivity.dispatchKeyEvent] acts as fallback ("Method 2").
 */
class KeyboardAccessibilityService : AccessibilityService() {

    private val pressedHidKeysByKeyCode = linkedMapOf<Int, Byte>()
    private val syntheticShiftKeyCodes = mutableSetOf<Int>()
    private var pressedModifierMask = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        instance = this
        Log.d(TAG, "KeyboardAccessibilityService connected")
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "KeyboardAccessibilityService destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we only need key event filtering
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isPhysicalKeyboardEvent(event)) return false

        val service = HidForegroundService.boundInstance ?: return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (onPhysicalKeyDown(event)) {
                    sendCurrentKeyboardReport(service)
                }
                true // consume the event
            }
            KeyEvent.ACTION_UP -> {
                if (onPhysicalKeyUp(event)) {
                    sendCurrentKeyboardReport(service)
                }
                true // consume the event
            }
            else -> false
        }
    }

    // -------------------------------------------------------------------------
    // Key state tracking (mirrors InputInterceptorActivity logic)
    // -------------------------------------------------------------------------

    private fun onPhysicalKeyDown(event: KeyEvent): Boolean {
        val modifierMask = modifierMaskForKeyCode(event.keyCode)
        if (modifierMask != 0) {
            val updatedMask = pressedModifierMask or modifierMask
            val changed = updatedMask != pressedModifierMask
            pressedModifierMask = updatedMask
            return changed
        }

        val mapping = resolveHidUsageForEvent(event) ?: return false
        val (usage, requiresSyntheticShift) = mapping

        val previousUsage = pressedHidKeysByKeyCode.put(event.keyCode, usage)
        val usageChanged = previousUsage != usage

        val hadSyntheticShift = syntheticShiftKeyCodes.contains(event.keyCode)
        if (requiresSyntheticShift) syntheticShiftKeyCodes.add(event.keyCode)
        else syntheticShiftKeyCodes.remove(event.keyCode)
        val syntheticShiftChanged = hadSyntheticShift != requiresSyntheticShift

        return usageChanged || syntheticShiftChanged
    }

    private fun onPhysicalKeyUp(event: KeyEvent): Boolean {
        val modifierMask = modifierMaskForKeyCode(event.keyCode)
        if (modifierMask != 0) {
            val updatedMask = pressedModifierMask and modifierMask.inv()
            val changed = updatedMask != pressedModifierMask
            pressedModifierMask = updatedMask
            return changed
        }

        val usageRemoved = pressedHidKeysByKeyCode.remove(event.keyCode) != null
        val syntheticShiftRemoved = syntheticShiftKeyCodes.remove(event.keyCode)
        return usageRemoved || syntheticShiftRemoved
    }

    private fun sendCurrentKeyboardReport(service: IBluetoothSender) {
        val report = ByteArray(8)
        val syntheticShiftMask = if (syntheticShiftKeyCodes.isNotEmpty()) LEFT_SHIFT_MODIFIER else 0
        report[0] = (pressedModifierMask or syntheticShiftMask).toByte()

        var reportIndex = 2
        val uniqueUsages = linkedSetOf<Byte>()
        for (usage in pressedHidKeysByKeyCode.values) {
            if (usage == 0.toByte()) continue
            if (uniqueUsages.add(usage) && uniqueUsages.size >= MAX_SIMULTANEOUS_HID_KEYS) break
        }
        for (usage in uniqueUsages) {
            if (reportIndex >= report.size) break
            report[reportIndex++] = usage
        }
        service.sendKeyboardReport(report)
    }

    private fun resolveHidUsageForEvent(event: KeyEvent): Pair<Byte, Boolean>? {
        resolvePrintableHidUsage(event)?.let { return it }
        val usage = AzertyToQwertyMapper.getHidUsage(event.keyCode)
        return if (usage == 0.toByte()) null else Pair(usage, false)
    }

    private fun resolvePrintableHidUsage(event: KeyEvent): Pair<Byte, Boolean>? {
        if (!event.isPrintingKey) return null

        val unicodeWithMeta = event.getUnicodeChar(event.metaState)
        if (unicodeWithMeta != 0) {
            val mapped = charToHidUsage(unicodeWithMeta.toChar())
            if (mapped != null) return mapped
        }

        val unicodeWithoutMeta = event.getUnicodeChar(0)
        if (unicodeWithoutMeta != 0) {
            return charToHidUsage(unicodeWithoutMeta.toChar())
        }
        return null
    }

    private fun charToHidUsage(ch: Char): Pair<Byte, Boolean>? {
        return when (ch) {
            in 'a'..'z' -> Pair((0x04 + (ch - 'a')).toByte(), false)
            in 'A'..'Z' -> Pair((0x04 + (ch - 'A')).toByte(), true)
            in '1'..'9' -> Pair((0x1E + (ch - '1')).toByte(), false)
            '0' -> Pair(0x27.toByte(), false)
            '\n', '\r' -> Pair(0x28.toByte(), false)
            ' ' -> Pair(0x2C.toByte(), false)
            '-' -> Pair(0x2D.toByte(), false)
            '=' -> Pair(0x2E.toByte(), false)
            '[' -> Pair(0x2F.toByte(), false)
            ']' -> Pair(0x30.toByte(), false)
            '\\' -> Pair(0x31.toByte(), false)
            ';' -> Pair(0x33.toByte(), false)
            '\'' -> Pair(0x34.toByte(), false)
            '`' -> Pair(0x35.toByte(), false)
            ',' -> Pair(0x36.toByte(), false)
            '.' -> Pair(0x37.toByte(), false)
            '/' -> Pair(0x38.toByte(), false)
            '!' -> Pair(0x1E.toByte(), true)
            '@' -> Pair(0x1F.toByte(), true)
            '#' -> Pair(0x20.toByte(), true)
            '$' -> Pair(0x21.toByte(), true)
            '%' -> Pair(0x22.toByte(), true)
            '^' -> Pair(0x23.toByte(), true)
            '&' -> Pair(0x24.toByte(), true)
            '*' -> Pair(0x25.toByte(), true)
            '(' -> Pair(0x26.toByte(), true)
            ')' -> Pair(0x27.toByte(), true)
            '_' -> Pair(0x2D.toByte(), true)
            '+' -> Pair(0x2E.toByte(), true)
            '{' -> Pair(0x2F.toByte(), true)
            '}' -> Pair(0x30.toByte(), true)
            '|' -> Pair(0x31.toByte(), true)
            ':' -> Pair(0x33.toByte(), true)
            '"' -> Pair(0x34.toByte(), true)
            '~' -> Pair(0x35.toByte(), true)
            '<' -> Pair(0x36.toByte(), true)
            '>' -> Pair(0x37.toByte(), true)
            '?' -> Pair(0x38.toByte(), true)
            '\t' -> Pair(0x2B.toByte(), false)
            else -> null
        }
    }

    private fun isPhysicalKeyboardEvent(event: KeyEvent): Boolean {
        val device = android.view.InputDevice.getDevice(event.deviceId) ?: return false
        val hasKeyboardSource =
            device.sources and android.view.InputDevice.SOURCE_KEYBOARD == android.view.InputDevice.SOURCE_KEYBOARD
        return hasKeyboardSource &&
            device.keyboardType != android.view.InputDevice.KEYBOARD_TYPE_NONE &&
            !device.isVirtual
    }

    private fun modifierMaskForKeyCode(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_CTRL_LEFT -> MOD_LEFT_CTRL
        KeyEvent.KEYCODE_SHIFT_LEFT -> MOD_LEFT_SHIFT
        KeyEvent.KEYCODE_ALT_LEFT -> MOD_LEFT_ALT
        KeyEvent.KEYCODE_META_LEFT -> MOD_LEFT_GUI
        KeyEvent.KEYCODE_CTRL_RIGHT -> MOD_RIGHT_CTRL
        KeyEvent.KEYCODE_SHIFT_RIGHT -> MOD_RIGHT_SHIFT
        KeyEvent.KEYCODE_ALT_RIGHT -> MOD_RIGHT_ALT
        KeyEvent.KEYCODE_META_RIGHT -> MOD_RIGHT_GUI
        else -> 0
    }

    companion object {
        private const val TAG = "VRBridgeA11y"
        private const val MAX_SIMULTANEOUS_HID_KEYS = 6
        private const val LEFT_SHIFT_MODIFIER = 0x02
        private const val MOD_LEFT_CTRL = 0x01
        private const val MOD_LEFT_SHIFT = 0x02
        private const val MOD_LEFT_ALT = 0x04
        private const val MOD_LEFT_GUI = 0x08
        private const val MOD_RIGHT_CTRL = 0x10
        private const val MOD_RIGHT_SHIFT = 0x20
        private const val MOD_RIGHT_ALT = 0x40
        private const val MOD_RIGHT_GUI = 0x80

        /** Set when the service is alive; null otherwise. */
        @Volatile
        var instance: KeyboardAccessibilityService? = null
            private set

        fun isActive(): Boolean = instance != null
    }
}

package com.vbridge.input

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Build
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import com.vbridge.input.databinding.ActivityMainBinding
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.math.abs
import kotlin.math.roundToInt

class InputInterceptorActivity : AppCompatActivity(),
    HidForegroundService.ConnectionListener,
    InputManager.InputDeviceListener {

    private lateinit var binding: ActivityMainBinding

    // Service binding
    private var hidService: HidForegroundService? = null
    private var serviceBound = false
    private var bluetoothPermissionsGranted = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            hidService = (binder as HidForegroundService.LocalBinder).service
            hidService?.ensureHidProfileReady()
            hidService?.connectionListener = this@InputInterceptorActivity
            serviceBound = true
            // Reflect current connection state immediately
            if (hidService?.isConnected == true) {
                runOnUiThread { setStatusConnected(null) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            resetKeyboardState()
            hidService = null
            serviceBound = false
        }
    }

    // External device detection
    private lateinit var inputManager: InputManager
    private var externalMouseConnected = false
    private var physicalKeyboardConnected = false
    private var questConnected = false
    private var lastMouseButtons: Byte = 0x00
    private var latchedMouseButtons: Byte = 0x00
    private val pressedHidKeysByKeyCode = linkedMapOf<Int, Byte>()
    private val syntheticShiftKeyCodes = mutableSetOf<Int>()
    private var pressedModifierMask = 0

    // Touch tracking for trackpad
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchGestureStartTime = 0L
    private var primaryPointerId = MotionEvent.INVALID_POINTER_ID
    private var maxPointersSeen = 0
    private var tapGestureCancelled = false
    private var rightClickSent = false
    private var secondaryTapPointerId = MotionEvent.INVALID_POINTER_ID
    private var secondaryTapStartTime = 0L
    private var touchDragActive = false
    private val pointerDownPositions = mutableMapOf<Int, Pair<Float, Float>>()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            File(filesDir, "crash.log").writeText(sw.toString())
        }

        // Edge-to-edge: let the app draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply insets so trackpad edges don't conflict with system gesture zones
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val gestureInsets = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val left   = maxOf(systemBars.left,   gestureInsets.left)
            val top    = maxOf(systemBars.top,     gestureInsets.top)
            val right  = maxOf(systemBars.right,   gestureInsets.right)
            val bottom = maxOf(systemBars.bottom,  gestureInsets.bottom)
            view.setPadding(left, top, right, bottom)
            insets
        }

        inputManager = getSystemService(INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(this, null)
        externalMouseConnected = hasExternalMouse()
        physicalKeyboardConnected = hasPhysicalKeyboard()
        updateKeyboardInputVisibility()

        binding.etKeyboardInput.addTextChangedListener(object : TextWatcher {
            private var previousText = ""
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                previousText = s.toString()
            }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val service: IBluetoothSender = hidService ?: return
                val newText = s.toString()
                if (newText.length > previousText.length) {
                    // Characters were added - send each new character
                    val added = newText.substring(previousText.length)
                    for (ch in added) {
                        sendCharAsHidKeystroke(service, ch)
                    }
                } else if (newText.length < previousText.length) {
                    // Characters were deleted - send backspace for each
                    val deletedCount = previousText.length - newText.length
                    repeat(deletedCount) {
                        val report = byteArrayOf(0x00, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x00, 0x00)
                        service.sendKeyboardReport(report)
                        service.sendKeyboardReport(ByteArray(8))
                    }
                }
            }
        })

        bluetoothPermissionsGranted = hasAllRequiredPermissions()
        requestPermissionsIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        if (bluetoothPermissionsGranted) {
            bindHidService()
        }
    }

    override fun onStop() {
        super.onStop()
        resetKeyboardState(hidService)
        if (serviceBound) {
            hidService?.connectionListener = null
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    override fun onDestroy() {
        inputManager.unregisterInputDeviceListener(this)
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Permission handling
    // -------------------------------------------------------------------------

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        bluetoothPermissionsGranted = allGranted
        if (allGranted) {
            startHidService()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                bindHidService()
            }
        }
        // If denied, app remains on screen but cannot connect - status text reflects this
    }

    private fun requestPermissionsIfNeeded() {
        val needed = buildList {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT))   add(Manifest.permission.BLUETOOTH_CONNECT)
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS))  add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) {
            bluetoothPermissionsGranted = true
            startHidService()
        }
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun hasAllRequiredPermissions(): Boolean {
        val bluetoothGranted =
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
            hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
        val notificationsGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        return bluetoothGranted && notificationsGranted
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startHidService() {
        val intent = Intent(this, HidForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun bindHidService() {
        if (serviceBound) return
        val intent = Intent(this, HidForegroundService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // -------------------------------------------------------------------------
    // ConnectionListener
    // -------------------------------------------------------------------------

    override fun onConnected(device: BluetoothDevice) {
        runOnUiThread { setStatusConnected(device.name ?: device.address) }
    }

    override fun onDisconnected() {
        runOnUiThread { setStatusDisconnected() }
    }

    private fun setStatusConnected(deviceName: String?) {
        questConnected = true
        val label = if (deviceName != null)
            getString(R.string.status_connected, deviceName)
        else
            getString(R.string.status_connected, "Quest 3")
        binding.tvStatus.text = label
        binding.tvStatus.setBackgroundColor(0xFF006400.toInt())  // dark green
        updateKeyboardInputVisibility()
    }

    private fun setStatusDisconnected() {
        resetKeyboardState()
        questConnected = false
        binding.tvStatus.text = getString(R.string.status_disconnected)
        binding.tvStatus.setBackgroundColor(0xFFCC0000.toInt())  // dark red
        updateKeyboardInputVisibility()
    }

    // -------------------------------------------------------------------------
    // Keyboard interception
    // -------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fromPhysicalKeyboard = isPhysicalKeyboardEvent(event)
        if (!fromPhysicalKeyboard && binding.etKeyboardInput.hasFocus()) {
            return super.dispatchKeyEvent(event)
        }
        if (!fromPhysicalKeyboard) {
            return super.dispatchKeyEvent(event)
        }

        val service: IBluetoothSender = hidService ?: return true

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (onPhysicalKeyDown(event)) {
                    sendCurrentKeyboardReport(service)
                }
                true
            }
            KeyEvent.ACTION_UP -> {
                if (onPhysicalKeyUp(event)) {
                    sendCurrentKeyboardReport(service)
                }
                true
            }
            KeyEvent.ACTION_MULTIPLE -> true
            else -> true
        }
    }

    // -------------------------------------------------------------------------
    // Touch to trackpad
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isFromMouse = event.source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
        val service: IBluetoothSender = hidService ?: return super.onTouchEvent(event)

        // Mouse drag (button held + move) arrives here as touch events
        if (isFromMouse) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val buttons = effectiveMouseButtons(event)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    lastMouseButtons = buttons
                    service.sendMouseReport(byteArrayOf(buttons, 0x00, 0x00, 0x00))
                }
                MotionEvent.ACTION_MOVE -> {
                    val buttons = effectiveMouseButtons(event)
                    val dx = (event.x - lastTouchX).roundToInt().clampToByte()
                    val dy = (event.y - lastTouchY).roundToInt().clampToByte()
                    lastTouchX = event.x
                    lastTouchY = event.y
                    if (dx != 0 || dy != 0 || buttons != lastMouseButtons) {
                        service.sendMouseReport(byteArrayOf(buttons, dx.toByte(), dy.toByte(), 0x00))
                    }
                    lastMouseButtons = buttons
                }
                MotionEvent.ACTION_UP -> {
                    latchedMouseButtons = 0x00
                    lastMouseButtons = 0x00
                    service.sendMouseReport(byteArrayOf(0x00, 0x00, 0x00, 0x00))
                }
                MotionEvent.ACTION_CANCEL -> {
                    latchedMouseButtons = 0x00
                    lastMouseButtons = 0x00
                    service.sendMouseReport(byteArrayOf(0x00, 0x00, 0x00, 0x00))
                }
            }
            return true
        }

        // Touch pad - disabled when USB mouse is connected
        if (externalMouseConnected) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startTouchGesture(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                rememberPointerDown(event, event.actionIndex)
                updateTapGestureState(event)
                if (touchDragActive) {
                    touchDragActive = false
                    service.sendMouseReport(byteArrayOf(0x00, 0x00, 0x00, 0x00))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                updateTapGestureState(event)
                if (event.pointerCount == 1) {
                    val primaryIndex = event.findPointerIndex(primaryPointerId)
                    if (primaryIndex != -1) {
                        maybeStartTouchDrag(service, event, primaryIndex)
                        val x = event.getX(primaryIndex)
                        val y = event.getY(primaryIndex)
                        val dx = (x - lastTouchX).roundToInt().clampToByte()
                        val dy = (y - lastTouchY).roundToInt().clampToByte()
                        lastTouchX = x
                        lastTouchY = y
                        val dragButton: Byte = if (touchDragActive) 0x01 else 0x00
                        if (dx != 0 || dy != 0) {
                            service.sendMouseReport(byteArrayOf(dragButton, dx.toByte(), dy.toByte(), 0x00))
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                updateTapGestureState(event)
                sendRelaxedRightClickIfEligible(service, event)
                onPointerUp(event)
            }
            MotionEvent.ACTION_UP -> {
                updateTapGestureState(event)
                if (touchDragActive) {
                    touchDragActive = false
                    service.sendMouseReport(byteArrayOf(0x00, 0x00, 0x00, 0x00))
                } else {
                    sendTapClickIfEligible(service, event)
                }
                resetTouchGesture()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (touchDragActive) {
                    touchDragActive = false
                    service.sendMouseReport(byteArrayOf(0x00, 0x00, 0x00, 0x00))
                }
                resetTouchGesture()
            }
        }
        return true
    }

    // -------------------------------------------------------------------------
    // Physical mouse / generic motion
    // -------------------------------------------------------------------------

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val service: IBluetoothSender = hidService ?: return super.onGenericMotionEvent(event)

        if (event.source and InputDevice.SOURCE_MOUSE != InputDevice.SOURCE_MOUSE) {
            return super.onGenericMotionEvent(event)
        }

        val dx     = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X).roundToInt().clampToByte()
        val dy     = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y).roundToInt().clampToByte()
        val scroll = (-event.getAxisValue(MotionEvent.AXIS_VSCROLL)).roundToInt().clampToByte()

        val buttons = effectiveMouseButtons(event)
        val buttonStateChanged = buttons != lastMouseButtons

        if (dx != 0 || dy != 0 || scroll != 0 || buttonStateChanged) {
            service.sendMouseReport(byteArrayOf(buttons, dx.toByte(), dy.toByte(), scroll.toByte()))
        }
        lastMouseButtons = buttons
        return true
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildMouseButtonByte(buttonState: Int): Byte {
        var b = 0
        if (buttonState and MotionEvent.BUTTON_PRIMARY   != 0) b = b or 0x01
        if (buttonState and MotionEvent.BUTTON_SECONDARY != 0) b = b or 0x02
        if (buttonState and MotionEvent.BUTTON_TERTIARY  != 0) b = b or 0x04
        return b.toByte()
    }

    private fun effectiveMouseButtons(event: MotionEvent): Byte {
        val reportedButtons = buildMouseButtonByte(event.buttonState)
        return when (event.actionMasked) {
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                latchedMouseButtons = 0x00
                0x00
            }
            MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                latchedMouseButtons = reportedButtons
                reportedButtons
            }
            else -> {
                if (reportedButtons.toInt() != 0) {
                    latchedMouseButtons = reportedButtons
                    reportedButtons
                } else {
                    latchedMouseButtons
                }
            }
        }
    }

    private fun maybeStartTouchDrag(service: IBluetoothSender, event: MotionEvent, primaryIndex: Int) {
        if (touchDragActive || tapGestureCancelled || rightClickSent) return
        if (maxPointersSeen != 1) return
        val elapsed = event.eventTime - touchGestureStartTime
        if (elapsed < DRAG_HOLD_TIMEOUT_MS) return
        if (!isPointerStillWithinTapSlop(primaryPointerId, event, primaryIndex)) return

        touchDragActive = true
        tapGestureCancelled = true
        service.sendMouseReport(byteArrayOf(0x01, 0x00, 0x00, 0x00))
    }

    private fun resetKeyboardState(service: IBluetoothSender? = null) {
        val hadKeyboardState = pressedModifierMask != 0 ||
            pressedHidKeysByKeyCode.isNotEmpty() ||
            syntheticShiftKeyCodes.isNotEmpty()
        pressedModifierMask = 0
        pressedHidKeysByKeyCode.clear()
        syntheticShiftKeyCodes.clear()
        if (hadKeyboardState && service != null) {
            service.sendKeyboardReport(ByteArray(8))
        }
    }

    private fun isPhysicalKeyboardEvent(event: KeyEvent): Boolean {
        val device = InputDevice.getDevice(event.deviceId) ?: return false
        val hasKeyboardSource =
            device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
        return hasKeyboardSource &&
            device.keyboardType != InputDevice.KEYBOARD_TYPE_NONE &&
            !device.isVirtual
    }

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

    private fun resolvePrintableHidUsage(event: KeyEvent): Pair<Byte, Boolean>? {
        if (!event.isPrintingKey) return null

        val unicodeWithMeta = event.getUnicodeChar(event.metaState)
        if (unicodeWithMeta != 0) {
            val mappedWithMeta = charToHidUsage(unicodeWithMeta.toChar())
            if (mappedWithMeta != null) return mappedWithMeta
        }

        val unicodeWithoutMeta = event.getUnicodeChar(0)
        if (unicodeWithoutMeta != 0) {
            return charToHidUsage(unicodeWithoutMeta.toChar())
        }
        return null
    }

    private fun Int.clampToByte(): Int = this.coerceIn(-127, 127)

    private fun startTouchGesture(event: MotionEvent) {
        primaryPointerId = event.getPointerId(0)
        lastTouchX = event.x
        lastTouchY = event.y
        touchGestureStartTime = event.eventTime
        touchDragActive = false
        maxPointersSeen = 1
        tapGestureCancelled = false
        rightClickSent = false
        secondaryTapPointerId = MotionEvent.INVALID_POINTER_ID
        secondaryTapStartTime = 0L
        pointerDownPositions.clear()
        pointerDownPositions[primaryPointerId] = event.x to event.y
    }

    private fun rememberPointerDown(event: MotionEvent, pointerIndex: Int) {
        val pointerId = event.getPointerId(pointerIndex)
        pointerDownPositions[pointerId] = event.getX(pointerIndex) to event.getY(pointerIndex)
        maxPointersSeen = maxOf(maxPointersSeen, event.pointerCount)
        if (maxPointersSeen > MAX_CLICK_POINTERS) {
            tapGestureCancelled = true
            secondaryTapPointerId = MotionEvent.INVALID_POINTER_ID
            secondaryTapStartTime = 0L
            return
        }
        if (event.pointerCount == 2 &&
            pointerId != primaryPointerId &&
            !tapGestureCancelled &&
            !rightClickSent &&
            isPointerStillWithinTapSlop(primaryPointerId, event)
        ) {
            secondaryTapPointerId = pointerId
            secondaryTapStartTime = event.eventTime
        }
    }

    private fun updateTapGestureState(event: MotionEvent) {
        if (tapGestureCancelled) return
        for (index in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(index)
            if (!isPointerStillWithinTapSlop(pointerId, event, index)) {
                tapGestureCancelled = true
                secondaryTapPointerId = MotionEvent.INVALID_POINTER_ID
                secondaryTapStartTime = 0L
                return
            }
        }
    }

    private fun onPointerUp(event: MotionEvent) {
        val liftedPointerId = event.getPointerId(event.actionIndex)
        pointerDownPositions.remove(liftedPointerId)
        if (liftedPointerId == secondaryTapPointerId) {
            secondaryTapPointerId = MotionEvent.INVALID_POINTER_ID
            secondaryTapStartTime = 0L
        }
        if (liftedPointerId != primaryPointerId) return

        primaryPointerId = MotionEvent.INVALID_POINTER_ID
        for (index in 0 until event.pointerCount) {
            if (index == event.actionIndex) continue
            primaryPointerId = event.getPointerId(index)
            lastTouchX = event.getX(index)
            lastTouchY = event.getY(index)
            return
        }
    }

    private fun isPointerStillWithinTapSlop(
        pointerId: Int,
        event: MotionEvent,
        pointerIndex: Int = event.findPointerIndex(pointerId)
    ): Boolean {
        if (pointerIndex == -1) return true
        val down = pointerDownPositions[pointerId] ?: return true
        val totalMove = abs(event.getX(pointerIndex) - down.first) + abs(event.getY(pointerIndex) - down.second)
        return totalMove < TAP_SLOP_PX
    }

    private fun sendMouseClick(service: IBluetoothSender, button: Byte) {
        service.sendMouseReport(byteArrayOf(button, 0x00, 0x00, 0x00))
        service.sendMouseReport(byteArrayOf(0x00, 0x00, 0x00, 0x00))
    }

    private fun sendRelaxedRightClickIfEligible(service: IBluetoothSender, event: MotionEvent) {
        if (rightClickSent || tapGestureCancelled) return
        if (event.pointerCount != 2 || maxPointersSeen != 2) return

        val liftedPointerId = event.getPointerId(event.actionIndex)
        if (liftedPointerId != secondaryTapPointerId) return

        val secondaryElapsed = event.eventTime - secondaryTapStartTime
        if (secondaryElapsed >= SECONDARY_TAP_TIMEOUT_MS) return

        sendMouseClick(service, 0x02)
        rightClickSent = true
        tapGestureCancelled = true
    }

    private fun sendTapClickIfEligible(service: IBluetoothSender, event: MotionEvent) {
        if (tapGestureCancelled || rightClickSent) return

        if (maxPointersSeen == 2 && secondaryTapStartTime != 0L) {
            val secondaryElapsed = event.eventTime - secondaryTapStartTime
            if (secondaryElapsed < SECONDARY_TAP_TIMEOUT_MS) {
                sendMouseClick(service, 0x02)
                rightClickSent = true
                return
            }
        }

        val elapsed = event.eventTime - touchGestureStartTime
        if (elapsed >= TAP_TIMEOUT_MS) return

        val button: Byte = when (maxPointersSeen) {
            1 -> 0x01
            2 -> 0x02
            else -> return
        }

        sendMouseClick(service, button)
    }

    private fun resetTouchGesture() {
        primaryPointerId = MotionEvent.INVALID_POINTER_ID
        touchGestureStartTime = 0L
        touchDragActive = false
        maxPointersSeen = 0
        tapGestureCancelled = false
        rightClickSent = false
        secondaryTapPointerId = MotionEvent.INVALID_POINTER_ID
        secondaryTapStartTime = 0L
        pointerDownPositions.clear()
    }

    // -------------------------------------------------------------------------
    // InputDeviceListener - USB mouse hot-plug detection
    // -------------------------------------------------------------------------

    override fun onInputDeviceAdded(deviceId: Int) {
        if (isExternalMouse(deviceId)) externalMouseConnected = true
        if (isPhysicalKeyboard(deviceId)) {
            physicalKeyboardConnected = true
            runOnUiThread { updateKeyboardInputVisibility() }
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        externalMouseConnected = hasExternalMouse()
        physicalKeyboardConnected = hasPhysicalKeyboard()
        runOnUiThread { updateKeyboardInputVisibility() }
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        externalMouseConnected = hasExternalMouse()
        physicalKeyboardConnected = hasPhysicalKeyboard()
        runOnUiThread { updateKeyboardInputVisibility() }
    }

    private fun hasExternalMouse(): Boolean =
        InputDevice.getDeviceIds().any { isExternalMouse(it) }

    private fun isExternalMouse(deviceId: Int): Boolean {
        val device = InputDevice.getDevice(deviceId) ?: return false
        return device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
                && !device.isVirtual
    }

    private fun hasPhysicalKeyboard(): Boolean =
        resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY

    private fun isPhysicalKeyboard(deviceId: Int): Boolean {
        val device = InputDevice.getDevice(deviceId) ?: return false
        return device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
                && device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                && !device.isVirtual
    }

    private fun updateKeyboardInputVisibility() {
        val show = questConnected && !physicalKeyboardConnected
        binding.etKeyboardInput.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun sendCharAsHidKeystroke(service: IBluetoothSender, ch: Char) {
        val (usage, shift) = charToHidUsage(ch) ?: return
        val modifier: Byte = if (shift) LEFT_SHIFT_MODIFIER.toByte() else 0x00  // Left Shift
        val report = byteArrayOf(modifier, 0x00, usage, 0x00, 0x00, 0x00, 0x00, 0x00)
        service.sendKeyboardReport(report)
        service.sendKeyboardReport(ByteArray(8))  // release
    }

    /** Maps a character to its HID usage ID and whether Shift is needed. */
    private fun charToHidUsage(ch: Char): Pair<Byte, Boolean>? {
        return when (ch) {
            in 'a'..'z' -> Pair((0x04 + (ch - 'a')).toByte(), false)
            in 'A'..'Z' -> Pair((0x04 + (ch - 'A')).toByte(), true)
            in '1'..'9' -> Pair((0x1E + (ch - '1')).toByte(), false)
            '0' -> Pair(0x27.toByte(), false)
            '\n', '\r' -> Pair(0x28.toByte(), false)  // Enter
            ' ' -> Pair(0x2C.toByte(), false)          // Space
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
            '\t' -> Pair(0x2B.toByte(), false)  // Tab
            else -> null
        }
    }

    companion object {
        private const val TAP_TIMEOUT_MS = 200L
        private const val SECONDARY_TAP_TIMEOUT_MS = 350L
        private const val DRAG_HOLD_TIMEOUT_MS = 220L
        private const val TAP_SLOP_PX    = 10f
        private const val MAX_CLICK_POINTERS = 2
        private const val MAX_SIMULTANEOUS_HID_KEYS = 6
        private const val MOD_LEFT_CTRL = 0x01
        private const val LEFT_SHIFT_MODIFIER = 0x02
        private const val MOD_LEFT_SHIFT = 0x02
        private const val MOD_LEFT_ALT = 0x04
        private const val MOD_LEFT_GUI = 0x08
        private const val MOD_RIGHT_CTRL = 0x10
        private const val MOD_RIGHT_SHIFT = 0x20
        private const val MOD_RIGHT_ALT = 0x40
        private const val MOD_RIGHT_GUI = 0x80
    }
}



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
import com.vbridge.input.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.math.roundToInt

class InputInterceptorActivity : AppCompatActivity(),
    HidForegroundService.ConnectionListener,
    InputManager.InputDeviceListener {

    private lateinit var binding: ActivityMainBinding

    // Service binding
    private var hidService: HidForegroundService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            hidService = (binder as HidForegroundService.LocalBinder).service
            hidService?.connectionListener = this@InputInterceptorActivity
            serviceBound = true
            // Reflect current connection state immediately
            if (hidService?.isConnected == true) {
                runOnUiThread { setStatusConnected(null) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            hidService = null
            serviceBound = false
        }
    }

    // External device detection
    private lateinit var inputManager: InputManager
    private var externalMouseConnected = false
    private var physicalKeyboardConnected = false
    private var questConnected = false

    // Touch tracking for trackpad
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
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
                    // Characters were added — send each new character
                    val added = newText.substring(previousText.length)
                    for (ch in added) {
                        sendCharAsHidKeystroke(service, ch)
                    }
                } else if (newText.length < previousText.length) {
                    // Characters were deleted — send backspace for each
                    val deletedCount = previousText.length - newText.length
                    repeat(deletedCount) {
                        val report = byteArrayOf(0x00, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x00, 0x00)
                        service.sendKeyboardReport(report)
                        service.sendKeyboardReport(ByteArray(8))
                    }
                }
            }
        })

        requestPermissionsIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, HidForegroundService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
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
        if (allGranted) startHidService()
        // If denied, app remains on screen but cannot connect — status text reflects this
    }

    private fun requestPermissionsIfNeeded() {
        val needed = buildList {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT))   add(Manifest.permission.BLUETOOTH_CONNECT)
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS))  add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) startHidService()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startHidService() {
        val intent = Intent(this, HidForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
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
        questConnected = false
        binding.tvStatus.text = getString(R.string.status_disconnected)
        binding.tvStatus.setBackgroundColor(0xFFCC0000.toInt())  // dark red
        updateKeyboardInputVisibility()
    }

    // -------------------------------------------------------------------------
    // Keyboard interception
    // -------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // When using virtual keyboard (no physical keyboard), let the EditText handle input
        // via TextWatcher — don't intercept IME key events
        if (!physicalKeyboardConnected && binding.etKeyboardInput.hasFocus()) {
            return super.dispatchKeyEvent(event)
        }

        val service: IBluetoothSender = hidService ?: return super.dispatchKeyEvent(event)

        val modifiers = AzertyToQwertyMapper.getModifierByte(event.metaState)
        val usage     = AzertyToQwertyMapper.getHidUsage(event.keyCode)

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val report = byteArrayOf(modifiers, 0x00, usage, 0x00, 0x00, 0x00, 0x00, 0x00)
                service.sendKeyboardReport(report)
                true
            }
            KeyEvent.ACTION_UP -> {
                service.sendKeyboardReport(ByteArray(8))  // all-zero = all keys released
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    // -------------------------------------------------------------------------
    // Touch → trackpad
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (externalMouseConnected) return true  // USB mouse takes priority

        val service: IBluetoothSender = hidService ?: return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX   = event.x
                lastTouchY   = event.y
                touchDownX   = event.x
                touchDownY   = event.y
                touchDownTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - lastTouchX).roundToInt().clampToByte()
                val dy = (event.y - lastTouchY).roundToInt().clampToByte()
                lastTouchX = event.x
                lastTouchY = event.y
                if (dx != 0 || dy != 0) {
                    service.sendMouseReport(byteArrayOf(0x00, dx.toByte(), dy.toByte(), 0x00))
                }
            }
            MotionEvent.ACTION_UP -> {
                val elapsed   = System.currentTimeMillis() - touchDownTime
                val totalMove = abs(event.x - touchDownX) + abs(event.y - touchDownY)
                if (elapsed < TAP_TIMEOUT_MS && totalMove < TAP_SLOP_PX) {
                    // Tap → left click then release
                    service.sendMouseReport(byteArrayOf(0x01, 0x00, 0x00, 0x00))
                    service.sendMouseReport(byteArrayOf(0x00, 0x00, 0x00, 0x00))
                }
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

        val dx     = event.getAxisValue(MotionEvent.AXIS_X).roundToInt().clampToByte()
        val dy     = event.getAxisValue(MotionEvent.AXIS_Y).roundToInt().clampToByte()
        val scroll = (-event.getAxisValue(MotionEvent.AXIS_VSCROLL)).roundToInt().clampToByte()

        val buttons = buildMouseButtonByte(event.buttonState)

        service.sendMouseReport(byteArrayOf(buttons, dx.toByte(), dy.toByte(), scroll.toByte()))
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

    private fun Int.clampToByte(): Int = this.coerceIn(-127, 127)

    // -------------------------------------------------------------------------
    // InputDeviceListener – USB mouse hot-plug detection
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
        val modifier: Byte = if (shift) 0x02 else 0x00  // Left Shift
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
        private const val TAP_SLOP_PX    = 10f
    }
}

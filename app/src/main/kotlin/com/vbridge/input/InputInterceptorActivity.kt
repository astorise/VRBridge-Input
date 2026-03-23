package com.vbridge.input

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
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

class InputInterceptorActivity : AppCompatActivity(), HidForegroundService.ConnectionListener {

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
        val label = if (deviceName != null)
            getString(R.string.status_connected, deviceName)
        else
            getString(R.string.status_connected, "Quest 3")
        binding.tvStatus.text = label
        binding.tvStatus.setBackgroundColor(0xFF006400.toInt())  // dark green
    }

    private fun setStatusDisconnected() {
        binding.tvStatus.text = getString(R.string.status_disconnected)
        binding.tvStatus.setBackgroundColor(0xFFCC0000.toInt())  // dark red
    }

    // -------------------------------------------------------------------------
    // Keyboard interception
    // -------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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

    companion object {
        private const val TAP_TIMEOUT_MS = 200L
        private const val TAP_SLOP_PX    = 10f
    }
}

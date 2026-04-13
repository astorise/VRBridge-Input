package com.vbridge.input

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class HidForegroundService : Service(), IBluetoothSender {

    // -------------------------------------------------------------------------
    // Public interface for the bound Activity
    // -------------------------------------------------------------------------

    interface ConnectionListener {
        fun onConnected(device: BluetoothDevice)
        fun onDisconnected()
    }

    inner class LocalBinder : Binder() {
        val service: HidForegroundService get() = this@HidForegroundService
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private val binder = LocalBinder()
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    var connectionListener: ConnectionListener? = null

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var hidProxyRequested = false

    val isConnected: Boolean get() = connectedHost != null

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        boundInstance = this
        createNotificationChannel()
        ensureHidProfileReady()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VRBridge HID")
            .setContentText("Waiting for Quest 3 connection…")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
        ensureHidProfileReady()
        return START_STICKY
    }

    override fun onDestroy() {
        boundInstance = null
        if (hasBluetoothPermissions()) {
            hidDevice?.unregisterApp()
        }
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
        hidProxyRequested = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun ensureHidProfileReady() {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        log("ensureHidProfileReady: adapter=${adapter != null}, enabled=${adapter?.isEnabled}, permissions=${hasBluetoothPermissions()}")
        if (!hasBluetoothPermissions()) {
            log("ensureHidProfileReady: skipping HID initialization until Bluetooth runtime permissions are granted")
            return
        }
        if (hidDevice != null || hidProxyRequested) {
            return
        }
        hidProxyRequested = true
        val gotProxy = adapter?.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
        log("getProfileProxy(HID_DEVICE) returned $gotProxy")
        if (gotProxy != true) {
            hidProxyRequested = false
        }
    }

    // -------------------------------------------------------------------------
    // Bluetooth profile proxy
    // -------------------------------------------------------------------------

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            log("profileListener.onServiceConnected: profile=$profile")
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidProxyRequested = false
                hidDevice = proxy as BluetoothHidDevice
                if (!hasBluetoothPermissions()) {
                    log("registerApp skipped: Bluetooth runtime permissions are missing")
                    return
                }
                val registered = hidDevice?.registerApp(
                    HidReportConfig.SDP_SETTINGS,
                    /* qosOut */ null,
                    /* qosIn  */ null,
                    callbackExecutor,
                    hidCallback
                )
                log("registerApp() returned $registered")
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            log("profileListener.onServiceDisconnected: profile=$profile")
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                hidProxyRequested = false
            }
        }
    }

    // -------------------------------------------------------------------------
    // HID device callbacks
    // -------------------------------------------------------------------------

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            log("onAppStatusChanged: registered=$registered, device=${pluggedDevice?.address}")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            log("onConnectionStateChanged: device=${device.address}, state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    updateNotification("Connected to ${device.name ?: device.address}")
                    connectionListener?.onConnected(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedHost = null
                    updateNotification("Waiting for Quest 3 connection…")
                    connectionListener?.onDisconnected()
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            log("onGetReport: type=$type, id=$id, bufferSize=$bufferSize")
            hidDevice?.replyReport(device, type, id, ByteArray(bufferSize))
        }
    }

    // -------------------------------------------------------------------------
    // Report sending
    // -------------------------------------------------------------------------

    override fun sendKeyboardReport(report: ByteArray) {
        val host = connectedHost ?: return
        if (!hasBluetoothPermissions()) {
            log("sendKeyboardReport skipped: Bluetooth runtime permissions are missing")
            return
        }
        val result = hidDevice?.sendReport(host, HidReportConfig.REPORT_ID_KEYBOARD.toInt(), report)
        log("sendKeyboardReport: result=$result")
    }

    override fun sendMouseReport(report: ByteArray) {
        val host = connectedHost ?: return
        if (!hasBluetoothPermissions()) {
            log("sendMouseReport skipped: Bluetooth runtime permissions are missing")
            return
        }
        val result = hidDevice?.sendReport(host, HidReportConfig.REPORT_ID_MOUSE.toInt(), report)
        log("sendMouseReport: result=$result")
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "HID Proxy Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the Bluetooth HID connection alive in the background"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VRBridge HID")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, notification)
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        try {
            val logFile = File(filesDir, "vrbridge.log")
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            logFile.appendText("$ts $msg\n")
        } catch (_: Exception) {}
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        val connectGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val advertiseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED
        return connectGranted && advertiseGranted
    }

    companion object {
        private const val TAG        = "VRBridgeHID"
        private const val CHANNEL_ID = "hid_service"
        private const val NOTIF_ID   = 1

        /** Accessible by [KeyboardAccessibilityService] to send HID reports. */
        @Volatile
        var boundInstance: HidForegroundService? = null
            private set
    }
}

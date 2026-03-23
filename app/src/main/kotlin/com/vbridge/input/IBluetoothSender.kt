package com.vbridge.input

interface IBluetoothSender {
    fun sendKeyboardReport(report: ByteArray)
    fun sendMouseReport(report: ByteArray)
}

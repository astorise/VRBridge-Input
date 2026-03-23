package com.vbridge.input

import org.junit.Assert.assertEquals
import org.junit.Test

class HidReportConfigTest {

    @Test
    fun descriptor_hasCorrectLength() {
        assertEquals(102, HidReportConfig.DESCRIPTOR.size)
    }

    @Test
    fun descriptor_keyboardReportId_isOne() {
        // Byte at index 7 is the REPORT_ID value for the keyboard collection (0x01)
        assertEquals(0x01.toByte(), HidReportConfig.DESCRIPTOR[7])
    }

    @Test
    fun descriptor_mouseReportId_isTwo() {
        // Byte at index 55 is the REPORT_ID value for the mouse collection (0x02)
        assertEquals(0x02.toByte(), HidReportConfig.DESCRIPTOR[55])
    }

    @Test
    fun descriptor_keyboardEndCollection_isPresent() {
        // Byte at index 47 is 0xC0 (END_COLLECTION) closing the keyboard application collection
        assertEquals(0xC0.toByte(), HidReportConfig.DESCRIPTOR[47])
    }

    @Test
    fun reportIdKeyboard_isOne() {
        assertEquals(1.toByte(), HidReportConfig.REPORT_ID_KEYBOARD)
    }

    @Test
    fun reportIdMouse_isTwo() {
        assertEquals(2.toByte(), HidReportConfig.REPORT_ID_MOUSE)
    }
}

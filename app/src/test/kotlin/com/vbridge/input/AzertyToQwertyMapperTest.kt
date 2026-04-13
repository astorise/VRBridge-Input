package com.vbridge.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class AzertyToQwertyMapperTest {

    @Test
    fun azertyA_mapsTo_qwertyQ() {
        assertEquals(0x14.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_A))
    }

    @Test
    fun azertyQ_mapsTo_qwertyA() {
        assertEquals(0x04.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_Q))
    }

    @Test
    fun azertyZ_mapsTo_qwertyW() {
        assertEquals(0x1A.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_Z))
    }

    @Test
    fun azertyW_mapsTo_qwertyZ() {
        assertEquals(0x1D.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_W))
    }

    @Test
    fun mKey_sameInBothLayouts() {
        assertEquals(0x10.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_M))
    }

    @Test
    fun spaceKey_mapsToHidSpace() {
        assertEquals(0x2C.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_SPACE))
    }

    @Test
    fun digit1_mapsToHid1() {
        assertEquals(0x1E.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_1))
    }

    @Test
    fun digit0_mapsToHid0() {
        assertEquals(0x27.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_0))
    }

    @Test
    fun unknownKey_mapsToZero() {
        assertEquals(0x00.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_UNKNOWN))
    }

    // ----- Numpad keys -----

    @Test
    fun numpad1_mapsToHidNumpad1() {
        assertEquals(0x59.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_NUMPAD_1))
    }

    @Test
    fun numpad0_mapsToHidNumpad0() {
        assertEquals(0x62.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_NUMPAD_0))
    }

    @Test
    fun numpadEnter_mapsToHidNumpadEnter() {
        assertEquals(0x58.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_NUMPAD_ENTER))
    }

    @Test
    fun numpadDot_mapsToHidNumpadDot() {
        assertEquals(0x63.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_NUMPAD_DOT))
    }

    @Test
    fun numpadAdd_mapsToHidNumpadAdd() {
        assertEquals(0x57.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_NUMPAD_ADD))
    }

    @Test
    fun numpad5_mapsToHidNumpad5() {
        assertEquals(0x5D.toByte(), AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_NUMPAD_5))
    }

    @Test
    fun digit1_and_numpad1_areDifferent() {
        val digit1 = AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_1)
        val numpad1 = AzertyToQwertyMapper.getHidUsage(KeyEvent.KEYCODE_NUMPAD_1)
        assertEquals(0x1E.toByte(), digit1)
        assertEquals(0x59.toByte(), numpad1)
        assert(digit1 != numpad1) { "Top-row digit 1 and numpad 1 must map to different HID usages" }
    }

    // ----- Modifier byte -----

    @Test
    fun modifierByte_leftShift() {
        assertEquals(0x02.toByte(), AzertyToQwertyMapper.getModifierByte(KeyEvent.META_SHIFT_LEFT_ON))
    }

    @Test
    fun modifierByte_noModifiers() {
        assertEquals(0x00.toByte(), AzertyToQwertyMapper.getModifierByte(0))
    }

    @Test
    fun modifierByte_multipleModifiers() {
        val meta = KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_ALT_LEFT_ON
        assertEquals(0x05.toByte(), AzertyToQwertyMapper.getModifierByte(meta))
    }
}

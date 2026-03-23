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
}

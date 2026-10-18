package com.karmakameleon.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlairColorTest {
    // ==================== parseFlairColor ====================

    @Test
    fun parseFlairColor_hexWithHash() {
        assertEquals(0xFFFF4500.toInt(), parseFlairColor("#ff4500"))
    }

    @Test
    fun parseFlairColor_uppercaseHex() {
        assertEquals(0xFFDADADA.toInt(), parseFlairColor("#DADADA"))
    }

    @Test
    fun parseFlairColor_withoutHash() {
        assertEquals(0xFF014980.toInt(), parseFlairColor("014980"))
    }

    @Test
    fun parseFlairColor_null() {
        assertNull(parseFlairColor(null))
    }

    @Test
    fun parseFlairColor_empty() {
        assertNull(parseFlairColor(""))
    }

    @Test
    fun parseFlairColor_transparent() {
        assertNull(parseFlairColor("transparent"))
    }

    @Test
    fun parseFlairColor_malformed() {
        assertNull(parseFlairColor("#zzzzzz"))
        assertNull(parseFlairColor("#fff"))
    }

    // ==================== flairNeedsDarkText ====================

    @Test
    fun flairNeedsDarkText_white() {
        assertTrue(flairNeedsDarkText(0xFFFFFFFF.toInt()))
    }

    @Test
    fun flairNeedsDarkText_black() {
        assertFalse(flairNeedsDarkText(0xFF000000.toInt()))
    }

    @Test
    fun flairNeedsDarkText_paleGray() {
        assertTrue(flairNeedsDarkText(0xFFDADADA.toInt()))
    }

    @Test
    fun flairNeedsDarkText_paleGreen() {
        assertTrue(flairNeedsDarkText(0xFFC8F7C5.toInt()))
    }

    @Test
    fun flairNeedsDarkText_navy() {
        assertFalse(flairNeedsDarkText(0xFF014980.toInt()))
    }

    @Test
    fun flairNeedsDarkText_darkRed() {
        assertFalse(flairNeedsDarkText(0xFFB71C1C.toInt()))
    }

    @Test
    fun flairNeedsDarkText_redditOrange() {
        // Black on #ff4500 is about 6.1:1, white only about 3.4:1
        assertTrue(flairNeedsDarkText(0xFFFF4500.toInt()))
    }
}

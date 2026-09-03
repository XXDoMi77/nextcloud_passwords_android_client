package com.dominikdomotor.nextcloudpasswords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccentColorTest {
    @Test
    fun `parses the hex the server sends`() {
        assertEquals(0xFF0082C9.toInt(), AccentColor.parse("#0082c9"))
    }

    @Test
    fun `tolerates a missing hash and stray whitespace`() {
        assertEquals(0xFF0082C9.toInt(), AccentColor.parse("  0082C9 "))
    }

    @Test
    fun `rejects anything that is not a colour`() {
        // The hex field is parsed on every keystroke, so half-typed values must not throw.
        listOf("", "   ", "#", "#12", "#12345", "nope", "#gggggg").forEach { assertNull(it, AccentColor.parse(it)) }
    }

    @Test
    fun `formats back to the server's own notation`() {
        assertEquals("#0082C9", AccentColor.format(0xFF0082C9.toInt()))
    }

    @Test
    fun `formatting round-trips through parsing`() {
        val color = 0xFF3B7D4F.toInt()
        assertEquals(color, AccentColor.parse(AccentColor.format(color)))
    }

    @Test
    fun `light colours get black text and dark colours get white`() {
        assertEquals(AccentColor.BLACK, AccentColor.contrastingTextOn(AccentColor.WHITE))
        assertEquals(AccentColor.BLACK, AccentColor.contrastingTextOn(0xFFFFEB3B.toInt()))
        assertEquals(AccentColor.WHITE, AccentColor.contrastingTextOn(AccentColor.BLACK))
        assertEquals(AccentColor.WHITE, AccentColor.contrastingTextOn(0xFF0082C9.toInt()))
    }
}

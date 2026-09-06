package com.dominikdomotor.nextcloudpasswords.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FaviconWarmUpOrderTest {
    private val ids = listOf("a", "b", "c", "d", "e")

    @Test
    fun `visible rows come first`() {
        assertEquals(listOf("c", "d", "a", "b", "e"), FaviconWarmUpOrder.visibleFirst(ids, 2, 3))
    }

    @Test
    fun `every id is kept exactly once`() {
        val ordered = FaviconWarmUpOrder.visibleFirst(ids, 1, 2)
        assertEquals(ids.size, ordered.size)
        assertEquals(ids.toSet(), ordered.toSet())
    }

    @Test
    fun `range past the end falls back to list order`() {
        // The crash this guards: search narrowed the list while it was scrolled below the new end,
        // so the layout manager reported subList(26, 21).
        assertEquals(ids, FaviconWarmUpOrder.visibleFirst(ids, 26, 20))
    }

    @Test
    fun `partly out of range range is clamped`() {
        assertEquals(listOf("d", "e", "a", "b", "c"), FaviconWarmUpOrder.visibleFirst(ids, 3, 99))
    }

    @Test
    fun `negative first position is clamped`() {
        assertEquals(listOf("a", "b", "c", "d", "e"), FaviconWarmUpOrder.visibleFirst(ids, -1, 1))
    }

    @Test
    fun `empty list stays empty`() {
        assertEquals(emptyList<String>(), FaviconWarmUpOrder.visibleFirst(emptyList(), 0, 5))
    }
}

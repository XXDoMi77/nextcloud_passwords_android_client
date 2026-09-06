package com.dominikdomotor.nextcloudpasswords.data

/** Decides which favicons to decode first. */
object FaviconWarmUpOrder {
    /**
     * [ids] reordered so the rows between [firstVisible] and [lastVisible] come first.
     *
     * Positions are tolerated rather than trusted. The list is asked for its visible range right after a new list is
     * submitted, and the layout manager still describes the previous one until the next layout pass — so a search that
     * narrows 165 rows to 21 while scrolled to row 26 reports a range past the end. Such a range yields plain list
     * order instead of an exception.
     */
    fun visibleFirst(ids: List<String>, firstVisible: Int, lastVisible: Int): List<String> {
        val start = firstVisible.coerceIn(0, ids.size)
        val end = (lastVisible + 1).coerceIn(start, ids.size)
        if (start >= end) return ids

        val visible = ids.subList(start, end)
        return visible + (ids - visible.toSet())
    }
}

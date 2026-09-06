package com.dominikdomotor.nextcloudpasswords.ui

import android.view.View
import com.dominikdomotor.nextcloudpasswords.R

/**
 * The filled container behind one row of a grouped list.
 *
 * The first and last row repeat the group's own corner radius so they fill its rounded corners rather than being sliced
 * flat by them; the rows between stay barely rounded so the run still reads as a single block. The list is clipped to
 * the same radius on top of this (see [clipToRoundedCorners]), which is what trims a row that happens to be scrolled
 * into the corner.
 */
object ListGroupBackground {
    fun apply(view: View, position: Int, count: Int) {
        val first = position == 0
        val last = position == count - 1
        view.setBackgroundResource(
            when {
                first && last -> R.drawable.list_group_single
                first -> R.drawable.list_group_top
                last -> R.drawable.list_group_bottom
                else -> R.drawable.list_group_middle
            }
        )
    }
}

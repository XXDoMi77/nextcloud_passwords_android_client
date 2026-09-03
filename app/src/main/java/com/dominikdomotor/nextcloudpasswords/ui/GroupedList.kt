package com.dominikdomotor.nextcloudpasswords.ui

import android.widget.ListView
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView
import com.dominikdomotor.nextcloudpasswords.R

/**
 * Gives a list the app's grouped look: rows separated by a gap rather than a rule, no scrollbar, and rounded ends.
 *
 * Applied from one place so the blocked-app list, the share suggestions and the folder picker cannot drift apart.
 */
fun ListView.asGroupedList() {
    divider = android.graphics.Color.TRANSPARENT.toDrawable()
    dividerHeight = resources.getDimensionPixelSize(R.dimen.spacing_group_vertical)
    isVerticalScrollBarEnabled = false
    clipToRoundedCorners(resources.getDimension(R.dimen.radius_group))
}

fun RecyclerView.asGroupedList() {
    isVerticalScrollBarEnabled = false
    clipToRoundedCorners(resources.getDimension(R.dimen.radius_group))
}

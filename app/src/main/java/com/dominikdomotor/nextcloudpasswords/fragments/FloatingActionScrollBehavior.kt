package com.dominikdomotor.nextcloudpasswords.fragments

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs

fun RecyclerView.hideFloatingActionsOnScroll(vararg actions: FloatingActionButton) {
    fun updateActions(scrollDelta: Float) {
        if (scrollDelta > 0) actions.forEach { it.hide() } else if (scrollDelta < 0) actions.forEach { it.show() }
    }

    addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateActions(dy.toFloat())
            }
        }
    )

    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var previousY = 0f
    addOnItemTouchListener(
        object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> previousY = event.y
                    MotionEvent.ACTION_MOVE -> {
                        val delta = previousY - event.y
                        if (abs(delta) >= touchSlop) {
                            updateActions(delta)
                            previousY = event.y
                        }
                    }
                }
                return false
            }
        }
    )
}

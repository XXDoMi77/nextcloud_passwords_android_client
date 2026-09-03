package com.dominikdomotor.nextcloudpasswords.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import com.dominikdomotor.nextcloudpasswords.R
import kotlin.math.abs
import kotlin.math.min

/**
 * Pull-to-refresh with a text header: over-pull reveals "Pull to refresh", crossing the threshold changes it to
 * "Release to refresh", and releasing hands off to the sweeping progress bar at the top of the list.
 *
 * Written rather than reused because `SwipeRefreshLayout` cannot do this — it exposes no drag progress and supports no
 * custom header — and Material 3's pull-to-refresh exists only in Compose. Drag handling goes through the
 * nested-scrolling parent contract, so `RecyclerView` keeps its own fling and scroll behaviour untouched.
 */
class PullToRefreshLayout
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr), NestedScrollingParent3 {

    /** Invoked once per gesture, when the user releases past the threshold. */
    var onRefresh: (() -> Unit)? = null

    private val parentHelper = NestedScrollingParentHelper(this)
    private val density = resources.displayMetrics.density
    private val triggerDistance = TRIGGER_DP * density
    private val maximumDistance = MAXIMUM_DP * density

    /**
     * Material's emphasized-decelerate curve: moves off quickly, then eases into place.
     *
     * Deliberately not a spring — it settles once and stops, with no swing back and forth. A linear settle reads as
     * mechanical; an overshoot would be invisible here anyway, because the content cannot translate above its resting
     * position.
     */
    private val settleInterpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    private lateinit var content: View
    private val header =
        TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.shaded_text_color))
            textSize = HEADER_TEXT_SP
            minHeight = (HEADER_HEIGHT_DP * density).toInt()
            setText(R.string.pull_to_refresh)
        }

    private var dragOffset = 0f
    private var armed = false
    private var settleAnimator: ValueAnimator? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        require(childCount == 1) { "PullToRefreshLayout must contain exactly one child" }
        content = getChildAt(0)
        addView(header, 0, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        applyOffset(0f)
    }

    // ------------------------------------------------------------------ nested scrolling

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean =
        // Touch only: a fling should not turn into a pull.
        type == ViewCompat.TYPE_TOUCH && axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        parentHelper.onNestedScrollAccepted(child, target, axes, type)
        settleAnimator?.cancel()
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        // Scrolling back up: unwind our own offset before the list starts moving again.
        if (type == ViewCompat.TYPE_TOUCH && dy > 0 && dragOffset > 0f) {
            val used = min(dy.toFloat(), dragOffset)
            applyOffset(dragOffset - used)
            consumed[1] = used.toInt()
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        if (type != ViewCompat.TYPE_TOUCH || dyUnconsumed >= 0) return
        // The list could not scroll further up, so the remainder is an over-pull.
        applyOffset(dragOffset + abs(dyUnconsumed) * DRAG_RESISTANCE)
        consumed[1] += dyUnconsumed
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
    ) = onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, IntArray(2))

    override fun onStopNestedScroll(target: View, type: Int) {
        parentHelper.onStopNestedScroll(target, type)
        if (type != ViewCompat.TYPE_TOUCH) return

        val shouldRefresh = armed
        settleToRest()
        if (shouldRefresh) onRefresh?.invoke()
    }

    override fun getNestedScrollAxes(): Int = parentHelper.nestedScrollAxes

    // ------------------------------------------------------------------------- movement

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // The header's height is only known once it has been laid out. Without re-positioning here
        // it keeps the translationY computed against a height of 0, which parks it on screen at rest
        // instead of just above the list.
        positionChildren()
    }

    private fun positionChildren() {
        content.translationY = dragOffset
        header.translationY = dragOffset - header.height
    }

    private fun applyOffset(offset: Float) {
        dragOffset = offset.coerceIn(0f, maximumDistance)
        positionChildren()

        val nowArmed = dragOffset >= triggerDistance
        if (nowArmed != armed) {
            armed = nowArmed
            header.setText(if (armed) R.string.release_to_refresh else R.string.pull_to_refresh)
            // A tick at the threshold means the user does not have to watch the label.
            if (armed) header.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun settleToRest() {
        settleAnimator?.cancel()
        // Scale the duration to how far there is to travel, so a short pull does not linger.
        val fraction = if (maximumDistance > 0f) dragOffset / maximumDistance else 0f
        settleAnimator =
            ValueAnimator.ofFloat(dragOffset, 0f).apply {
                duration = (MIN_SETTLE_MILLIS + fraction * (MAX_SETTLE_MILLIS - MIN_SETTLE_MILLIS)).toLong()
                interpolator = settleInterpolator
                addUpdateListener { applyOffset(it.animatedValue as Float) }
                start()
            }
    }

    override fun onDetachedFromWindow() {
        settleAnimator?.cancel()
        settleAnimator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TRIGGER_DP = 64f
        const val MAXIMUM_DP = 112f
        const val HEADER_HEIGHT_DP = 48f
        const val HEADER_TEXT_SP = 14f

        /** Pulling feels heavier than a plain 1:1 drag, matching platform over-scroll. */
        const val DRAG_RESISTANCE = 0.5f

        const val MIN_SETTLE_MILLIS = 180f
        const val MAX_SETTLE_MILLIS = 340f
    }
}

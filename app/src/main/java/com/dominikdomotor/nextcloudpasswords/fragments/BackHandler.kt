package com.dominikdomotor.nextcloudpasswords.fragments

/**
 * A tab that wants first refusal on the back gesture.
 *
 * The fragments used to register their own [androidx.activity.OnBackPressedCallback]s, which stayed enabled while the
 * fragment was hidden. Since all three tabs are added at once and only shown or hidden, whichever callback was
 * registered last handled every back press regardless of the visible tab — so back closed the app from anywhere instead
 * of returning to the password list.
 *
 * [OverviewActivity][com.dominikdomotor.nextcloudpasswords.activities.OverviewActivity] now owns a single callback and
 * asks only the tab that is actually showing.
 */
interface BackHandler {
    /** Returns true when the tab consumed the back press. */
    fun handleBack(): Boolean
}

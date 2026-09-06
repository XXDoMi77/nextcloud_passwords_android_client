package com.dominikdomotor.nextcloudpasswords.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.content.res.AppCompatResources
import com.dominikdomotor.nextcloudpasswords.ui.theme.themeColor
import com.google.android.material.R as MaterialR
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.R as FastScrollR

/**
 * The Md2 fast scroller, with its thumb and track tinted from the palette.
 *
 * `useMd2Style()` tints them itself, from `colorControlActivated` and `colorControlNormal` - but only the framework
 * (`android:`) forms of those attributes, and only whatever they happen to resolve to at the moment the builder runs.
 * That made the scrollbar the one control in the app that did not follow the chosen colour: it stayed on the baseline
 * Material accent no matter what the seed was. Re-tinting afterwards from roles we map ourselves takes the guesswork
 * out - the thumb is the accent, the track is the surface tone it slides over.
 *
 * The library's own drawables are reused rather than rebuilt, because their padding and intrinsic size are what the
 * scroller measures itself against.
 *
 * `colorPrimary` comes from AppCompat's `R.attr` and not Material's: non-transitive R classes are the default, so each
 * library's `R` carries only the resources it declares itself.
 */
fun FastScrollerBuilder.usePaletteStyle(context: Context): FastScrollerBuilder = apply {
    useMd2Style()
    setThumbDrawable(tinted(context, FastScrollR.drawable.afs_md2_thumb, AppCompatR.attr.colorPrimary))
    setTrackDrawable(tinted(context, FastScrollR.drawable.afs_md2_track, MaterialR.attr.colorSurfaceContainerHighest))
}

private fun tinted(context: Context, @DrawableRes drawable: Int, attr: Int): Drawable =
    // Mutated first: these come from the resource cache, and tinting a shared instance would repaint every other
    // user of the same drawable.
    AppCompatResources.getDrawable(context, drawable)!!.mutate().apply {
        setTintList(ColorStateList.valueOf(context.themeColor(attr)))
    }

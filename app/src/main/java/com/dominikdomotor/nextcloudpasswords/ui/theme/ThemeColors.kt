package com.dominikdomotor.nextcloudpasswords.ui.theme

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.google.android.material.color.MaterialColors

/**
 * Resolves a Material 3 role attribute against this context's theme.
 *
 * Code that used to read a fixed `R.color.…` now asks for the role instead, so it picks up whatever the generated
 * palette put in that slot. Magenta as the fallback is deliberate: an unmapped role should be impossible to miss on
 * screen rather than quietly render as black.
 */
@ColorInt fun Context.themeColor(@AttrRes attr: Int): Int = MaterialColors.getColor(this, attr, MISSING_ROLE)

@ColorInt private const val MISSING_ROLE: Int = 0xFFFF00FF.toInt()

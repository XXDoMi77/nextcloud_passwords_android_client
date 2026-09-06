package com.dominikdomotor.nextcloudpasswords

import android.util.Log
import com.dominikdomotor.nextcloudpasswords.managers.Keys

typealias GF = GlobalFunctions

object GlobalFunctions {
    /**
     * Debug-only logging.
     *
     * Release builds stay silent: these messages carry request URLs and response codes, which do not belong in a
     * shipped password manager's logcat.
     */
    fun println(msg: String) {
        if (BuildConfig.DEBUG) Log.i(Keys.LOG_TAG, msg)
    }
}

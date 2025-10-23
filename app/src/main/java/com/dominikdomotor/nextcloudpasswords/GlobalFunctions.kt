package com.dominikdomotor.nextcloudpasswords

import android.util.Log
import com.dominikdomotor.nextcloudpasswords.managers.Keys

typealias GF = GlobalFunctions

object GlobalFunctions {
    fun println(msg: String) {
        Log.i(Keys.log_key, msg)
    }
}

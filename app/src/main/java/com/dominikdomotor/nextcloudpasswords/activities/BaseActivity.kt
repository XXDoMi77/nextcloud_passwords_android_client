package com.dominikdomotor.nextcloudpasswords.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set decorFitsSystemWindows to false for all activities
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Access WindowInsetsController to manipulate system bars if needed.
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // Make sure that the system bar does not draw on top of the app if light content is displayed.
        windowInsetsController.isAppearanceLightStatusBars = false
    }
}

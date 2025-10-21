package com.dominikdomotor.nextcloudpasswords.activities

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.databinding.ActivityOverviewBinding
import com.dominikdomotor.nextcloudpasswords.managers.EFM
import com.dominikdomotor.nextcloudpasswords.managers.NM
import com.dominikdomotor.nextcloudpasswords.managers.SM
import com.google.android.material.bottomnavigation.BottomNavigationView

//import com.ionspin.kotlin.crypto.LibsodiumInitializer


class OverviewActivity : BaseActivity() {
    private lateinit var binding: ActivityOverviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        EFM.init(this.applicationContext)
        SM.init()
        NM.init(this.applicationContext)

        if (!SM.getSettings().loggedIn) {
            GF.println("logged_in_check")
            startActivity(Intent(this, EnterServerURLActivity::class.java))
            this.finish()
        }

        binding = ActivityOverviewBinding.inflate(layoutInflater)
        supportActionBar?.hide()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }

        fun isDarkMode(context: Context): Boolean {
            val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == Configuration.UI_MODE_NIGHT_YES
        }

        fun setStatusBarAppearance(window: Window, isDarkMode: Boolean, context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowInsetsController = window.insetsController ?: return
                windowInsetsController.setSystemBarsAppearance(
                    if (isDarkMode) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                window.decorView.systemUiVisibility = if (isDarkMode) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            window.statusBarColor = ContextCompat.getColor(context, R.color.status_bar_color)
        }

        setStatusBarAppearance(window, isDarkMode(this), this)

        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_overview) as NavHostFragment
        val navController = navHostFragment.navController
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        //		val appBarConfiguration = AppBarConfiguration(
        //			setOf(
        //				R.id.navigation_folders, R.id.navigation_passwords, R.id.navigation_account
        //			)
        //		)
        //		setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

    }
}
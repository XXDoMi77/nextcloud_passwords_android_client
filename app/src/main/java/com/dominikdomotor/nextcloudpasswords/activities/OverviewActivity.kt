package com.dominikdomotor.nextcloudpasswords.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
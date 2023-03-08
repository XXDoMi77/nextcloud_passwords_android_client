package com.dominikdomotor.nextcloudpasswords

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.dominikdomotor.nextcloudpasswords.databinding.ActivityOverviewBinding
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.Password
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson

class OverviewActivity : AppCompatActivity() {
	
	
	private lateinit var binding: ActivityOverviewBinding
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		//checking if user is logged in
		if (!App.sharedPreferences().getBoolean("logged_in", false)) {
			val intent = Intent(this, EnterServerURLActivity::class.java)
			finish()
			startActivity(intent)
		}
		
		if (App.sharedPreferencesAreInitialized()) {
			try {
				passwords = Gson().fromJson(App.sharedPreferences().getString(SPKeys.passwords, SPKeys.not_found), Array<Password>::class.java).sortedBy { it.label }.toTypedArray()
			}
			catch (e: Exception){
				e.printStackTrace()
			}
			this.pullPasswords {
				//TODO() recyclerview has to be notified of updated passwords somehow, don't know how to get it in this scope without a global variable... so for now I couldn't be bothered less
			}
		}
		
		binding = ActivityOverviewBinding.inflate(layoutInflater)
		supportActionBar?.hide()
		setContentView(binding.root)
		
		val navView: BottomNavigationView = binding.navView
		
		val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_overview) as NavHostFragment
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
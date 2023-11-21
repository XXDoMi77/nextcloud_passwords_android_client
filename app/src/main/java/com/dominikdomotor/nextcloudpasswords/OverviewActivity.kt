package com.dominikdomotor.nextcloudpasswords

import android.content.Intent
import android.os.Bundle
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.dominikdomotor.nextcloudpasswords.databinding.ActivityOverviewBinding
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.Password
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
//import com.ionspin.kotlin.crypto.LibsodiumInitializer

class OverviewActivity : AppCompatActivity() {
	
	
	private lateinit var binding: ActivityOverviewBinding
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		SharedPreferencesManager.init(this)
		PasswordManager.init(this)
//		EncryptionManager.init()
//		LibsodiumInitializer.initializeWithCallback {}
		
		//write default values for Settings to SharedPreferences if they don't yet exist
		if (!SharedPreferencesManager.getSharedPreferences().contains(SPKeys.settings_password_length)){
			SharedPreferencesManager.getSharedPreferences().edit().putString(SPKeys.settings_password_length, "20").apply()
		}
		if (!SharedPreferencesManager.getSharedPreferences().contains(SPKeys.settings_include_symbols_number)){
			SharedPreferencesManager.getSharedPreferences().edit().putString(SPKeys.settings_include_symbols_number, "1").apply()
		}
		if (!SharedPreferencesManager.getSharedPreferences().contains(SPKeys.settings_exclude_similar_numbers)){
			SharedPreferencesManager.getSharedPreferences().edit().putBoolean(SPKeys.settings_exclude_similar_numbers, true).apply()
		}
		
		//checking if user is logged in
		if (!SharedPreferencesManager.getSharedPreferences().getBoolean("logged_in", false)) {
			println("Switching to EnterServerURLActivity")
			val intent = Intent(this, EnterServerURLActivity::class.java)
			finish()
			startActivity(intent)
		} else {
			try {
				PasswordManager.init(this)
			}
			catch (e: Exception){
				e.printStackTrace()
			}
//			PasswordManager.pullPasswords(this) {
//				//TODO() recyclerview has to be notified of updated passwords somehow, don't know how to get it in this scope without a global variable... so for now I couldn't be bothered less
//			}
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
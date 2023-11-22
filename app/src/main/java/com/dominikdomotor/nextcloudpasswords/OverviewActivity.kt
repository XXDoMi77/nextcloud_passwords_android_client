package com.dominikdomotor.nextcloudpasswords

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.dominikdomotor.nextcloudpasswords.databinding.ActivityOverviewBinding
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import com.google.android.material.bottomnavigation.BottomNavigationView

//import com.ionspin.kotlin.crypto.LibsodiumInitializer

class OverviewActivity : AppCompatActivity() {
	
	
	private lateinit var binding: ActivityOverviewBinding
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		val efm = EncryptedFileManager(applicationContext)
		
		
		PasswordManager.init(applicationContext)
//		EncryptionManager.init()
//		LibsodiumInitializer.initializeWithCallback {}
		
		//write default values for Settings to SharedPreferences if they don't yet exist
		if (!efm.exists(SPKeys.settings_password_length)){
			efm.store(SPKeys.settings_password_length, "20")
		}
		if (!efm.exists(SPKeys.settings_include_symbols_number)){
			efm.store(SPKeys.settings_include_symbols_number, "1")
		}
		if (!efm.exists(SPKeys.settings_exclude_similar_numbers)){
			efm.store(SPKeys.settings_exclude_similar_numbers, true.toString())
		}
		
		//checking if user is logged in
		if (!efm.read(SPKeys.logged_in).toBoolean()) {
			println("Switching to EnterServerURLActivity")
			val intent = Intent(this, EnterServerURLActivity::class.java)
			finish()
			startActivity(intent)
		} else {
			try {
				PasswordManager.init(applicationContext)
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
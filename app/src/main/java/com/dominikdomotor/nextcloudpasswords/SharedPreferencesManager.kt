package com.dominikdomotor.nextcloudpasswords
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys


object SharedPreferencesManager {
	private lateinit var encryptedSharedPreferences: SharedPreferences
	
	fun init(context: Context) {
		
		
		var masterKey = MasterKey.Builder(context)
			.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
			.build()
		
		encryptedSharedPreferences = EncryptedSharedPreferences.create(
			context,
			SPKeys.secure_storage,
			masterKey,
			EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
			EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
		)
	}
	
	fun getSharedPreferences(): SharedPreferences {
		return encryptedSharedPreferences
	}
}
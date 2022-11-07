package com.dominikdomotor.nextcloudpasswords

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.API
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import com.google.gson.Gson
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection

class App: Application() {
	
	init {
		instance = this
	}
	
	companion object {
		private var instance: App? = null
		
		fun applicationContext() : Context {
			return instance!!.applicationContext
		}

		
		fun sharedPreferences(): SharedPreferences {
			//instancing SharedPreferences
			val sharedPrefsFile = SPKeys.secure_storage
			val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
			val mainKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)
			return EncryptedSharedPreferences.create(
				sharedPrefsFile, mainKeyAlias, instance!!, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
			)
		}
		
		fun sharedPreferencesAreInitialized(): Boolean {
			if (sharedPreferences().contains(SPKeys.passwords)) {
				if (sharedPreferences().getString(SPKeys.passwords, SPKeys.not_found)?.isNotEmpty() == true) {
					return true
				}
			}
			return false
		}
		

		
		fun makeHTTPSRequest(urlExtension: String, requestMethod: String, body: String?, headers: List<Pair<String, String>>?){
			
			val server = sharedPreferences().getString(SPKeys.server, SPKeys.not_found)
			val username = sharedPreferences().getString(SPKeys.username, SPKeys.not_found)
			val token = sharedPreferences().getString(SPKeys.token, SPKeys.not_found)
			val userCredentials = "$username:$token"
			val basicAuth = "Basic " + String(Base64.getEncoder().encode(userCredentials.toByteArray()))
			
			Thread {
				val url = URL(server + urlExtension)
				val httpsConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
				
				httpsConnection.requestMethod = requestMethod

				httpsConnection.setRequestProperty("Authorization", basicAuth)
				httpsConnection.setRequestProperty("User-Agent", "Nextcloud Passwords App " + applicationContext().getString(R.string.on) + " " + Settings.Secure.getString(applicationContext().contentResolver, "bluetooth_name"))
				if (!headers.isNullOrEmpty()){
					headers.forEach{
						httpsConnection.setRequestProperty(it.first, it.second)
					}
				}
				
				if(!body.isNullOrEmpty()){
					httpsConnection.doOutput = true
					val outStream: OutputStream = httpsConnection.outputStream
					val outStreamWriter = OutputStreamWriter(outStream, "UTF-8")
					outStreamWriter.write(body)
					outStreamWriter.flush()
					outStreamWriter.close()
					outStream.close()
				}else {
					httpsConnection.doOutput = false
				}
				
//				if (httpsConnection.responseCode in 200..299) {
////			val inputAsString = httpConnection.inputStream.bufferedReader().use { it.readText() }
//
//					passwords.forEachIndexed { index, password ->
//						if (password.id == updatedPassword.id) {
//							passwords[index] = updatedPassword
//						}
//					}
//					App.sharedPreferences().edit().putString(SPKeys.passwords, Gson().toJson(passwords)).apply()
//
//				} else {
//				}
//
//				println("\nSent ${httpsConnection.requestMethod} request to URL : $url; Response Code : ${httpsConnection.responseCode}")
//
//				func()
			}.start()
		}
		
		fun getRandomString(length: Int) : String {
			val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + "._-;,+*/#"
			return (1..length)
				.map { allowedChars.random() }
				.joinToString("")
		}

		fun makeToast(message: String, length: Int) {
			Looper.prepare()
			Toast.makeText(instance, message, length).show()
		}
	}
	
	override fun onCreate() {
		super.onCreate()
		// initialize for any
		
		// Use ApplicationContext.
		// example: SharedPreferences etc...
		val context: Context = App.applicationContext()
	}
}
package com.dominikdomotor.nextcloudpasswords

import android.app.Activity
import android.webkit.URLUtil
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.API
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.Password
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URI
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection


var passwords: Array<Password> = arrayOf(Password())



fun Activity.updatePassword(updatedPassword: Password, func: () -> Unit) {
	val strategy: ExclusionStrategy = object : ExclusionStrategy {
		override fun shouldSkipField(field: FieldAttributes): Boolean {
			if (field.name == "id" ||
					field.name == "password" ||
					field.name == "label" ||
					field.name == "username" ||
					field.name == "url" ||
					field.name == "notes" ||
					field.name == "customFields" ||
					field.name == "folder" ||
					field.name == "favorite"
			) {
				return false
			}
			return true
		}
		
		override fun shouldSkipClass(clazz: Class<*>?): Boolean {
			return false
		}
	}
	
	val gson = GsonBuilder()
		.addSerializationExclusionStrategy(strategy)
		.create()
	val jsonString = gson.toJson(updatedPassword)


//	val updatedPasswordJSON = Gson().toJson(updatedPassword)
	
	println(jsonString)
	
	val server = App.sharedPreferences().getString(SPKeys.server, SPKeys.not_found)
	val username = App.sharedPreferences().getString(SPKeys.username, SPKeys.not_found)
	val token = App.sharedPreferences().getString(SPKeys.token, SPKeys.not_found)
	val userCredentials = "$username:$token"
	val basicAuth = "Basic " + String(Base64.getEncoder().encode(userCredentials.toByteArray()))
	Thread {
		val url = URL(server + API.Password.Update.url)
		val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
		
		httpConnection.requestMethod = "PATCH"
		httpConnection.setRequestProperty("Authorization", basicAuth)
		httpConnection.setRequestProperty("Connection", "keep-alive")
		httpConnection.setRequestProperty("Content-Type", "application/json");
		
		httpConnection.doOutput = true
		val outStream: OutputStream = httpConnection.outputStream
		val outStreamWriter = OutputStreamWriter(outStream, "UTF-8")
		outStreamWriter.write(jsonString)
		outStreamWriter.flush()
		outStreamWriter.close()
		outStream.close()
		
		if (httpConnection.responseCode in 200..299) {
//			val inputAsString = httpConnection.inputStream.bufferedReader().use { it.readText() }
			App.makeToast(getString(R.string.password_successfully_updated), Toast.LENGTH_SHORT)
			
			passwords.forEachIndexed { index, password ->
				if (password.id == updatedPassword.id) {
					passwords[index] = updatedPassword
				}
			}
			App.sharedPreferences().edit().putString(SPKeys.passwords, Gson().toJson(passwords)).apply()
			
		} else {
			App.makeToast(getString(R.string.something_went_wrong_try_again), Toast.LENGTH_SHORT)
		}
		
		println("\nSent ${httpConnection.requestMethod} request to URL : $url; Response Code : ${httpConnection.responseCode}")
		
		func()
	}.start()
}



fun Activity.pullPasswords(func: () -> Unit) {
	try {
		val server = App.sharedPreferences().getString(SPKeys.server, SPKeys.not_found)
		val username = App.sharedPreferences().getString(SPKeys.username, SPKeys.not_found)
		val token = App.sharedPreferences().getString(SPKeys.token, SPKeys.not_found)
		val userCredentials = "$username:$token"
		val basicAuth = "Basic " + String(Base64.getEncoder().encode(userCredentials.toByteArray()))
		Thread {
			val getPasswordListURL = URL(server + API.Password.List.url)
			with(getPasswordListURL.openConnection() as HttpsURLConnection) {
				setRequestProperty("Authorization", basicAuth)
				setRequestProperty("Connection", "keep-alive")
				requestMethod = "GET"
				println("\nSent $requestMethod request to URL : $getPasswordListURL; Response Code : $responseCode")
				if (responseCode in 200..299) {
					val inputAsString = inputStream.bufferedReader().use { it.readText() }
					App.sharedPreferences().edit().putString(SPKeys.passwords, inputAsString).apply()
					App.makeToast(getString(R.string.password_list_fetch_successful), Toast.LENGTH_SHORT)
					
				} else {
					App.makeToast(getString(R.string.something_went_wrong_try_again), Toast.LENGTH_SHORT)
				}
			}
			
			val getShareListURL = URL("$server/index.php/apps/passwords/api/1.0/share/list")
			with(getShareListURL.openConnection() as HttpsURLConnection) {
				setRequestProperty("Authorization", basicAuth)
				setRequestProperty("Connection", "keep-alive")
				requestMethod = "GET"
				println("\nSent $requestMethod request to URL : $getShareListURL; Response Code : $responseCode")
				if (responseCode in 200..299) {
					val inputAsString = inputStream.bufferedReader().use { it.readText() }
					App.sharedPreferences().edit().putString(SPKeys.shares, inputAsString).apply()
//					makeToast(getString(R.string.share_list_fetch_successful), Toast.LENGTH_SHORT)
				} else {
//					makeToast(getString(R.string.something_went_wrong_try_again), Toast.LENGTH_SHORT)
				}
			}
			
			
			val faviconTemp: MutableMap<String, ByteArray> = mutableMapOf()
			passwords.forEach { password ->
				if(password.favicon.isNotEmpty()){
					faviconTemp[password.id] = password.favicon
				}
			}
			
			passwords = Gson().fromJson(App.sharedPreferences().getString(SPKeys.passwords, SPKeys.not_found), Array<Password>::class.java).sortedBy { it.label }.toTypedArray()
			
			passwords.forEach { password ->
				if (faviconTemp.containsKey(password.id)) {
					password.favicon = faviconTemp[password.id]!!
				}
			}
			
			App.sharedPreferences().edit().putString(SPKeys.passwords, Gson().toJson(passwords)).apply()
			
			runOnUiThread {
				func()
			}
			return@Thread
		}.start()
	} catch (e: Exception) {
		e.printStackTrace()
	}
	return
}


fun Activity.pullPasswordIcons(func: () -> Unit) {
	Thread {
		fun getDomainName(url: String?): String {
			val uri = URI(url)
			val domain: String = uri.host
			return if (domain.startsWith("www.")) domain.substring(4) else domain
		}
		
		lateinit var builder: AlertDialog.Builder
		lateinit var dialog: AlertDialog
		lateinit var progressBar: ProgressBar
		runOnUiThread {
			builder = AlertDialog.Builder(this)
			builder.setCancelable(false) // if you want user to wait for some process to finish,
			
			builder.setView(R.layout.layout_loading_dialog)
			dialog = builder.create()
			dialog.show()
			progressBar = dialog.findViewById<ProgressBar>(R.id.progressBar)!!
			progressBar.max = passwords.size
		}
		
		val server = App.sharedPreferences().getString(SPKeys.server, SPKeys.not_found)
		val username = App.sharedPreferences().getString(SPKeys.username, SPKeys.not_found)
		val token = App.sharedPreferences().getString(SPKeys.token, SPKeys.not_found)
		val userCredentials = "$username:$token"
		val basicAuth = "Basic " + String(Base64.getEncoder().encode(userCredentials.toByteArray()))
		
		val averageIndex: MutableList<Int> = mutableListOf()
		
		val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
		passwords.forEachIndexed { index, password ->
			val worker = Runnable {
				try {
					if (password.url.isNotEmpty() and URLUtil.isValidUrl(password.url)) {
						val getFaviconURL = URL(server + "/index.php/apps/passwords/api/1.0/service/favicon/" + getDomainName(password.url) + "/32")
						with(getFaviconURL.openConnection() as HttpsURLConnection) {
							setRequestProperty("Authorization", basicAuth)
							requestMethod = "GET"
							connectTimeout = 2000
							readTimeout = 2000
							println("\nSent $requestMethod request to URL : $getFaviconURL; Response Code : $responseCode")
							if (responseCode in 200..299) {
								password.favicon = inputStream.readBytes()
							}
						}
					}
					println("Hello this is thread $index")
					runOnUiThread {
						averageIndex.add(index)
						if (progressBar.progress < averageIndex.takeLast(10).average().toInt() + 7) {
							progressBar.progress = averageIndex.takeLast(10).average().toInt() + 7
							dialog.findViewById<TextView>(R.id.textview_progress)?.text = "${averageIndex.takeLast(10).average().toInt() + 7}/${passwords.size}"
						}
					}
					
				} catch (e: Exception) {
					e.printStackTrace()
				}
				
			}
			executor.execute(worker)
		}
		executor.shutdown()
		while (!executor.isTerminated) {
		}
		App.sharedPreferences().edit().putString(SPKeys.passwords, Gson().toJson(passwords)).apply()
		println("Finished all threads")
		println("Done fetching favicons")
		runOnUiThread {
			dialog.dismiss()
			func()
		}
		return@Thread
	}.start()
}

fun Activity.clearFaviconCache() {
	passwords.forEach { password ->
		password.favicon = byteArrayOf()
	}
	App.sharedPreferences().edit().putString(SPKeys.passwords, Gson().toJson(passwords)).apply()
}

fun Activity.clearPasswordCache() {
	passwords = arrayOf(Password())
	App.sharedPreferences().edit().putString(SPKeys.passwords, Gson().toJson(passwords)).apply()
}
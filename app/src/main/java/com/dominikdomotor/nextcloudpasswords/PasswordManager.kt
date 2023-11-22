package com.dominikdomotor.nextcloudpasswords


import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.API
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.Password
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import com.google.android.material.snackbar.Snackbar
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection


object PasswordManager {
	private lateinit var passwords: Array<Password>
	private lateinit var partners: MutableMap<String, String>
	
	private lateinit var server: String
	private lateinit var username: String
	private lateinit var token: String
	private lateinit var userCredentials: String
	private lateinit var basicAuth: String
	
	fun init(applicationContext: Context) {
		try {
			val efm = EncryptedFileManager(applicationContext)
			
			println("Passwords getting read from sharedpreferences")
			
			passwords = Gson().fromJson(
				efm.read(SPKeys.passwords), Array<Password>::class.java
			).sortedBy { it.label }.toTypedArray()
			
			
			server = efm.read(SPKeys.server)
			username = efm.read(SPKeys.username)
			token = efm.read(SPKeys.token)
			userCredentials = "$username:$token"
			basicAuth = "Basic " + String(Base64.getEncoder().encode(userCredentials.toByteArray()))
			
		} catch (e: Exception) {
			println(e.stackTrace)
			emptyPasswords()
			clearFaviconCache(applicationContext)
			clearStoredPasswordsFromSharedPreferences(applicationContext)
//			context.getSharedPreferences(SPKeys.secure_storage, Context.MODE_PRIVATE).edit().clear().commit()
		}
	}
	
	fun deletePassword(applicationContext: Context, activity: Activity, deletedPassword: Password, func: () -> Unit) {
		val strategy: ExclusionStrategy = object : ExclusionStrategy {
			override fun shouldSkipField(field: FieldAttributes): Boolean {
				if (field.name == "id"
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
		val jsonString = gson.toJson(deletedPassword)
		Thread {
			val url = URL(server + API.Password.Delete.URL)
			val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
			
			httpConnection.requestMethod = API.Password.Delete.METHOD
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
				activity.runOnUiThread {
					Toast.makeText(activity, activity.getString(R.string.password_successfully_deleted), Toast.LENGTH_SHORT).show()
				}
				
				passwords.filter { it.id != deletedPassword.id }.toTypedArray()
				writePasswordsToEncryptedSharedPreferences(applicationContext)
			} else {
				activity.runOnUiThread {
					Toast.makeText(activity, activity.getString(R.string.something_went_wrong_try_again), Toast.LENGTH_SHORT).show()
				}
			}
			
			println("\nSent ${httpConnection.requestMethod} request to URL : $url; Response Code : ${httpConnection.responseCode}")
			func()
		}.start()
	}
	
	fun updatePassword(applicationContext: Context, activity: Activity, updatedPassword: Password, func: () -> Unit) {
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
		
		Thread {
			
			val efm = EncryptedFileManager(applicationContext)
			
			val url = URL(server + API.Password.Update.URL)
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
				activity.runOnUiThread {
					Toast.makeText(activity, activity.getString(R.string.password_successfully_updated), Toast.LENGTH_SHORT).show()
				}
				
				passwords.forEachIndexed { index, password ->
					if (password.id == updatedPassword.id) {
						passwords[index] = updatedPassword
					}
				}
				efm.store(SPKeys.passwords, Gson().toJson(passwords))
				
			} else {
				activity.runOnUiThread {
					Toast.makeText(activity, activity.getString(R.string.something_went_wrong_try_again), Toast.LENGTH_SHORT).show()
				}
			}
			
			println("\nSent ${httpConnection.requestMethod} request to URL : $url; Response Code : ${httpConnection.responseCode}")
			
			func()
		}.start()
	}
	
	fun sha1(input: String): String {
		val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
		return bytes.joinToString("") { "%02x".format(it) }
	}
	
	fun createPassword(activity: Activity, label: String, username: String, password: String, url: String, notes: String, func: () -> Unit) {
		val notesFixed = notes.replace("\n", "\\n")
		
		try {
			
			val hash = sha1(password)
			
			Thread {
				val api = API.Password.Create
				val httpConnection: HttpsURLConnection = URL(server + api.URL).openConnection() as HttpsURLConnection
				
				httpConnection.requestMethod = api.METHOD
				api.HEADERS.forEach { header ->
					httpConnection.setRequestProperty(header.first, header.second)
				}
				httpConnection.setRequestProperty("Authorization", basicAuth)
				
				if (api.DO_OUTPUT) {
					httpConnection.doOutput = true
					val outStream: OutputStream = httpConnection.outputStream
					val outStreamWriter = OutputStreamWriter(outStream, "UTF-8")
					outStreamWriter.write(
						"""
{
	"label": "$label",
	"username": "$username",
	"password": "$password",
	"url": "$url",
	"notes": "$notesFixed",
	"hash": "$hash"
}
"""
					)
					outStreamWriter.flush()
					outStreamWriter.close()
					outStream.close()
				}
				
				when (httpConnection.responseCode) {
					in 200..299 -> {
						func()
						activity.runOnUiThread {
							Toast.makeText(activity, activity.getString(R.string.password_successfully_created), Toast.LENGTH_SHORT).show()
						}
					}
					
					412 -> {
						//TODO Make E2E encryption work...
					}
					
					else -> {
						activity.runOnUiThread {
							Toast.makeText(activity, activity.getString(R.string.something_went_wrong_try_again), Toast.LENGTH_SHORT).show()
						}
					}
				}
			}.start()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	fun pullPasswords(applicationContext: Context, activity: Activity, func: () -> Unit) {
		try {
			Thread {
				
				val efm = EncryptedFileManager(applicationContext)
				
				val api = API.Password.List
				
				val url = URL(server + api.URL)
				val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
				
				httpConnection.requestMethod = api.METHOD
				api.HEADERS.forEach { header ->
					httpConnection.setRequestProperty(header.first, header.second)
				}
				httpConnection.setRequestProperty("Authorization", basicAuth)
				

//				if(api.DO_OUTPUT){
//					httpConnection.doOutput = true
//					val outStream: OutputStream = httpConnection.outputStream
//					val outStreamWriter = OutputStreamWriter(outStream, "UTF-8")
//					outStreamWriter.write(api.OUTPUT)
//					outStreamWriter.flush()
//					outStreamWriter.close()
//					outStream.close()
//				}
//
				when (httpConnection.responseCode) {
					in 200..299 -> {
						val inputAsString = httpConnection.inputStream.bufferedReader().use { it.readText() }
						efm.store(SPKeys.passwords, inputAsString)
					}
					
					412 -> {
						//TODO Make E2E encryption work...
					}
					
					else -> {
						activity.runOnUiThread {
							Toast.makeText(activity, activity.getString(R.string.something_went_wrong_try_again), Toast.LENGTH_SHORT).show()
						}
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
						efm.store(SPKeys.shares, inputAsString)
//					makeToast(getString(R.string.share_list_fetch_successful), Toast.LENGTH_SHORT)
					} else {
//					makeToast(getString(R.string.something_went_wrong_try_again), Toast.LENGTH_SHORT)
					}
				}
				
				
				val faviconTemp: MutableMap<String, ByteArray> = mutableMapOf()
				passwords.forEach { password ->
					if (password.favicon.isNotEmpty()) {
						faviconTemp[password.id] = password.favicon
					}
				}
				
				passwords = Gson().fromJson(
					efm.read(SPKeys.passwords), Array<Password>::class.java
				).sortedBy { it.label }.toTypedArray()
				
				passwords.forEach { password ->
					if (faviconTemp.containsKey(password.id)) {
						password.favicon = faviconTemp[password.id]!!
					}
				}
				
				efm.store(SPKeys.passwords, Gson().toJson(passwords))
				
				activity.runOnUiThread {
					func()
				}
			}.start()
		} catch (e: Exception) {
			e.printStackTrace()
		}
		return
	}
	
	
	fun pullPasswordIcons(applicationContext: Context, activity: Activity, func: () -> Unit) {
		Thread {
			val efm = EncryptedFileManager(applicationContext)
			
			fun getDomainName(url: String?): String {
				val uri = URI(url)
				val domain: String = uri.host
				return if (domain.startsWith("www.")) domain.substring(4) else domain
			}

//		lateinit var builder: AlertDialog.Builder
//		lateinit var dialog: AlertDialog
//		lateinit var progressBar: ProgressBar
//		runOnUiThread {
//			builder = AlertDialog.Builder(this)
//			builder.setCancelable(false) // if you want user to wait for some process to finish,
//
//			builder.setView(R.layout.layout_loading_dialog)
//			dialog = builder.create()
//			dialog.show()
//			progressBar = dialog.findViewById<ProgressBar>(R.id.progressBar)!!
//			progressBar.max = passwords.size
//		}
			
			val snackBarProgress: Snackbar =
				Snackbar.make(activity.findViewById(R.id.coordinatorLayoutForNotifications), "Progress", Snackbar.LENGTH_INDEFINITE)
			val view: View = snackBarProgress.view
			val params = view.layoutParams as CoordinatorLayout.LayoutParams
			params.gravity = Gravity.TOP
			view.layoutParams = params
			
			val contentLay: ViewGroup =
				snackBarProgress.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).parent as ViewGroup
			val item = ProgressBar(activity)
			contentLay.addView(item, 0)
			snackBarProgress.show()
			
			var progress = 0
			
			val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
			PasswordManager.passwords.forEachIndexed { index, password ->
				val worker = Runnable {
					try {
						if (password.url.isNotEmpty() and URLUtil.isValidUrl(password.url)) {
							val getFaviconURL =
								URL(server + "/index.php/apps/passwords/api/1.0/service/favicon/" + getDomainName(password.url) + "/32")
							with(getFaviconURL.openConnection() as HttpsURLConnection) {
								setRequestProperty("Authorization", basicAuth)
								requestMethod = "GET"
								connectTimeout = 8000
								readTimeout = 8000
								println("\nSent $requestMethod request to URL : $getFaviconURL; Response Code : $responseCode")
								if (responseCode in 200..299) {
									password.favicon = inputStream.readBytes()
								}
							}
						} else {
							val getFaviconURL = URL(server + "/index.php/apps/passwords/api/1.0/service/favicon/" + password.label + "/32")
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
					} catch (e: Exception) {
						e.printStackTrace()
					}
					progress++
					activity.runOnUiThread {
//					progressBar.progress = progress
//					dialog.findViewById<TextView>(R.id.textview_progress)?.text = "$progress/${passwords.size}"
						snackBarProgress.setText("Loading icons $progress/${PasswordManager.passwords.size}")
					}
				}
				executor.execute(worker)
			}
			executor.shutdown()
			while (!executor.isTerminated) {
			}
			efm.store(SPKeys.passwords, Gson().toJson(PasswordManager.passwords))
			println("Finished all threads")
			println("Done fetching favicons")
			activity.runOnUiThread {
//			dialog.dismiss()
				snackBarProgress.dismiss()
				func()
			}
		}.start()
	}
	
	fun getPartners(activity: Activity, applicationContext: Context) {
		try {
			val efm = EncryptedFileManager(applicationContext)
			val server = efm.read(SPKeys.server)
			val username = efm.read(SPKeys.username)
			val token = efm.read(SPKeys.token)
			
			val userCredentials = "$username:$token"
			val basicAuth = "Basic " + String(Base64.getEncoder().encode(userCredentials.toByteArray()))
			Thread {
				val url = URL(server + API.Share.Partners.URL)
				with(url.openConnection() as HttpsURLConnection) {
					setRequestProperty("Authorization", basicAuth)
					setRequestProperty("Connection", "keep-alive")
					setRequestProperty("Content-Type", "application/json");
					doOutput = true
					val outStream: OutputStream = outputStream
					val outStreamWriter = OutputStreamWriter(outStream, "UTF-8")
					outStreamWriter.write(API.Share.Partners.BODY)
					outStreamWriter.flush()
					outStreamWriter.close()
					outStream.close()
					requestMethod = "GET"
					if (responseCode in 200..299) {
						val inputAsString = inputStream.bufferedReader().use { it.readText() }
						efm.store(SPKeys.partners, inputAsString)
					} else if (responseCode == 412) {
//						val url = URL(server + API.Session.Request.url)
//						val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
//
//						httpConnection.requestMethod = API.Session.Request.method
//						httpConnection.setRequestProperty("Authorization", basicAuth)
//						httpConnection.setRequestProperty("Connection", "keep-alive")
//						httpConnection.setRequestProperty("Content-Type", "application/json");
//
//						val inputStreamAsString = httpConnection.inputStream.bufferedReader().use { it.readText() }
//						print("\n\n\n" + inputStreamAsString + "\n\n\n")
//
//						var challengea = Gson().fromJson(inputStreamAsString, ChallengeContainer::class.java)
//						print("\n\n\n" + challengea.toString() + "\n\n\n")
//						print("\n\n\n" + challengea.challenge.salts[0] + "\n\n\n")
//
//						val sodium = SodiumAndroid()
//
//						//sodium.crypto_generichash()
//
//
					} else {
//					val inputAsString = inputStream.bufferedReader().use { it.readText() }
//					print(inputStream)
						activity.runOnUiThread {
							Toast.makeText(activity, activity.getString(R.string.something_went_wrong_try_again), Toast.LENGTH_SHORT).show()
						}
					}
				}

//				try{
//					partners = Gson().fromJson(
//						SharedPreferencesManager.getSharedPreferences().getString(SPKeys.partners, SPKeys.not_found), object : TypeToken<Partners>() {}.type
//					)
//				} catch (e:Exception){
//					e.printStackTrace()
//				}
				
				val builder = GsonBuilder()
				partners = builder.create().fromJson(
					efm.read(SPKeys.partners),
					object : TypeToken<MutableMap<String, String>>() {})
				println(partners.javaClass)
				println(partners.toMutableMap()["Ferenc"])
				
				efm.store(SPKeys.partners, Gson().toJson(partners))

//				activity.runOnUiThread {
//					func()
//				}
			}.start()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	fun getPasswords(): Array<Password> {
		return passwords
	}
	
	fun clearPasswordsCache() {
		passwords = arrayOf(Password())
	}
	
	fun clearFaviconCache(applicationContext: Context) {
		passwords.forEach { password ->
			password.favicon = byteArrayOf()
		}
		val efm = EncryptedFileManager(applicationContext)
		efm.store(SPKeys.passwords, Gson().toJson(passwords))
	}
	
	fun clearStoredPasswordsFromSharedPreferences(applicationContext: Context) {
		passwords = arrayOf(Password())
		val efm = EncryptedFileManager(applicationContext)
		efm.store(SPKeys.passwords, Gson().toJson(passwords))
	}
	
	private fun writePasswordsToEncryptedSharedPreferences(applicationContext: Context) {
		val efm = EncryptedFileManager(applicationContext)
		efm.store(SPKeys.passwords, Gson().toJson(passwords))
	}
	
	fun emptyPasswords() {
		passwords = arrayOf(Password())
	}
	
	fun generateRandomPassword(applicationContext: Context): String {
		val efm = EncryptedFileManager(applicationContext)
		val length: Int = efm.read(SPKeys.settings_password_length).toInt()
		val numberOfSpecialCharacters: Int = efm.read(SPKeys.settings_include_symbols_number).toInt()
		val excludeSimilarCharacters: Boolean = efm.read(SPKeys.settings_exclude_similar_numbers).toBoolean()
		val lettersNumbers = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
		val specialCharacters = "#\$*+,-.:<=>?@\\^_~"
		val similarCharacters = "iIlL1oO0uvcemnwWbdpqsS5"
		
		var availableCharacters = lettersNumbers.filter { it !in similarCharacters }

//		if (numberOfSpecialCharacters > 0){
//			availableCharacters += specialCharacters
//		}
		
		if (!excludeSimilarCharacters) {
			availableCharacters += similarCharacters
		}
		
		println("availableCharacters: $availableCharacters")
		
		// Generate the first part of the string with random characters
		val randomPasswordWithoutSpecialCharacters = (1..length)
			.map { availableCharacters.random() }
			.joinToString("")
		
		
		println("randomPasswordWithoutSpecialCharacters: $randomPasswordWithoutSpecialCharacters")
		
		// Create a list of indices where replacements will occur
		val indicesToReplace = (randomPasswordWithoutSpecialCharacters.indices).shuffled().take(numberOfSpecialCharacters)
		
		// Replace characters at the selected indices with characters from the replacementCharacterSet
		val result = randomPasswordWithoutSpecialCharacters.mapIndexed { index, char ->
			if (index in indicesToReplace) {
				specialCharacters.random()
			} else {
				char
			}
		}.joinToString("")
		
		
		println("generatedPassword: $result")
		
		return result
	}
}
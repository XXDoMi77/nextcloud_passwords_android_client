package com.dominikdomotor.nextcloudpasswords.managers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.dataclasses.Data
import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Passwords
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.Shares
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.Collator
import java.util.Locale

typealias SM = StorageManager

object StorageManager {
	private var data:Data = Data()
	private var favicons: MutableMap<String, Bitmap> = mutableMapOf()
	
//	private var settings:Settings = Settings()
//	private var passwords: Passwords = Passwords()
//	private var favicons: MutableMap<String, Bitmap> = mutableMapOf()
//	private var partners: MutableMap<String, String> = mutableMapOf()
//	private lateinit var shares: Shares
	
	fun init() {
		synchronized(this) {
			if (EFM.exists(Keys.data)) {
				try {
					data = Gson().fromJson(EFM.read(Keys.data), Data::class.java)
				}
				catch (e:Exception){
					throw Exception("Failed to parse data into memory")
				}
			}
			if (EFM.exists(Keys.favicons)) {
				try {
					favicons = loadFaviconsFromString(EFM.read(Keys.favicons))
				}
				catch (e:Exception){
					throw Exception("Failed to parse favicons into memory")
				}
//				data.favicons = Gson().fromJson(EFM.read(Keys.data), Passwords::class.java)
			}
//			if (EFM.exists(Keys.settings)) {
//				data.settings = Gson().fromJson(EFM.read(Keys.settings), Settings::class.java)
//			}
//			if (EFM.exists(Keys.passwords)) {
//				data.passwords = Gson().fromJson(EFM.read(Keys.passwords), Passwords::class.java)
//				data.passwords.sortWith(compareBy(Collator.getInstance(Locale.getDefault())) { it.label })
//			}
//			if (EFM.exists(Keys.favicons)) {
////				data.favicons = loadFaviconsFromString(EFM.read(Keys.favicons))
//				data.favicons = Gson().fromJson(EFM.read(Keys.data), Passwords::class.java)
//			}
//			if (EFM.exists(Keys.partners)) {
//				data.partners = GsonBuilder().create().fromJson(EFM.read(Keys.partners), object : TypeToken<MutableMap<String, String>>() {})
//			}
//			if (EFM.exists(Keys.shares)) {
//				data.shares = Gson().fromJson(EFM.read(Keys.shares), Shares::class.java)
//			}
		}
	}
	
	private fun writeDataToStorage(){
		synchronized(this){
			EFM.store(Keys.data, Gson().toJson(data))
		}
	}
	
	fun updateSettings(updateBlock: (Settings) -> Unit) {
		synchronized(this) {
			updateBlock(data.settings)
			if (data.settings.basicAuth.isEmpty() && data.settings.loggedIn) {
				data.settings.basicAuth = "Basic " + String(Base64.encode("${data.settings.username}:${data.settings.token}".toByteArray(), Base64.NO_WRAP))
			}
			writeDataToStorage()
		}
	}
	
	fun setPasswords(updatedPasswords:Passwords) {
		synchronized(this) {
			data.passwords = updatedPasswords
			data.passwords.sortWith(compareBy(Collator.getInstance(Locale.getDefault())) { it.label })
			writeDataToStorage()
		}
	}
	
	fun updatePassword(updateBlock: (Passwords) -> Unit){
		synchronized(this){
			updateBlock(data.passwords)
			writeDataToStorage()
		}
	}
	
	fun addFavicon(id:String, bitmap:Bitmap){
		synchronized(this){
			favicons[id] = bitmap
			EFM.store(Keys.favicons, convertFaviconsToString(favicons))
		}
	}
	
	fun setPartners(updatedPartners:MutableMap<String, String>){
		synchronized(this){
			data.partners = updatedPartners
			writeDataToStorage()
		}
	}
	
	fun setShares(updatedShares:Shares){
		synchronized(this){
			data.shares = updatedShares
			writeDataToStorage()
		}
	}
	
	fun removePassword(password:Password){
		synchronized(this){
			data.passwords.removeIf { it == password }
			writeDataToStorage()
		}
	}
	
	fun getPasswords(): Passwords{
		return data.passwords
	}
	
	fun getFavicons(): MutableMap<String, Bitmap>{
		return favicons
	}
	
	fun getSettings(): Settings {
		return data.settings
	}
	
	fun getShares(): Shares{
		return data.shares
	}
	
	fun clearOfflinePasswordCache() {
		synchronized(this){
			data.passwords = Passwords()
			writeDataToStorage()
		}
	}
	
	fun clearFaviconCache(){
		synchronized(this){
			favicons = mutableMapOf()
			EFM.deleteFile(Keys.favicons)
		}
	}
	
	fun deleteAllData(){
		synchronized(this){
			data.settings = Settings()
			data.passwords = Passwords()
			data.partners = mutableMapOf()
			data.shares = Shares()
			favicons = mutableMapOf()
			EFM.deleteAllFiles()
		}
	}
	
	private fun convertFaviconsToString(favicons: MutableMap<String, Bitmap>): String {
		// Convert Bitmaps to Base64 strings
		val faviconsBase64: MutableMap<String, String> = mutableMapOf()
		var index = 0
		favicons.forEach { (key, value) ->
			val byteArrayOutputStream = ByteArrayOutputStream()
			value.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
			val bitmapData = byteArrayOutputStream.toByteArray()
			val base64 = Base64.encodeToString(bitmapData, Base64.DEFAULT)
			faviconsBase64[key] = base64
			index++
//			GF.prtln(index.toString() + ".\tconverting bitmaps to string " + key)
		}
		
		GF.println("favicon size in function: " + favicons.size)
		// Convert the faviconsBase64 map to a JSON string
		val gson = Gson()
		return gson.toJson(faviconsBase64)
	}
	
	private fun loadFaviconsFromString(jsonString: String): MutableMap<String, Bitmap> {
		// Convert the JSON string to a map of Base64 strings
		val gson = Gson()
		val type = object : TypeToken<MutableMap<String, String>>() {}.type
		val faviconsBase64: MutableMap<String, String> = gson.fromJson(jsonString, type)
		
		// Convert Base64 strings back to Bitmaps
		val favicons: MutableMap<String, Bitmap> = mutableMapOf()
		for ((key, value) in faviconsBase64) {
			val bitmapData = Base64.decode(value, Base64.DEFAULT)
			val inputStream: InputStream = ByteArrayInputStream(bitmapData)
			val bitmap = BitmapFactory.decodeStream(inputStream)
			favicons[key] = bitmap
		}
		
		return favicons
	}
}

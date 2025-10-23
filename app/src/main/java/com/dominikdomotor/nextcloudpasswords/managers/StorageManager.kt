package com.dominikdomotor.nextcloudpasswords.managers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.dominikdomotor.nextcloudpasswords.dataclasses.Data
import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Passwords
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.Shares
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.Collator
import java.util.Locale

@Singleton
class StorageManager
@Inject
constructor(
    private val encryptedFileManager: EncryptedFileManager,
) {
    private var data: Data = Data()
    private var favicons: MutableMap<String, Bitmap> = mutableMapOf()

    init {
        if (encryptedFileManager.exists(Keys.data)) {
            try {
                data = Gson().fromJson(encryptedFileManager.read(Keys.data), Data::class.java)
            } catch (e: Exception) {
                throw Exception("Failed to parse data into memory")
            }
        }
        if (encryptedFileManager.exists(Keys.favicons)) {
            try {
                favicons = loadFaviconsFromString(encryptedFileManager.read(Keys.favicons))
            } catch (e: Exception) {
                throw Exception("Failed to parse favicons into memory")
            }
        }
    }

    private fun writeDataToStorage() {
        synchronized(this) { encryptedFileManager.store(Keys.data, Gson().toJson(data)) }
    }

    fun updateSettings(updateBlock: (Settings) -> Unit) {
        synchronized(this) {
            updateBlock(data.settings)
            if (data.settings.basicAuth.isEmpty() && data.settings.loggedIn) {
                data.settings.basicAuth =
                    "Basic " +
                        String(
                            Base64.encode(
                                "${data.settings.username}:${data.settings.token}".toByteArray(),
                                Base64.NO_WRAP,
                            ),
                        )
            }
            writeDataToStorage()
        }
    }

    fun setPasswords(updatedPasswords: Passwords) {
        synchronized(this) {
            data.passwords = updatedPasswords
            data.passwords.sortWith(compareBy(Collator.getInstance(Locale.getDefault())) { it.label })
            writeDataToStorage()
        }
    }

    fun updatePassword(updateBlock: (Passwords) -> Unit) {
        synchronized(this) {
            updateBlock(data.passwords)
            writeDataToStorage()
        }
    }

    fun addFavicon(
        id: String,
        bitmap: Bitmap,
    ) {
        synchronized(this) {
            favicons[id] = bitmap
            encryptedFileManager.store(Keys.favicons, convertFaviconsToString(favicons))
        }
    }

    fun setPartners(updatedPartners: MutableMap<String, String>) {
        synchronized(this) {
            data.partners = updatedPartners
            writeDataToStorage()
        }
    }

    fun setShares(updatedShares: Shares) {
        synchronized(this) {
            data.shares = updatedShares
            writeDataToStorage()
        }
    }

    fun removePassword(password: com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password) {
        synchronized(this) {
            data.passwords.removeIf { it == password }
            writeDataToStorage()
        }
    }

    fun getPasswords(): Passwords = data.passwords

    fun getFavicons(): MutableMap<String, Bitmap> = favicons

    fun getSettings(): Settings = data.settings

    fun getShares(): Shares = data.shares

    fun clearOfflinePasswordCache() {
        synchronized(this) {
            data.passwords = Passwords()
            writeDataToStorage()
        }
    }

    fun clearFaviconCache() {
        synchronized(this) {
            favicons = mutableMapOf()
            encryptedFileManager.deleteFile(Keys.favicons)
        }
    }

    fun deleteAllData() {
        synchronized(this) {
            data.settings = Settings()
            data.passwords = Passwords()
            data.partners = mutableMapOf()
            data.shares = Shares()
            favicons = mutableMapOf()
            encryptedFileManager.deleteAllFiles()
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
        }

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

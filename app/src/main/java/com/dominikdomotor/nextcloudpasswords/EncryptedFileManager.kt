package com.dominikdomotor.nextcloudpasswords

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import java.io.File

class EncryptedFileManager(private val applicationContext: Context) {
	
	private val masterKey: MasterKey = MasterKey.Builder(applicationContext)
		.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
		.build()
	
	fun exists(filename: String): Boolean {
		val file = File(applicationContext.filesDir, filename)
		return file.exists()
	}
	
	fun store(filename: String, content: String) {
		try {
			val file = File(applicationContext.filesDir, filename)
			if (file.exists()) {
				file.delete()
			}
			
			val encryptedFile = EncryptedFile.Builder(
				applicationContext,
				file,
				masterKey,
				EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
			).build()
			
			encryptedFile.openFileOutput().use { outputStream ->
				outputStream.write(content.toByteArray())
			}
		} catch (e: Exception) {
			e.printStackTrace()
			println("Something went wrong when trying to store data")
		}
	}
	
	fun read(filename: String): String {
		try {
			val encryptedFile = EncryptedFile.Builder(
				applicationContext,
				File(applicationContext.filesDir, filename),
				masterKey,
				EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
			).build()
			
			return encryptedFile.openFileInput().use { inputStream ->
				inputStream.bufferedReader().use { it.readText() }
			}
		} catch (e: Exception) {
			e.printStackTrace()
			println("Something went wrong when trying to load data")
			return SPKeys.not_found
		}
	}
}

package com.dominikdomotor.nextcloudpasswords.managers

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.dominikdomotor.nextcloudpasswords.GF
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.File

@Singleton
class EncryptedFileManager @Inject constructor(@ApplicationContext private val applicationContext: Context) {

    private var encryptedFiles: MutableMap<String, EncryptedFile> = mutableMapOf()
    private val masterKey: MasterKey

    init {
        masterKey = MasterKey.Builder(applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        preloadAllFiles()
    }

    private fun preloadAllFiles() {
        try {
            val filesDir = applicationContext.filesDir
            val fileList = filesDir.listFiles()

            if (fileList != null) {
                GF.println(fileList.toString())
            }

            fileList?.forEach { file ->
                val filename = file.name
                // Check if the file is not a directory and not hidden
                if (file.isFile && !file.isHidden) {
                    // Initialize encryptedFile for each file
                    initializeEncryptedFile(filename)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            GF.println("Something went wrong when trying to preload all files")
        }
    }

    private fun initializeEncryptedFile(filename: String) {
        try {
            val encryptedFile =
                EncryptedFile.Builder(
                        applicationContext,
                        File(applicationContext.filesDir, filename),
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB)
                    .build()

            encryptedFiles[filename] = encryptedFile
        } catch (e: Exception) {
            e.printStackTrace()
            GF.println("Something went wrong when trying to initialize EncryptedFile for $filename")
        }
    }

    fun deleteAllFiles() {
        try {
            val directory = applicationContext.filesDir
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            GF.println("Something went wrong while trying to delete appdata")
        }
    }

    fun deleteFile(fileName: String) {
        try {
            val file = File(applicationContext.filesDir, fileName)

            if (file.exists()) {
                file.delete()
                GF.println("File $fileName deleted successfully.")
            } else {
                GF.println("File $fileName not found.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            GF.println("Something went wrong while trying to delete the file: $fileName")
        }
    }

    fun exists(filename: String): Boolean {
        val file = File(applicationContext.filesDir, filename)
        return file.exists()
    }

    fun read(filename: String): String {
        try {
            // Check if encryptedFile is null or not initialized for the given filename
            if (!encryptedFiles.containsKey(filename) || encryptedFiles[filename] == null) {
                initializeEncryptedFile(filename)
            }

            // Check if encryptedFile is still null, handle it appropriately
            if (encryptedFiles[filename] == null) {
                GF.println("Unable to initialize EncryptedFile for $filename. Returning default value.")
                return Keys.not_found
            }

            return encryptedFiles[filename]!!.openFileInput().use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            GF.println("Something went wrong when trying to load data for $filename")
            return Keys.not_found
        }
    }

    fun store(filename: String, content: String) {
        //		val startTime = System.currentTimeMillis()
        try {
            // Check if encryptedFile is null or not initialized for the given filename
            if (!encryptedFiles.containsKey(filename) || encryptedFiles[filename] == null) {
                initializeEncryptedFile(filename)
            }

            // Check if encryptedFile is still null, handle it appropriately
            if (encryptedFiles[filename] == null) {
                GF.println("Unable to initialize EncryptedFile for $filename. Unable to store data.")
                return
            }

            val encryptedFile = encryptedFiles[filename]!!

            // Use standard File operations instead of encryptedFile.file.exists()
            val file = File(applicationContext.filesDir, filename)
            if (file.exists()) {
                file.delete()
            }

            encryptedFile.openFileOutput().use { outputStream -> outputStream.write(content.toByteArray()) }
            //			GF.prtln(("Time to write to file in ms: " + (System.currentTimeMillis() - startTime).toString()))
        } catch (e: Exception) {
            e.printStackTrace()
            GF.println("Something went wrong when trying to store data for $filename")
        }
    }
}

package com.dominikdomotor.nextcloudpasswords.managers

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.dominikdomotor.nextcloudpasswords.GF
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores files in the app's private directory, encrypted with an AES-256-GCM key held in the Android Keystore. */
@Singleton
class EncryptedFileManager @Inject constructor(@param:ApplicationContext private val applicationContext: Context) {

    @Synchronized
    fun deleteAllFiles() {
        runCatching { applicationContext.filesDir.listFiles()?.forEach(::deleteRecursively) }
            .onFailure {
                it.printStackTrace()
                GF.println("Something went wrong while trying to delete appdata")
            }
    }

    @Synchronized
    fun deleteFile(fileName: String) {
        runCatching {
                val file = resolve(fileName)
                if (file.exists()) deleteRecursively(file)
            }
            .onFailure {
                it.printStackTrace()
                GF.println("Something went wrong while trying to delete the file: $fileName")
            }
    }

    fun exists(filename: String): Boolean = resolve(filename).exists()

    /** True only for a regular file, so a directory of the same name is not mistaken for content. */
    fun isFile(filename: String): Boolean = resolve(filename).isFile

    @Synchronized fun read(filename: String): String = readBytes(filename)?.decodeToString() ?: Keys.NOT_FOUND

    @Synchronized
    fun store(filename: String, content: String) {
        storeBytes(filename, content.toByteArray())
    }

    /** Returns the decrypted contents of [filename], or null when it is missing or unreadable. */
    @Synchronized
    fun readBytes(filename: String): ByteArray? {
        return try {
            val file = resolve(filename)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            if (bytes.startsWith(MAGIC)) {
                return decrypt(bytes)
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            GF.println("Something went wrong when trying to load data for $filename")
            null
        }
    }

    /** Writes [content] to [filename] atomically, creating parent directories as needed. */
    @Synchronized
    fun storeBytes(filename: String, content: ByteArray) {
        try {
            val file = resolve(filename)
            file.parentFile?.mkdirs()
            val temporaryFile = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(temporaryFile).use { outputStream ->
                outputStream.write(encrypt(content))
                outputStream.fd.sync()
            }
            Files.move(
                temporaryFile.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            GF.println("Something went wrong when trying to store data for $filename")
        }
    }

    private fun resolve(name: String) = File(applicationContext.filesDir, name)

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursively)
        file.delete()
    }

    private fun encrypt(content: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        return MAGIC + cipher.iv.size.toByte() + cipher.iv + cipher.doFinal(content)
    }

    private fun decrypt(bytes: ByteArray): ByteArray {
        val ivSize = bytes[MAGIC.size].toInt()
        val ivStart = MAGIC.size + 1
        require((ivSize in 12..16) && (bytes.size > ivStart + ivSize)) { "Invalid encrypted file format" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(128, bytes.copyOfRange(ivStart, ivStart + ivSize)),
        )
        return cipher.doFinal(bytes.copyOfRange(ivStart + ivSize, bytes.size))
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            return it
        }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
            }
            .generateKey()
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        (size > prefix.size) && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "npac_file_encryption_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val MAGIC = byteArrayOf(0x4e, 0x50, 0x41, 0x43, 0x01)
    }
}

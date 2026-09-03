package com.dominikdomotor.nextcloudpasswords.managers

import android.util.Base64
import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.GenericHash
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.charset.StandardCharsets
import org.json.JSONObject

data class CseKeychain(val keys: Map<String, ByteArray>, val currentKeyId: String)

@Singleton
class CseCryptoManager @Inject constructor() {
    // Lazy on purpose: constructing this loads the native libsodium binary, which is pure startup
    // cost for the majority of accounts that never use client-side encryption.
    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid(), StandardCharsets.UTF_8) }

    fun solveChallenge(passphrase: String, salts: List<String>): String {
        require(passphrase.length in 12..128) { "The E2E passphrase must contain 12 to 128 characters" }
        require(salts.size == 3) { "Invalid PWDv1r1 challenge" }
        val passwordSalt = decodeHex(salts[0])
        val genericHashKey = decodeHex(salts[1])
        val passwordHashSalt = decodeHex(salts[2])
        val input = passphrase.toByteArray(StandardCharsets.UTF_8) + passwordSalt
        val genericHash = ByteArray(GenericHash.BYTES_MAX)
        check(
            sodium.cryptoGenericHash(
                genericHash,
                genericHash.size,
                input,
                input.size.toLong(),
                genericHashKey,
                genericHashKey.size,
            )
        ) {
            "Unable to solve E2E challenge"
        }
        return encodeHex(deriveKey(genericHash, passwordHashSalt))
    }

    fun decryptKeychain(encryptedKeychain: String, passphrase: String): CseKeychain {
        val encoded = decodeHexOrBase64(encryptedKeychain)
        require(encoded.size > PwHash.SALTBYTES + SecretBox.NONCEBYTES + SecretBox.MACBYTES) {
            "Invalid CSEv1r1 keychain"
        }
        val salt = encoded.copyOfRange(0, PwHash.SALTBYTES)
        val decrypted =
            decryptSecretBox(encoded.copyOfRange(PwHash.SALTBYTES, encoded.size), deriveKey(passphrase, salt))
        val json = JSONObject(decrypted)
        val keysJson = json.getJSONObject("keys")
        val keys = buildMap { keysJson.keys().forEach { id -> put(id, decodeHex(keysJson.getString(id))) } }
        val current = json.getString("current")
        require(keys.containsKey(current)) { "Current CSE key is missing" }
        return CseKeychain(keys, current)
    }

    fun decrypt(password: Password, keychain: CseKeychain): Password {
        if (password.cseType != CSE_TYPE) return password
        val key = keychain.keys[password.cseKey] ?: error("Missing CSE key ${password.cseKey}")
        return password.copy(
            url = decryptIfPresent(password.url, key),
            label = decryptIfPresent(password.label, key),
            notes = decryptIfPresent(password.notes, key),
            password = decryptIfPresent(password.password, key),
            username = decryptIfPresent(password.username, key),
            customFields = decryptIfPresent(password.customFields, key),
        )
    }

    fun encrypt(password: Password, keychain: CseKeychain): Password {
        val key = keychain.keys.getValue(keychain.currentKeyId)
        return password.copy(
            url = encryptIfPresent(password.url, key),
            label = encryptIfPresent(password.label, key),
            notes = encryptIfPresent(password.notes, key),
            password = encryptIfPresent(password.password, key),
            username = encryptIfPresent(password.username, key),
            customFields = encryptIfPresent(password.customFields, key),
            cseKey = keychain.currentKeyId,
            cseType = CSE_TYPE,
        )
    }

    fun decrypt(folder: Folder, keychain: CseKeychain): Folder {
        if (folder.cseType != CSE_TYPE) return folder
        val key = keychain.keys[folder.cseKey] ?: error("Missing CSE key ${folder.cseKey}")
        return folder.copy(label = decryptIfPresent(folder.label, key))
    }

    fun encrypt(folder: Folder, keychain: CseKeychain): Folder {
        val key = keychain.keys.getValue(keychain.currentKeyId)
        return folder.copy(
            label = encryptIfPresent(folder.label, key),
            cseKey = keychain.currentKeyId,
            cseType = CSE_TYPE,
        )
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray =
        deriveKey(passphrase.toByteArray(StandardCharsets.UTF_8), salt)

    private fun deriveKey(password: ByteArray, salt: ByteArray): ByteArray {
        require(salt.size == PwHash.SALTBYTES) { "Invalid Argon2id salt" }
        val key = ByteArray(SecretBox.KEYBYTES)
        check(
            sodium.cryptoPwHash(
                key,
                key.size,
                password,
                password.size,
                salt,
                PwHash.OPSLIMIT_INTERACTIVE,
                PwHash.MEMLIMIT_INTERACTIVE,
                PwHash.Alg.getDefault(),
            )
        ) {
            "Unable to derive E2E key"
        }
        return key
    }

    private fun decryptIfPresent(value: String, key: ByteArray): String =
        if (value.isEmpty()) value else decryptSecretBox(decodeHexOrBase64(value), key)

    private fun encryptIfPresent(value: String, key: ByteArray): String =
        if (value.isEmpty()) value else encodeHex(encryptSecretBox(value, key))

    private fun decryptSecretBox(encoded: ByteArray, key: ByteArray): String {
        require(encoded.size >= SecretBox.NONCEBYTES + SecretBox.MACBYTES) { "Invalid encrypted value" }
        val nonce = encoded.copyOfRange(0, SecretBox.NONCEBYTES)
        val ciphertext = encoded.copyOfRange(SecretBox.NONCEBYTES, encoded.size)
        val message = ByteArray(ciphertext.size - SecretBox.MACBYTES)
        check(sodium.cryptoSecretBoxOpenEasy(message, ciphertext, ciphertext.size.toLong(), nonce, key)) {
            "Invalid E2E passphrase or corrupted data"
        }
        return String(message, StandardCharsets.UTF_8)
    }

    private fun encryptSecretBox(value: String, key: ByteArray): ByteArray {
        val message = value.toByteArray(StandardCharsets.UTF_8)
        val nonce = sodium.randomBytesBuf(SecretBox.NONCEBYTES)
        val ciphertext = ByteArray(message.size + SecretBox.MACBYTES)
        check(sodium.cryptoSecretBoxEasy(ciphertext, message, message.size.toLong(), nonce, key)) {
            "Unable to encrypt E2E value"
        }
        return nonce + ciphertext
    }

    private fun decodeHexOrBase64(value: String): ByteArray =
        runCatching { decodeHex(value) }.getOrElse { Base64.decode(value, Base64.DEFAULT) }

    private fun decodeHex(value: String): ByteArray {
        require(value.length % 2 == 0 && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "Invalid hexadecimal value"
        }
        return ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private fun encodeHex(value: ByteArray): String = value.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        const val CSE_TYPE = "CSEv1r1"
    }
}

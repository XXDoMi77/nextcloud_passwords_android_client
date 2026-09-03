package com.dominikdomotor.nextcloudpasswords.managers

import com.dominikdomotor.nextcloudpasswords.data.ApiResult
import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.managers.network.PasswordsApiClient
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

enum class E2eSessionResult {
    READY,
    PASSPHRASE_REQUIRED,
    INVALID_PASSPHRASE,
    UNSUPPORTED_CHALLENGE,
    FAILED,
}

/**
 * Opens and holds the Passwords API session, solving the `PWDv1r1` challenge when the account has client-side
 * encryption enabled.
 */
@Singleton
class E2eSessionManager
@Inject
constructor(
    private val storageManager: StorageManager,
    private val cryptoManager: CseCryptoManager,
    private val api: PasswordsApiClient,
) {
    private val mutex = Mutex()
    private val _passphraseRequired = MutableStateFlow(false)
    val passphraseRequired = _passphraseRequired.asStateFlow()

    @Volatile private var keychain: CseKeychain? = null
    @Volatile private var inMemoryPassphrase: String? = null
    @Volatile private var unencryptedAccountChecked = false

    suspend fun ensureSession(passphrase: String? = null, storePassphrase: Boolean = false): E2eSessionResult =
        mutex.withLock {
            if (keychain != null || unencryptedAccountChecked) return@withLock E2eSessionResult.READY

            val requirements =
                when (val result = api.request("session/request", parse = ::JSONObject)) {
                    is ApiResult.Success -> result.value
                    is ApiResult.Failure -> return@withLock E2eSessionResult.FAILED
                }

            if (!requirements.has("challenge")) {
                return@withLock openUnencryptedSession()
            }

            val challenge = requirements.getJSONObject("challenge")
            val tokenCount = requirements.optJSONArray("token")?.length() ?: 0
            if (challenge.optString("type") != CHALLENGE_TYPE || tokenCount > 0) {
                return@withLock E2eSessionResult.UNSUPPORTED_CHALLENGE
            }

            val selectedPassphrase = passphrase ?: inMemoryPassphrase ?: storageManager.settings.value.e2ePassphrase
            if (selectedPassphrase.isBlank()) {
                _passphraseRequired.value = true
                return@withLock E2eSessionResult.PASSPHRASE_REQUIRED
            }

            solveAndOpen(challenge, selectedPassphrase, passphrase != null, storePassphrase)
        }

    private suspend fun openUnencryptedSession(): E2eSessionResult {
        val opened =
            api.request("session/open", method = "POST", body = emptyMap<String, String>(), parse = ::JSONObject)
        val success = opened.valueOrNull()?.optBoolean("success") == true
        if (!success) return E2eSessionResult.FAILED
        unencryptedAccountChecked = true
        _passphraseRequired.value = false
        return E2eSessionResult.READY
    }

    private suspend fun solveAndOpen(
        challenge: JSONObject,
        passphrase: String,
        passphraseWasSuppliedByUser: Boolean,
        storePassphrase: Boolean,
    ): E2eSessionResult {
        val solution =
            try {
                val saltsJson = challenge.getJSONArray("salts")
                cryptoManager.solveChallenge(passphrase, List(saltsJson.length()) { saltsJson.getString(it) })
            } catch (_: IllegalArgumentException) {
                _passphraseRequired.value = true
                return E2eSessionResult.INVALID_PASSPHRASE
            } catch (_: Exception) {
                return E2eSessionResult.FAILED
            }

        val opened =
            api.request("session/open", method = "POST", body = mapOf("challenge" to solution), parse = ::JSONObject)
        val payload = opened.valueOrNull()
        if (payload == null || !payload.optBoolean("success")) {
            // The API rejects a wrong challenge with a non-success status, so this is the wrong passphrase.
            _passphraseRequired.value = true
            return E2eSessionResult.INVALID_PASSPHRASE
        }

        return try {
            val encryptedKeychain = payload.getJSONObject("keys").getString(CseCryptoManager.CSE_TYPE)
            keychain = cryptoManager.decryptKeychain(encryptedKeychain, passphrase)
            inMemoryPassphrase = passphrase
            storageManager.updateSettings {
                if (storePassphrase) it.e2ePassphrase = passphrase
                else if (passphraseWasSuppliedByUser) it.e2ePassphrase = ""
            }
            _passphraseRequired.value = false
            E2eSessionResult.READY
        } catch (_: IllegalArgumentException) {
            _passphraseRequired.value = true
            E2eSessionResult.INVALID_PASSPHRASE
        } catch (_: IllegalStateException) {
            _passphraseRequired.value = true
            E2eSessionResult.INVALID_PASSPHRASE
        } catch (_: Exception) {
            E2eSessionResult.FAILED
        }
    }

    fun decryptPasswords(passwords: List<Password>): List<Password> {
        val activeKeychain = keychain ?: return passwords
        return passwords.map { cryptoManager.decrypt(it, activeKeychain) }
    }

    fun decryptFolders(folders: List<Folder>): List<Folder> {
        val activeKeychain = keychain ?: return folders
        return folders.map { cryptoManager.decrypt(it, activeKeychain) }
    }

    fun encrypt(password: Password): Password = keychain?.let { cryptoManager.encrypt(password, it) } ?: password

    fun encrypt(folder: Folder): Folder = keychain?.let { cryptoManager.encrypt(folder, it) } ?: folder

    fun forgetStoredPassphrase() {
        storageManager.updateSettings { it.e2ePassphrase = "" }
    }

    fun dismissPassphrasePrompt() {
        _passphraseRequired.value = false
    }

    /** Drops the session so the next sync re-authenticates; the keychain passphrase is kept. */
    fun invalidateSession() {
        keychain = null
        unencryptedAccountChecked = false
        storageManager.apiSessionToken = ""
    }

    /** Drops the session and every trace of the passphrase. */
    fun reset() {
        invalidateSession()
        inMemoryPassphrase = null
        _passphraseRequired.value = false
    }

    private companion object {
        const val CHALLENGE_TYPE = "PWDv1r1"
    }
}

package com.dominikdomotor.nextcloudpasswords.managers

import android.util.Base64
import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.dataclasses.Data
import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.SharesItem
import com.google.gson.Gson
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The local source of truth: the encrypted data document plus the in-memory state derived from it.
 *
 * Everything is exposed as immutable [StateFlow] snapshots. Callers never receive the backing collections, so state can
 * only change through the mutator methods, each of which persists and republishes atomically.
 */
@Singleton
class StorageManager @Inject constructor(private val encryptedFileManager: EncryptedFileManager) {
    private val lock = Any()
    private var data: Data = Data()

    @Volatile private var loaded = false
    @Volatile var apiSessionToken: String = ""

    private val _passwords = MutableStateFlow<List<Password>>(emptyList())
    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    private val _shares = MutableStateFlow<List<SharesItem>>(emptyList())
    private val _settings = MutableStateFlow(Settings())

    val passwords: StateFlow<List<Password>> = _passwords.asStateFlow()
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()
    val shares: StateFlow<List<SharesItem>> = _shares.asStateFlow()
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    /**
     * Reads and decrypts the data document. Safe to call repeatedly; only the first call does work.
     *
     * A corrupt or undecryptable document is discarded rather than thrown, because this used to run in the constructor
     * and a failure there put the app into a crash loop with no way out.
     */
    suspend fun load() {
        if (loaded) return
        withContext(Dispatchers.IO) { synchronized(lock) { ensureLoadedLocked() } }
    }

    /**
     * Reads the document if that has not happened yet.
     *
     * Every mutator goes through this: a write that landed before [load] finished — the login service persisting
     * credentials, for instance — would otherwise save an empty document over the real one.
     */
    private fun ensureLoadedLocked() {
        if (loaded) return
        val startedAt = System.currentTimeMillis()
        data = readData() ?: Data()
        loaded = true
        migrateLegacyDefaults()
        publish()
        GF.println("Loaded ${data.passwords.size} passwords in ${System.currentTimeMillis() - startedAt} ms")
    }

    /**
     * Re-reads the document from disk, discarding in-memory state.
     *
     * The autofill service runs in its own process and therefore has its own instance of this class, so it needs an
     * explicit way to pick up writes made by the main process.
     */
    fun reloadFromStorage(): Boolean =
        synchronized(lock) {
            val fresh = readData() ?: return@synchronized false
            data = fresh
            loaded = true
            publish()
            true
        }

    fun updateSettings(updateBlock: (Settings) -> Unit) = mutate {
        updateBlock(data.settings)
        val settings = data.settings
        if (settings.basicAuth.isEmpty() && settings.loggedIn) {
            val credentials = "${settings.username}:${settings.token}".toByteArray()
            settings.basicAuth = "Basic " + String(Base64.encode(credentials, Base64.NO_WRAP))
        }
    }

    fun setPasswords(updated: List<Password>) = mutate {
        data.passwords = updated.sortedWith(compareBy(Collator.getInstance(Locale.getDefault())) { it.label })
    }

    fun setFolders(updated: List<Folder>) = mutate {
        data.folders = updated.filterNot { it.trashed }.sortedBy { it.label.lowercase() }
    }

    fun setShares(updated: List<SharesItem>) = mutate { data.shares = updated }

    /** Replaces the stored copy of [updated] by id, leaving every other password untouched. */
    fun replacePassword(updated: Password) = mutate {
        data.passwords = data.passwords.map { if (it.id == updated.id) updated else it }
    }

    fun removePassword(password: Password) = mutate {
        data.passwords = data.passwords.filterNot { it.id == password.id }
    }

    fun clearOfflinePasswordCache() = mutate { data.passwords = emptyList() }

    fun deleteAllData() =
        mutate(persist = false) {
            data = Data()
            apiSessionToken = ""
            encryptedFileManager.deleteAllFiles()
        }

    private inline fun mutate(persist: Boolean = true, block: () -> Unit) {
        synchronized(lock) {
            ensureLoadedLocked()
            block()
            if (persist) writeData()
            publish()
        }
    }

    private fun readData(): Data? {
        if (!encryptedFileManager.exists(Keys.DATA)) return null
        return runCatching { Gson().fromJson(encryptedFileManager.read(Keys.DATA), Data::class.java) }
            .onFailure { GF.println("Could not parse the stored data document; starting from empty state") }
            .getOrNull()
    }

    private fun writeData() {
        encryptedFileManager.store(Keys.DATA, Gson().toJson(data))
    }

    private fun migrateLegacyDefaults() {
        if (data.settings.includedSymbols == Settings.LEGACY_DEFAULT_SYMBOLS) {
            data.settings.includedSymbols = Settings.DEFAULT_SYMBOLS
            writeData()
        }
    }

    private fun publish() {
        _passwords.value = data.passwords
        _folders.value = data.folders
        _shares.value = data.shares
        _settings.value = data.settings.copy()
    }
}

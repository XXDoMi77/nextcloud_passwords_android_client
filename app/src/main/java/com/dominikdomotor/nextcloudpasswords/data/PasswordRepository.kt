package com.dominikdomotor.nextcloudpasswords.data

import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.SharesItem
import com.dominikdomotor.nextcloudpasswords.managers.CseCryptoManager
import com.dominikdomotor.nextcloudpasswords.managers.E2eSessionManager
import com.dominikdomotor.nextcloudpasswords.managers.E2eSessionResult
import com.dominikdomotor.nextcloudpasswords.managers.FaviconStore
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import com.dominikdomotor.nextcloudpasswords.managers.network.FaviconDownloader
import com.dominikdomotor.nextcloudpasswords.managers.network.PasswordsApiClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single entry point the UI uses for password data.
 *
 * It owns the "talk to the server, then update the local store" sequencing that used to be spread across
 * `NetworkManager`, and reports every outcome as an [ApiResult] rather than a callback that fired the same way on
 * success and failure.
 */
@Singleton
class PasswordRepository
@Inject
constructor(
    private val api: PasswordsApiClient,
    private val storageManager: StorageManager,
    private val faviconStore: FaviconStore,
    private val faviconDownloader: FaviconDownloader,
    private val e2eSessionManager: E2eSessionManager,
) {
    val passwords: StateFlow<List<Password>> = storageManager.passwords
    val folders: StateFlow<List<Folder>> = storageManager.folders
    val shares: StateFlow<List<SharesItem>> = storageManager.shares
    val settings: StateFlow<Settings> = storageManager.settings
    val passphraseRequired = e2eSessionManager.passphraseRequired

    private val syncMutex = Mutex()
    private val _isSyncing = MutableStateFlow(false)

    /** True while a refresh is in flight, wherever it was triggered from. */
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _showsSyncProgress = MutableStateFlow(false)

    /**
     * True while a sync the user should be able to see is running.
     *
     * Lives here rather than in a view model so the indicator survives a tab switch: pulling on the password list and
     * moving to the folder browser keeps showing progress in both.
     *
     * Not every sync sets it. Returning to the app from the background refreshes silently, because that happens on
     * every single switch back and a bar appearing each time is noise nobody asked for. A pull, an unlock and the one
     * sync that runs at start-up do set it - at start-up especially, since on a first run there is no cached data yet,
     * so without it the app is an empty screen that gives no sign of doing anything.
     */
    val isSyncVisible: StateFlow<Boolean> = _showsSyncProgress.asStateFlow()

    /**
     * Loads the encrypted data document. Idempotent; safe to call from every screen's start-up.
     *
     * Favicons are deliberately not part of this: decoding a few hundred cached PNGs took long enough to be visible as
     * a startup delay while the splash screen waited on it.
     */
    suspend fun load() = storageManager.load()

    /** One-off upgrade of the old single-blob favicon cache. Cheap when there is nothing to do. */
    suspend fun migrateFaviconCache() = faviconStore.migrateLegacyCache()

    /** Progressively decodes cached favicons, [orderedIds] first. See [FaviconStore.warmUp]. */
    suspend fun warmFavicons(orderedIds: List<String>) = faviconStore.warmUp(orderedIds)

    /**
     * Opens an API session if needed, then refreshes passwords, folders and shares.
     *
     * Serialised: the two list screens and the foreground refresh all call this, and running several at once would just
     * compete for the same endpoints.
     */
    suspend fun sync(showProgress: Boolean = false): ApiResult<Unit> {
        // Set before taking the lock: a pull that queues behind a background sync should still show
        // an indicator while it waits.
        if (showProgress) _showsSyncProgress.value = true
        try {
            return syncMutex.withLock {
                _isSyncing.value = true
                try {
                    syncLocked()
                } finally {
                    _isSyncing.value = false
                }
            }
        } finally {
            if (showProgress) _showsSyncProgress.value = false
        }
    }

    private suspend fun syncLocked(): ApiResult<Unit> {
        when (e2eSessionManager.ensureSession()) {
            E2eSessionResult.READY -> Unit
            E2eSessionResult.PASSPHRASE_REQUIRED -> return ApiResult.Failure(FailureReason.PASSPHRASE_REQUIRED)
            E2eSessionResult.INVALID_PASSPHRASE -> return ApiResult.Failure(FailureReason.PASSPHRASE_REQUIRED)
            E2eSessionResult.UNSUPPORTED_CHALLENGE -> return ApiResult.Failure(FailureReason.UNSUPPORTED_CHALLENGE)
            E2eSessionResult.FAILED -> return ApiResult.Failure(FailureReason.NETWORK)
        }

        pullPasswords().onFailure {
            return it
        }

        // Folders, shares and the server's theme are supporting data: report their failure but keep the
        // passwords we got.
        pullFolders()
        pullShares()
        pullServerTheme()
        return ApiResult.Success(Unit)
    }

    /**
     * Reads the colour the Nextcloud admin set in the Theming app.
     *
     * A different API to the rest of this class - the capabilities endpoint is core Nextcloud, not the Passwords app,
     * and it needs the OCS header - so it goes through [PasswordsApiClient.requestUrl] rather than `request`. The
     * colour is stored rather than applied directly, so the accent can be reset to it later without another call.
     */
    suspend fun pullServerTheme(): ApiResult<Unit> {
        val server = settings.value.server
        if (server.isBlank()) return ApiResult.Failure(FailureReason.UNAUTHORIZED)
        return api.requestUrl(
                URL("$server/ocs/v2.php/cloud/capabilities?format=json"),
                headers = mapOf("OCS-APIRequest" to "true"),
                parse = ::parseThemeColor,
            )
            .map { color -> storageManager.updateSettings { it.serverThemeColor = color } }
    }

    private fun parseThemeColor(json: String): String =
        Gson()
            .fromJson(json, JsonObject::class.java)
            .getAsJsonObject("ocs")
            ?.getAsJsonObject("data")
            ?.getAsJsonObject("capabilities")
            ?.getAsJsonObject("theming")
            ?.get("color")
            ?.asString
            .orEmpty()

    suspend fun pullPasswords(): ApiResult<Unit> =
        api.request("password/list", parse = { parseList<Password>(it) })
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .map { storageManager.setPasswords(e2eSessionManager.decryptPasswords(it)) }

    suspend fun pullFolders(): ApiResult<Unit> =
        api.request("folder/list", parse = { parseList<Folder>(it) })
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .map { storageManager.setFolders(e2eSessionManager.decryptFolders(it)) }

    suspend fun pullShares(): ApiResult<Unit> =
        api.request("share/list", parse = { parseList<SharesItem>(it) })
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .map(storageManager::setShares)

    /** Fetches any favicons that are not cached yet. Cancellable. */
    suspend fun downloadFavicons() = faviconDownloader.downloadMissing()

    // ---------------------------------------------------------------- passwords

    suspend fun createPassword(password: Password): ApiResult<Unit> {
        val payload = e2eSessionManager.encrypt(password.copy(hash = sha1(password.password)))
        return api.call("password/create", "POST", passwordBody(payload))
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .also { if (it.isSuccess) pullPasswords() }
    }

    suspend fun updatePassword(password: Password): ApiResult<Unit> {
        val payload = e2eSessionManager.encrypt(password.copy(hash = sha1(password.password)))
        val body = passwordBody(payload) + mapOf("id" to password.id, "favorite" to password.favorite)
        return api.call("password/update", "PATCH", body)
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .onSuccess { storageManager.replacePassword(password) }
    }

    suspend fun deletePassword(password: Password): ApiResult<Unit> =
        api.call("password/delete", "DELETE", mapOf("id" to password.id))
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .onSuccess { storageManager.removePassword(password) }

    private fun passwordBody(password: Password): Map<String, Any> =
        mapOf(
            "password" to password.password,
            "label" to password.label,
            "username" to password.username,
            "url" to password.url,
            "notes" to password.notes,
            "customFields" to password.customFields,
            "folder" to password.folder,
            "hash" to password.hash,
            "cseKey" to password.cseKey,
            "cseType" to password.cseType,
        )

    // ------------------------------------------------------------------ folders

    suspend fun createFolder(label: String, parent: String): ApiResult<Unit> {
        val folder = e2eSessionManager.encrypt(Folder(label = label, parent = parent))
        return api.call(
                "folder/create",
                "POST",
                mapOf(
                    "label" to folder.label,
                    "parent" to parent,
                    "cseKey" to folder.cseKey,
                    "cseType" to folder.cseType,
                ),
            )
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .also { if (it.isSuccess) pullFolders() }
    }

    suspend fun updateFolder(folder: Folder, label: String, parent: String): ApiResult<Unit> {
        val encrypted = e2eSessionManager.encrypt(folder.copy(label = label, parent = parent))
        return api.call(
                "folder/update",
                "PATCH",
                mapOf(
                    "id" to folder.id,
                    "label" to encrypted.label,
                    "parent" to parent,
                    "revision" to folder.revision,
                    "cseKey" to encrypted.cseKey,
                    "cseType" to encrypted.cseType,
                ),
            )
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .also { if (it.isSuccess) pullFolders() }
    }

    suspend fun deleteFolder(folder: Folder): ApiResult<Unit> =
        api.call("folder/delete", "DELETE", mapOf("id" to folder.id, "revision" to folder.revision))
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .also { if (it.isSuccess) pullFolders() }

    /**
     * Every folder that may not become [folder]'s parent: itself plus all of its descendants.
     *
     * Without this a folder could be moved underneath its own child, producing a cycle.
     */
    fun invalidParentsFor(folder: Folder): Set<String> = FolderTree.selfAndDescendants(folders.value, folder.id)

    // ------------------------------------------------------------------- shares

    suspend fun createShare(password: Password, receiverId: String): ApiResult<Unit> {
        if (password.cseType == CseCryptoManager.CSE_TYPE) {
            return ApiResult.Failure(FailureReason.SHARING_UNAVAILABLE)
        }
        return api.call(
                "share/create",
                "POST",
                mapOf("password" to password.id, "receiver" to receiverId, "editable" to false, "shareable" to false),
            )
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .also { if (it.isSuccess) pullShares() }
    }

    suspend fun updateShare(shareId: String, editable: Boolean, shareable: Boolean): ApiResult<Unit> =
        api.call("share/update", "PATCH", mapOf("id" to shareId, "editable" to editable, "shareable" to shareable))
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .also { if (it.isSuccess) pullShares() }

    suspend fun revokeShare(shareId: String): ApiResult<Unit> =
        api.call("share/delete", "DELETE", mapOf("id" to shareId))
            .also { it.failureOrNull()?.let(::handleSessionExpiry) }
            .also { if (it.isSuccess) pullShares() }

    suspend fun searchShareRecipients(search: String, limit: Int = 20): ApiResult<Map<String, String>> {
        val encoded = URLEncoder.encode(search, Charsets.UTF_8.name())
        val safeLimit = limit.coerceIn(1, 255)
        return api.request("share/partners?search=$encoded&limit=$safeLimit") {
            Gson().fromJson(it, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap()
        }
    }

    // ------------------------------------------------------------------ account

    /** Revokes the app password server-side and wipes all local state. */
    suspend fun logout(): ApiResult<Unit> {
        val server = settings.value.server
        val result =
            if (server.isBlank()) {
                ApiResult.Success(Unit)
            } else {
                api.requestUrl(
                    URL("$server/ocs/v2.php/core/apppassword"),
                    method = "DELETE",
                    headers = mapOf("OCS-APIREQUEST" to "true"),
                ) {}
            }

        // A token the server already rejected is just as gone as one we revoked, so wipe either way.
        val revoked = result.isSuccess || result.failureOrNull()?.reason == FailureReason.UNAUTHORIZED
        if (revoked) wipe()
        return if (revoked) ApiResult.Success(Unit) else result
    }

    suspend fun clearCaches() {
        storageManager.clearOfflinePasswordCache()
        faviconStore.clear()
    }

    private fun wipe() {
        e2eSessionManager.reset()
        storageManager.deleteAllData()
        faviconStore.forget()
    }

    /** A 412 means the API session is gone; drop it so the next call re-authenticates. */
    private fun handleSessionExpiry(failure: ApiResult.Failure) {
        if (failure.reason == FailureReason.SESSION_EXPIRED) e2eSessionManager.invalidateSession()
    }

    private inline fun <reified T> parseList(json: String): List<T> =
        Gson().fromJson(json, object : TypeToken<List<T>>() {}.type) ?: emptyList()

    private fun sha1(input: String): String =
        MessageDigest.getInstance("SHA-1").digest(input.toByteArray()).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
}

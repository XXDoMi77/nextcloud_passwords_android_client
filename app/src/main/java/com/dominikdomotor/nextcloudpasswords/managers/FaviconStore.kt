package com.dominikdomotor.nextcloudpasswords.managers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import com.dominikdomotor.nextcloudpasswords.GF
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Caches password favicons as individual encrypted files, decoded on demand.
 *
 * Two earlier designs were too slow. The original kept every favicon base64-encoded in one JSON blob and rewrote the
 * whole blob per download, making a sync O(n²). One file per favicon fixed writes, but eagerly decoding the entire
 * cache at start-up still cost about a second on a large account. Now nothing is decoded until a row actually asks for
 * it, and results are held in a memory-bounded cache.
 */
@Singleton
class FaviconStore @Inject constructor(private val encryptedFileManager: EncryptedFileManager) {
    /** Bounded by pixel memory rather than entry count, because favicon sizes vary. */
    private val cache =
        object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }

    /** Ids with no cached file, so repeated misses do not hit the disk on every bind. */
    private val known404 = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = UPDATE_BUFFER)

    /** Emits a password id whenever its favicon becomes available. */
    val updates: SharedFlow<String> = _updates.asSharedFlow()

    /** The decoded favicon if it is already in memory. Cheap; safe to call while binding a row. */
    fun cached(passwordId: String): Bitmap? = cache.get(passwordId)

    /** Decodes the favicon from disk if it is not in memory yet. Null when there is none. */
    suspend fun load(passwordId: String): Bitmap? {
        cache.get(passwordId)?.let {
            return it
        }
        if (passwordId in known404) return null
        return withContext(Dispatchers.IO) { decodeAndCache(passwordId) }
    }

    /**
     * Blocking decode, for callers with no coroutine scope.
     *
     * The autofill service runs in its own process and renders at most a handful of datasets.
     */
    fun peek(passwordId: String): Bitmap? = cache.get(passwordId) ?: decodeAndCache(passwordId)

    /**
     * Decodes every id in [orderedIds] that is not cached yet, one at a time, publishing each as it lands so its row
     * updates immediately.
     *
     * Deliberately not a batch: decoding the whole cache before showing anything cost about a second of start-up on a
     * large account. Pass the visible rows first so those appear straight away while the rest continue to fill in
     * behind them. Cancellable, and yields between entries so it never competes with scrolling.
     */
    suspend fun warmUp(orderedIds: List<String>) =
        withContext(Dispatchers.IO) {
            for (id in orderedIds) {
                currentCoroutineContext().ensureActive()
                if (cache.get(id) != null || id in known404) continue
                if (decodeAndCache(id) != null) _updates.tryEmit(id)
                yield()
            }
        }

    /** Whether a favicon file exists, without decoding it. Lets the downloader skip what it has. */
    fun hasStored(passwordId: String): Boolean = encryptedFileManager.exists(path(passwordId))

    /** Persists one favicon and tells any list showing it to rebind that row. */
    suspend fun put(passwordId: String, bitmap: Bitmap) {
        withContext(Dispatchers.IO) { encryptedFileManager.storeBytes(path(passwordId), bitmap.toPngBytes()) }
        cache.put(passwordId, bitmap)
        known404.remove(passwordId)
        _updates.tryEmit(passwordId)
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) { encryptedFileManager.deleteFile(Keys.FAVICON_DIRECTORY) }
        forget()
    }

    /** Drops the in-memory copies without touching disk; used when all app data is wiped. */
    fun forget() {
        cache.evictAll()
        known404.clear()
    }

    /**
     * Splits the pre-Preview-10 single-blob favicon cache into per-favicon files.
     *
     * Cheap when there is nothing to do: a single file-existence check.
     */
    suspend fun migrateLegacyCache() =
        withContext(Dispatchers.IO) {
            if (!encryptedFileManager.isFile(Keys.LEGACY_FAVICON_BLOB)) {
                // Anything else under that name (e.g. a directory left by an interrupted migration)
                // is not a blob and cannot be read as one.
                if (encryptedFileManager.exists(Keys.LEGACY_FAVICON_BLOB)) {
                    encryptedFileManager.deleteFile(Keys.LEGACY_FAVICON_BLOB)
                }
                return@withContext
            }

            // Decode everything before touching disk, so a failure part-way leaves the blob intact.
            val decoded =
                runCatching {
                        val json = encryptedFileManager.read(Keys.LEGACY_FAVICON_BLOB)
                        val type = object : TypeToken<Map<String, String>>() {}.type
                        val encoded: Map<String, String> = Gson().fromJson(json, type) ?: emptyMap()
                        encoded.mapValues { (_, base64) -> Base64.decode(base64, Base64.DEFAULT) }
                    }
                    .onFailure { GF.println("Could not read the legacy favicon cache; discarding it") }
                    .getOrDefault(emptyMap())

            decoded.forEach { (id, bytes) -> encryptedFileManager.storeBytes(path(id), bytes) }
            encryptedFileManager.deleteFile(Keys.LEGACY_FAVICON_BLOB)
            known404.clear()
            GF.println("Migrated ${decoded.size} favicons to per-file storage")
        }

    private fun decodeAndCache(passwordId: String): Bitmap? {
        val bytes = encryptedFileManager.readBytes(path(passwordId))
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bitmap == null) {
            known404.add(passwordId)
            return null
        }
        cache.put(passwordId, bitmap)
        return bitmap
    }

    private fun path(passwordId: String) = "${Keys.FAVICON_DIRECTORY}/$passwordId"

    private fun Bitmap.toPngBytes(): ByteArray =
        ByteArrayOutputStream().use {
            compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
            it.toByteArray()
        }

    private companion object {
        const val MAX_CACHE_BYTES = 8 * 1024 * 1024
        const val UPDATE_BUFFER = 128
        const val PNG_QUALITY = 100
    }
}

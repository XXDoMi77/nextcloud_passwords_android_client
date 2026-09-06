package com.dominikdomotor.nextcloudpasswords.managers.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.URLUtil
import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.data.Domains
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.managers.FaviconStore
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.net.URL
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Downloads the server-rendered favicon for each password that does not have one cached.
 *
 * The favicon endpoint is rate limited server-side, so this stays deliberately modest: a small concurrency limit, a
 * short timeout (an icon is not worth a minute of waiting), and a shared back-off that pauses *every* worker when the
 * server pushes back, honouring `Retry-After` when one is sent.
 *
 * A failure for one password no longer stops the batch. The original implementation called `executor.shutdownNow()`
 * from inside a worker on any non-2xx response, so a single missing icon cancelled every remaining download.
 */
@Singleton
class FaviconDownloader
@Inject
constructor(private val storageManager: StorageManager, private val faviconStore: FaviconStore) {
    /** Wall-clock time until which every worker holds off, set when the server rate limits us. */
    @Volatile private var pausedUntilMillis = 0L

    /** Fetches every missing favicon. Cancellable by the caller. */
    suspend fun downloadMissing() = coroutineScope {
        val server = storageManager.settings.value.server
        if (server.isBlank()) return@coroutineScope

        val semaphore = Semaphore(CONCURRENCY)
        storageManager.passwords.value
            .filterNot { faviconStore.hasStored(it.id) }
            .forEach { password -> launch { semaphore.withPermit { fetchWithRetry(server, password) } } }
    }

    private suspend fun fetchWithRetry(server: String, password: Password) {
        repeat(MAX_ATTEMPTS) { attempt ->
            awaitBackOff()
            when (val outcome = fetch(server, password)) {
                is Outcome.Fetched -> {
                    faviconStore.put(password.id, outcome.bitmap)
                    return
                }
                // Give up quietly: this password simply has no icon available.
                Outcome.Unavailable -> return
                is Outcome.RateLimited -> {
                    val wait = outcome.retryAfterMillis ?: backOffMillis(attempt)
                    pausedUntilMillis = maxOf(pausedUntilMillis, System.currentTimeMillis() + wait)
                    GF.println("Favicon endpoint rate limited; pausing ${wait}ms")
                }
            }
        }
    }

    private suspend fun awaitBackOff() {
        while (true) {
            val remaining = pausedUntilMillis - System.currentTimeMillis()
            if (remaining <= 0) return
            // Wake up periodically so cancellation is observed promptly.
            delay(min(remaining, MAX_SINGLE_DELAY_MILLIS))
        }
    }

    private suspend fun fetch(server: String, password: Password): Outcome =
        withContext(Dispatchers.IO) {
            val connection =
                runCatching { createAuthorizedConnection(faviconUrl(server, password), storageManager) }.getOrNull()
                    ?: return@withContext Outcome.Unavailable
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS

                when (val status = connection.responseCode) {
                    in 200..299 -> {
                        val bytes = connection.inputStream.use { it.readBytes() }
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) Outcome.Fetched(bitmap) else Outcome.Unavailable
                    }
                    HTTP_TOO_MANY_REQUESTS,
                    HTTP_SERVICE_UNAVAILABLE ->
                        Outcome.RateLimited(retryAfterMillis(connection.getHeaderField("Retry-After")))
                    else -> {
                        GF.println("No favicon for ${password.id} (HTTP $status)")
                        Outcome.Unavailable
                    }
                }
            } catch (_: Exception) {
                // One unavailable icon must not abort the rest of the batch.
                Outcome.Unavailable
            } finally {
                connection.disconnect()
            }
        }

    /** `Retry-After` in seconds; the HTTP-date form is ignored in favour of our own back-off. */
    private fun retryAfterMillis(header: String?): Long? =
        header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.times(1_000L)?.coerceAtMost(MAX_BACK_OFF_MILLIS)

    private fun backOffMillis(attempt: Int): Long = min(BASE_BACK_OFF_MILLIS shl attempt, MAX_BACK_OFF_MILLIS)

    /** The server renders an icon for a domain, or a letter tile from the label when there is no usable URL. */
    private fun faviconUrl(server: String, password: Password): URL {
        val path = "$server/index.php/apps/passwords/api/1.0/service/favicon"
        val host = registrableDomain(password.url)
        return URL(if (host != null) "$path/$host/32" else "$path/${password.label}/32")
    }

    private fun registrableDomain(url: String): String? {
        if (url.isEmpty() || !URLUtil.isValidUrl(url)) return null
        return Domains.registrableDomain(runCatching { URI(url).host }.getOrNull())
    }

    private sealed interface Outcome {
        data class Fetched(val bitmap: Bitmap) : Outcome

        /** No icon for this password; do not retry. */
        data object Unavailable : Outcome

        data class RateLimited(val retryAfterMillis: Long?) : Outcome
    }

    private companion object {
        /** Kept low on purpose: the server throttles bursts of favicon requests. */
        const val CONCURRENCY = 2
        const val MAX_ATTEMPTS = 4
        const val TIMEOUT_MILLIS = 15_000
        const val BASE_BACK_OFF_MILLIS = 2_000L
        const val MAX_BACK_OFF_MILLIS = 60_000L
        const val MAX_SINGLE_DELAY_MILLIS = 5_000L
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}

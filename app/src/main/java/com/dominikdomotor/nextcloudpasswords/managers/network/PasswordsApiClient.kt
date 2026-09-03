package com.dominikdomotor.nextcloudpasswords.managers.network

import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.data.ApiResult
import com.dominikdomotor.nextcloudpasswords.data.FailureReason
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import com.google.gson.Gson
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One place where every Passwords API call is issued.
 *
 * Centralising this fixes three problems the per-endpoint code had: connections were left undisconnected on five paths,
 * `try { launch { } } catch` blocks caught nothing because the body ran asynchronously, and failures were reported
 * through the same zero-argument callback as successes.
 */
@Singleton
class PasswordsApiClient @Inject constructor(private val storageManager: StorageManager) {
    /** Issues a request against `/index.php/apps/passwords/api/1.0/[path]`. */
    suspend fun <T> request(
        path: String,
        method: String = "GET",
        body: Any? = null,
        parse: (String) -> T,
    ): ApiResult<T> {
        val server = storageManager.settings.value.server
        if (server.isBlank()) return ApiResult.Failure(FailureReason.UNAUTHORIZED)
        return requestUrl(URL("$server/index.php/apps/passwords/api/1.0/$path"), method, body, parse = parse)
    }

    /** Issues a request against an arbitrary endpoint on the configured server. */
    suspend fun <T> requestUrl(
        url: URL,
        method: String = "GET",
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
        parse: (String) -> T,
    ): ApiResult<T> =
        withContext(Dispatchers.IO) {
            val connection =
                try {
                    createAuthorizedConnection(url, storageManager)
                } catch (e: Exception) {
                    return@withContext ApiResult.Failure(FailureReason.NETWORK, e)
                }

            try {
                connection.requestMethod = method
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                if (body != null) {
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    connection.outputStream.bufferedWriter().use { it.write(Gson().toJson(body)) }
                }

                val status = connection.responseCode
                GF.println("$method ${url.path} -> $status")
                captureSessionToken(connection)

                if (status !in 200..299) {
                    return@withContext ApiResult.Failure(failureFor(status))
                }

                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                try {
                    ApiResult.Success(parse(payload))
                } catch (e: Exception) {
                    GF.println("Could not parse the response for ${url.path}")
                    ApiResult.Failure(FailureReason.PARSE, e)
                }
            } catch (e: IOException) {
                ApiResult.Failure(FailureReason.NETWORK, e)
            } catch (e: Exception) {
                ApiResult.Failure(FailureReason.SERVER, e)
            } finally {
                connection.disconnect()
            }
        }

    /** Issues a request whose response body is not needed. */
    suspend fun call(path: String, method: String = "GET", body: Any? = null): ApiResult<Unit> =
        request(path, method, body) {}

    private fun failureFor(status: Int): FailureReason =
        when (status) {
            401,
            403 -> FailureReason.UNAUTHORIZED
            412 -> FailureReason.SESSION_EXPIRED
            else -> FailureReason.SERVER
        }

    private fun captureSessionToken(connection: HttpsURLConnection) {
        connection
            .getHeaderField("X-API-SESSION")
            ?.takeIf { it.isNotBlank() }
            ?.let { storageManager.apiSessionToken = it }
    }
}

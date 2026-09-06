package com.dominikdomotor.nextcloudpasswords.managers.network

import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val REQUEST_TIMEOUT_MILLIS = 60_000

/** Creates a connection carrying the stored credentials and, when present, the API session token. */
internal fun createAuthorizedConnection(url: URL, storageManager: StorageManager): HttpsURLConnection {
    val connection = createHttpsConnection(url, storageManager)

    connection.setRequestProperty("Authorization", storageManager.settings.value.basicAuth)
    storageManager.apiSessionToken
        .takeIf { it.isNotBlank() }
        ?.let { connection.setRequestProperty("X-API-SESSION", it) }
    connection.setRequestProperty("Connection", "keep-alive")
    connection.connectTimeout = REQUEST_TIMEOUT_MILLIS
    connection.readTimeout = REQUEST_TIMEOUT_MILLIS

    return connection
}

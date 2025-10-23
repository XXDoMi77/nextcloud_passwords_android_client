package com.dominikdomotor.nextcloudpasswords.managers.network

import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import java.net.URL
import javax.net.ssl.HttpsURLConnection

// Creates and configures a new HttpsURLConnection for every request.
internal fun createAuthorizedConnection(
    url: URL,
    storageManager: StorageManager,
): HttpsURLConnection {
    val connection = url.openConnection() as HttpsURLConnection

    // Setting up Authorization header
    connection.setRequestProperty("Authorization", storageManager.getSettings().basicAuth)
    connection.connectTimeout = 60000
    connection.readTimeout = 60000

    return connection
}

package com.dominikdomotor.nextcloudpasswords.managers.network

import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Passwords
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import com.dominikdomotor.nextcloudpasswords.managers.ToastManager
import com.google.gson.Gson
import java.io.InputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

@Singleton
class PasswordsDownloader
@Inject
constructor(
    private val toastManager: ToastManager,
    private val storageManager: StorageManager,
) {
    @Volatile
    var downloadRunning = false
        private set

    fun startDownload(func: () -> Unit) {
        // Check if download is already running
        if (downloadRunning) {
            GF.println("Password download is already running.")
            return
        }
        downloadRunning = true
        GF.println("Starting password download...")

        // Create URL for password list
        val path = "/index.php/apps/passwords/api/1.0/password/list"
        val url = URL("${storageManager.getSettings().server}$path")

        // Run on separate thread to not block UI
        Thread {
                val httpsConnection = createAuthorizedConnection(url)
                setUpHTTPSConnection(httpsConnection)
                try {
                    with(httpsConnection) {
                        when (responseCode) {
                            in 200..299 -> {
                                storePasswords(inputStream)
                                func()
                            }

                            412 -> {
                                // TODO Make E2E encryption work...
                            }

                            else -> {
                                toastManager.makeToast(R.string.something_went_wrong_try_again)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    downloadRunning = false
                    GF.println("Something went wrong while trying to pull passwords from server")
                } finally {
                    httpsConnection.disconnect()
                    downloadRunning = false
                    GF.println("Password download finished.")
                }
            }
            .start()
    }

    private fun createAuthorizedConnection(url: URL): HttpsURLConnection {
        val httpConnection = url.openConnection() as HttpsURLConnection
        httpConnection.setRequestProperty("Authorization", storageManager.getSettings().basicAuth)
        return httpConnection
    }

    private fun setUpHTTPSConnection(httpConnection: HttpsURLConnection) {
        httpConnection.requestMethod = "GET"
        httpConnection.setRequestProperty("Connection", "keep-alive")
    }

    private fun storePasswords(inputStream: InputStream) {
        storageManager.setPasswords(
            Gson()
                .fromJson(
                    inputStream.bufferedReader().use { it.readText() },
                    Passwords::class.java,
                ),
        )
        toastManager.makeToast(R.string.password_list_fetch_successful)
    }
}

package com.dominikdomotor.nextcloudpasswords.managers.network

import android.graphics.BitmapFactory
import android.webkit.URLUtil
import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Singleton
class FaviconDownloader
@Inject
constructor(
    private val storageManager: StorageManager,
) {
    @Volatile
    var downloadRunning = false
        private set

    private lateinit var executor: ExecutorService

    fun stopFaviconPull() {
        executor.shutdownNow()
        downloadRunning = false
    }

    // If password has URL property create regular URL, if not replace it with label, so that Nextcloud Passwords
    // generates an icon with the starting letter of label
    private fun assembleURL(password: Password): URL {
        val server = storageManager.getSettings().server
        val path = "/index.php/apps/passwords/api/1.0/service/favicon/"

        if (password.url.isNotEmpty() and URLUtil.isValidUrl(password.url)) {
            val faviconURL = URL(password.url)
            val strippedFaviconURL = faviconURL.host.split(".").takeLast(2).joinToString(".")

            return URL("$server$path$strippedFaviconURL/32")
        } else {
            return URL("$server$path${password.label}/32")
        }
    }

    private fun storeFavicon(
        password: Password,
        inputStream: InputStream,
    ) {
        val bytesRead = inputStream.readBytes()
        storageManager.addFavicon(
            password.id,
            BitmapFactory.decodeByteArray(
                bytesRead,
                0,
                bytesRead.size,
            ),
        )
    }

    private fun createDownloadWorker(
        onFaviconDownloadedFinished: () -> Unit,
        password: Password,
    ): Runnable = Runnable {
        val assembledURL = assembleURL(password)
        val httpsConnection = createAuthorizedConnection(assembledURL, storageManager)
        try {
            with(httpsConnection) {
                requestMethod = "GET"
                GF.println("\nSent $requestMethod request to URL: $assembledURL\nResponse Code : $responseCode")
                when (responseCode) {
                    in 200..299 -> {
                        storeFavicon(password, inputStream)
                        onFaviconDownloadedFinished()
                    }

                    else -> {
                        stopFaviconPull()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            httpsConnection.disconnect()
        }
    }

    fun startDownload(onEachFaviconDownloadFinished: () -> Unit) {
        // check if download is already running
        if (downloadRunning) {
            return
        }
        downloadRunning = true

        // create thread to not block UI
        Thread {
                try {
                    // create executor service with 4 threads
                    executor = Executors.newFixedThreadPool(4)

                    // get a copy of passwords from cache
                    val passwordsCache = storageManager.getPasswords()

                    // set up Runnable worker for each passwords Favicon
                    passwordsCache.forEach { password ->

                        // only download favicons if not already downloaded
                        val existingFavicon = storageManager.getFavicons()[password.id]
                        if (downloadRunning && (existingFavicon == null || existingFavicon.width <= 0)) {

                            // create worker
                            val worker = createDownloadWorker(onEachFaviconDownloadFinished, password)

                            // add worker to executor
                            executor.execute(worker)
                        }
                    }

                    // stop accepting any more workers
                    executor.shutdown()

                    // Wait for all tasks to complete, with a 1 minute timeout
                    if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                        downloadRunning = false
                        executor.shutdownNow() // Force shutdown if tasks are stuck.
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    GF.println("Something went wrong while trying to pull favicons from server")
                    downloadRunning = false
                }
            }
            .start()
    }
}

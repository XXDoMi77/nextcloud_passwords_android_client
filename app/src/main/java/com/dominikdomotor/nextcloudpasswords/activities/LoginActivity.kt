package com.dominikdomotor.nextcloudpasswords.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import jakarta.inject.Inject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginActivity : BaseActivity() {
    @Inject lateinit var storageManager: StorageManager

    private var pollUrl: String? = null
    private var token: String? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        startNextcloudLogin()
    }

    private fun startNextcloudLogin() {
        val serverUrl = intent.getStringExtra("server_URL").toString() + "/index.php/login/v2"

        CoroutineScope(Dispatchers.IO).launch {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val tokenName = "Nextcloud Passwords Android App - $deviceName"

            try {
                val url = URL(serverUrl)
                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("User-Agent", tokenName)
                connection.doOutput = true

                // Send empty POST request
                connection.outputStream.use { it.write(ByteArray(0)) }

                val responseCode = connection.responseCode
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val loginUrl = json.getString("login")
                    pollUrl = json.getJSONObject("poll").getString("endpoint")
                    token = json.getJSONObject("poll").getString("token")

                    openInExternalBrowser(loginUrl)
                    startPolling()
                } else {
                    Log.e("LoginActivity", "Unexpected response code: $responseCode")
                }
            } catch (e: IOException) {
                Log.e("LoginActivity", "Login request failed: ${e.message}")
            }
        }
    }

    private fun openInExternalBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun startPolling() {
        val pollingInterval: Long = 2000
        lateinit var pollRunnable: Runnable
        pollRunnable =
            object : Runnable {
                override fun run() {
                    if (pollUrl == null || token == null) return

                    Log.e("LoginActivity", "pollURL: $pollUrl")

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val url = URL(pollUrl)
                            val connection = url.openConnection() as HttpsURLConnection
                            connection.requestMethod = "POST"
                            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                            connection.doOutput = true

                            // Send token in the request body
                            val postData = "token=${token!!}".toByteArray(Charsets.UTF_8)
                            connection.outputStream.use { it.write(postData) }

                            val responseCode = connection.responseCode
                            if (responseCode == HttpsURLConnection.HTTP_OK) {
                                val response = connection.inputStream.bufferedReader().use { it.readText() }
                                val json = JSONObject(response)
                                val server = json.getString("server")
                                val username = json.getString("loginName")
                                val appPassword = json.getString("appPassword")

                                saveCredentials(server, username, appPassword)
                            } else if (responseCode == HttpsURLConnection.HTTP_NOT_FOUND) {
                                // Retry in 2 seconds if the endpoint returns 404
                                handler.postDelayed(pollRunnable, pollingInterval)
                            } else {
                                Log.e("LoginActivity", "Unexpected response code: $responseCode")
                                handler.postDelayed(pollRunnable, pollingInterval) // Retry in 2 seconds
                            }
                        } catch (e: IOException) {
                            Log.e("LoginActivity", "Polling failed: ${e.message}")
                            handler.postDelayed(pollRunnable, pollingInterval) // Retry in 2 seconds
                        }
                    }
                }
            }

        handler.postDelayed(pollRunnable, pollingInterval)
    }

    private fun saveCredentials(
        server: String,
        username: String,
        token: String,
    ) {
        storageManager.updateSettings {
            it.server = server
            it.username = username
            it.token = token
            it.loggedIn = true
        }

        val intent =
            Intent(this, OverviewActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        startActivity(intent)
        finish()
    }
}

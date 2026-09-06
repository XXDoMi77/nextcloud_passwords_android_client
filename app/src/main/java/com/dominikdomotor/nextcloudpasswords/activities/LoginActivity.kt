package com.dominikdomotor.nextcloudpasswords.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.data.LoginCallbackParser
import com.dominikdomotor.nextcloudpasswords.managers.network.createHttpsConnection
import com.dominikdomotor.nextcloudpasswords.services.LoginPollingService
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@AndroidEntryPoint
class LoginActivity : BaseActivity() {
    private var loginCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        setContentView(R.layout.activity_login)

        if (handleLoginCallback(intent.data)) {
            checkLoginAndFinish()
            return
        }

        val forceNew = intent.getBooleanExtra("force_new_login", false)
        if (!forceNew && storageManager.settings.value.loginInProgress) {
            Log.i("LoginActivity", "Login already in progress, showing waiting screen")
            return
        }

        startNextcloudLogin()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleLoginCallback(intent.data)) {
            checkLoginAndFinish()
        }
    }

    private fun checkLoginAndFinish() {
        if (storageManager.settings.value.loggedIn) {
            val intent =
                Intent(this, OverviewActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        if (!loginCompleted && isFinishing) {
            storageManager.updateSettings {
                it.loginInProgress = false
                it.pendingLoginServer = ""
            }
            stopService(Intent(this, LoginPollingService::class.java))
        }
        super.onDestroy()
    }

    private fun startNextcloudLogin() {
        val server = intent.getStringExtra("server_URL") ?: return
        val serverUrl = "$server/index.php/login/v2"

        // lifecycleScope, so the request is cancelled with the activity instead of outliving it.
        lifecycleScope.launch {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val tokenName = "Nextcloud Passwords Android App - $deviceName"

            try {
                withContext(Dispatchers.IO) {
                    val url = URL(serverUrl)
                    val connection = createHttpsConnection(url, storageManager)
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("User-Agent", tokenName)
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
                    connection.readTimeout = NETWORK_TIMEOUT_MILLIS
                    connection.doOutput = true

                    storageManager.updateSettings {
                        it.loginInProgress = true
                        it.pendingLoginServer = server
                    }

                    val redirectUri = "nc://login"
                    val postData =
                        "user_agent=${URLEncoder.encode(tokenName, "UTF-8")}" +
                            "&redirect_url=${URLEncoder.encode(redirectUri, "UTF-8")}" +
                            "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}"
                    connection.outputStream.use { it.write(postData.toByteArray()) }

                    val responseCode = connection.responseCode
                    if (responseCode == HttpsURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        Log.i("LoginActivity", "Login handshake response: $response")
                        val json = JSONObject(response)
                        val loginUrl = json.getString("login")
                        val pollUrl = json.getJSONObject("poll").getString("endpoint")
                        val token = json.getJSONObject("poll").getString("token")

                        startPollingService(pollUrl, token)
                        withContext(Dispatchers.Main) { openInExternalBrowser(loginUrl) }
                    } else {
                        storageManager.updateSettings {
                            it.loginInProgress = false
                            it.pendingLoginServer = ""
                        }
                        Log.e("LoginActivity", "Unexpected response code: $responseCode")
                    }
                }
            } catch (e: IOException) {
                storageManager.updateSettings {
                    it.loginInProgress = false
                    it.pendingLoginServer = ""
                }
                Log.e("LoginActivity", "Login request failed: ${e.message}")
            }
        }
    }

    private fun openInExternalBrowser(url: String) {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(this, url.toUri())
    }

    private fun startPollingService(endpoint: String, token: String) {
        val serviceIntent =
            Intent(this, LoginPollingService::class.java).apply {
                putExtra(LoginPollingService.EXTRA_ENDPOINT, endpoint)
                putExtra(LoginPollingService.EXTRA_TOKEN, token)
            }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    /**
     * Acts on an `nc://login/...` intent, if that is what arrived.
     *
     * The inline form carries credentials in the URL, and this activity is browsable on a custom scheme, so anything
     * that can open a link can deliver one. It is only acted on when it answers a login this app started, against the
     * server the user typed. Without both checks a link on any page would be enough to swap the app onto someone
     * else's Nextcloud: the list would be replaced by theirs on the next sync, autofill would offer their entries, and
     * anything created afterwards would be created there.
     *
     * A rejected callback is not an error the user has to see - nothing changed - so it is logged and dropped. If a
     * real login is still running, polling finishes it; if there is none, this activity was opened by the link alone
     * and has nothing to show.
     */
    private fun handleLoginCallback(uri: Uri?): Boolean =
        when (val result = LoginCallbackParser.parse(uri?.toString())) {
            is LoginCallbackParser.Result.NotACallback -> false
            is LoginCallbackParser.Result.ReturnedToApp -> true
            is LoginCallbackParser.Result.Credentials -> {
                val settings = storageManager.settings.value
                val answersOurLogin =
                    settings.loginInProgress && LoginCallbackParser.isSameOrigin(settings.pendingLoginServer, result.server)

                if (answersOurLogin) {
                    saveCredentials(result.server, result.username, result.appPassword)
                } else {
                    Log.w("LoginActivity", "Ignoring a login callback that does not answer a login started here")
                    if (!settings.loginInProgress) finish()
                }
                true
            }
        }

    private fun saveCredentials(server: String, username: String, token: String) {
        storageManager.updateSettings {
            it.server = server
            it.username = username
            it.token = token
            it.loggedIn = true
            it.loginInProgress = false
            it.pendingLoginServer = ""
        }

        loginCompleted = true
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

    private companion object {
        const val NETWORK_TIMEOUT_MILLIS = 15_000
    }
}

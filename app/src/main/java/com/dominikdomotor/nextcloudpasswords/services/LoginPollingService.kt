package com.dominikdomotor.nextcloudpasswords.services

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.activities.OverviewActivity
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import com.dominikdomotor.nextcloudpasswords.managers.network.createHttpsConnection
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

@AndroidEntryPoint
class LoginPollingService : Service() {
    @Inject lateinit var storageManager: StorageManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val endpoint = intent?.getStringExtra(EXTRA_ENDPOINT)
        val token = intent?.getStringExtra(EXTRA_TOKEN)
        if (endpoint.isNullOrBlank() || token.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification(R.string.login_in_progress))
        pollingJob?.cancel()
        pollingJob = serviceScope.launch { pollUntilComplete(endpoint, token, startId) }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun pollUntilComplete(endpoint: String, token: String, startId: Int) {
        val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MILLIS
        while (serviceScope.isActive && System.currentTimeMillis() < deadline) {
            val credentials = poll(endpoint, token)
            if (credentials != null) {
                completeLogin(credentials)
                stopSelf(startId)
                return
            }
            delay(POLL_INTERVAL_MILLIS)
        }
        storageManager.updateSettings { it.loginInProgress = false }
        stopSelf(startId)
    }

    private fun poll(endpoint: String, token: String): LoginCredentials? {
        val connection = createHttpsConnection(URL(endpoint), storageManager)
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.doOutput = true
            val body = "token=${URLEncoder.encode(token, Charsets.UTF_8.name())}"
            connection.outputStream.bufferedWriter().use { it.write(body) }

            if (connection.responseCode != HttpsURLConnection.HTTP_OK) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            LoginCredentials(
                server = json.getString("server"),
                username = json.getString("loginName"),
                appPassword = json.getString("appPassword"),
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    // The background-activity-start opt-in is deprecated but still the only supported way to bring
    // the app forward when the browser hands the login back.
    @Suppress("DEPRECATION")
    private fun completeLogin(credentials: LoginCredentials) {
        storageManager.updateSettings {
            it.server = credentials.server
            it.username = credentials.username
            it.token = credentials.appPassword
            it.loggedIn = true
            it.loginInProgress = false
        }

        val destination =
            Intent(this, OverviewActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val creatorOptions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                ActivityOptions.makeBasic().apply {
                    pendingIntentCreatorBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
            } else {
                null
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                destination,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                creatorOptions?.toBundle(),
            )
        notificationManager.notify(
            COMPLETION_NOTIFICATION_ID,
            createNotification(R.string.login_complete, pendingIntent, autoCancel = true),
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options =
                    ActivityOptions.makeBasic().apply {
                        pendingIntentBackgroundActivityStartMode =
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }
                pendingIntent.send(this, 0, null, null, null, null, options.toBundle())
            } else {
                pendingIntent.send()
            }
        } catch (_: Exception) {
            // The completion notification remains available when background launch is restricted.
        }
    }

    private fun createNotification(textResId: Int, contentIntent: PendingIntent? = null, autoCancel: Boolean = false) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_vpn_key_24)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(textResId))
            .setOngoing(!autoCancel)
            .setAutoCancel(autoCancel)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.login_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    private data class LoginCredentials(val server: String, val username: String, val appPassword: String)

    companion object {
        const val EXTRA_ENDPOINT = "login_poll_endpoint"
        const val EXTRA_TOKEN = "login_poll_token"
        private const val NOTIFICATION_CHANNEL_ID = "login"
        private const val NOTIFICATION_ID = 1001
        private const val COMPLETION_NOTIFICATION_ID = 1002
        private const val NETWORK_TIMEOUT_MILLIS = 15_000
        private const val POLL_INTERVAL_MILLIS = 2_000L
        private const val LOGIN_TIMEOUT_MILLIS = 20 * 60 * 1_000L
    }
}

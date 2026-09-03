package com.dominikdomotor.nextcloudpasswords.activities

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.ImageButton
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.managers.network.ServerCertificateInfo
import com.dominikdomotor.nextcloudpasswords.managers.network.createHttpsConnection
import com.dominikdomotor.nextcloudpasswords.managers.network.inspectServerCertificate
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog
import dagger.hilt.android.AndroidEntryPoint
import java.net.URL
import java.text.DateFormat
import java.util.Date
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import org.json.JSONObject

@AndroidEntryPoint
class EnterServerURLActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_enter_server_url)

        val urlInput = findViewById<EditText>(R.id.URL_input)

        urlInput.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        // check url, if ok open next activity otherwise try to guess url
        fun openLoginActivity() {
            urlInput.error = null

            // trying to guess the url
            if (!URLUtil.isValidUrl(urlInput.text.toString())) {
                urlInput.error = getString(R.string.not_a_valid_url_alert_message)
                urlInput.setText(
                    URLUtil.guessUrl(urlInput.text.toString().filter { !it.isWhitespace() })
                        .replace("http://www.", "https://", true)
                        .replace("http:", "https:", true)
                )
                urlInput.setSelection(urlInput.length()) // placing cursor at the end of the tex
                // whitespace at the end of the url results in the authentication process not
                // working, so
                // trying to remove them and letting the user know
            } else if (urlInput.text.toString().contains(" ")) {
                urlInput.error = getString(R.string.whitespaces_in_url_alert_message)
                urlInput.setText(urlInput.text.toString().filter { !it.isWhitespace() })
                urlInput.setSelection(urlInput.length()) // placing cursor at the end of the text

                // if everything is ok with the entered url the next activity is opened and the server url is passed
            } else if (URLUtil.isValidUrl(urlInput.text.toString())) {
                val serverUrl = URL(urlInput.text.toString().trimEnd('/'))
                if (!serverUrl.protocol.equals("https", ignoreCase = true)) {
                    urlInput.error = getString(R.string.https_is_required)
                } else {
                    checkNextcloudServer(serverUrl, urlInput, allowCertificatePrompt = true)
                }
            }
        }

        // handle keyboard enter press
        urlInput.setOnKeyListener(
            View.OnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                    openLoginActivity()
                    return@OnKeyListener true
                } else {
                    false
                }
            }
        )

        // handle button press
        findViewById<ImageButton>(R.id.enter_URL_button).setOnClickListener { openLoginActivity() }
    }

    private fun checkNextcloudServer(serverUrl: URL, urlInput: EditText, allowCertificatePrompt: Boolean) {
        Thread {
                try {
                    val statusUrl = URL("${serverUrl.toExternalForm().trimEnd('/')}/status.php")
                    val connection = createHttpsConnection(statusUrl, storageManager)
                    val isNextcloud =
                        try {
                            connection.requestMethod = "GET"
                            connection.doOutput = false
                            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
                            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
                            if (connection.responseCode == HttpsURLConnection.HTTP_OK) {
                                val status = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                                status.optBoolean("installed") &&
                                    status.optString("version").isNotBlank() &&
                                    status.optString("productname").contains("nextcloud", ignoreCase = true)
                            } else {
                                false
                            }
                        } finally {
                            connection.disconnect()
                        }

                    runOnUiThread {
                        if (isNextcloud) openLogin(serverUrl)
                        else urlInput.error = getString(R.string.this_URL_doesnt_seem_to_point_to_a_nextcloud_server)
                    }
                } catch (_: SSLHandshakeException) {
                    if (allowCertificatePrompt) showCertificatePrompt(serverUrl, urlInput)
                    else showServerError(urlInput)
                } catch (_: Exception) {
                    showServerError(urlInput)
                }
            }
            .start()
    }

    private fun showCertificatePrompt(serverUrl: URL, urlInput: EditText) {
        try {
            val certificate = inspectServerCertificate(serverUrl)
            runOnUiThread { showCertificateDialog(serverUrl, urlInput, certificate) }
        } catch (_: Exception) {
            showServerError(urlInput)
        }
    }

    private fun showCertificateDialog(serverUrl: URL, urlInput: EditText, certificate: ServerCertificateInfo) {
        val expiry = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(certificate.expiresAtMillis))
        AppDialog(this)
            .title(R.string.untrusted_certificate)
            .message(getString(R.string.untrusted_certificate_warning, serverUrl.host, certificate.sha256, expiry))
            .button(R.string.cancel)
            .button(R.string.trust_certificate) {
                storageManager.updateSettings {
                    it.trustedCertificateHost = serverUrl.host
                    it.trustedCertificateSha256 = certificate.sha256
                }
                checkNextcloudServer(serverUrl, urlInput, allowCertificatePrompt = false)
            }
            .showCompact()
    }

    private fun openLogin(serverUrl: URL) {
        val intent = Intent(this, LoginActivity::class.java)
        intent.putExtra("server_URL", serverUrl.toExternalForm())
        intent.putExtra("force_new_login", true)
        finish()
        startActivity(intent)
    }

    private fun showServerError(urlInput: EditText) {
        runOnUiThread { urlInput.error = getString(R.string.this_URL_doesnt_seem_to_point_to_a_nextcloud_server) }
    }

    companion object {
        private const val NETWORK_TIMEOUT_MILLIS = 15_000
    }
}

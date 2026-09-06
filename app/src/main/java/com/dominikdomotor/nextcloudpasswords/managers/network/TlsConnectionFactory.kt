package com.dominikdomotor.nextcloudpasswords.managers.network

import android.annotation.SuppressLint
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class ServerCertificateInfo(val sha256: String, val expiresAtMillis: Long)

fun createHttpsConnection(url: URL, storageManager: StorageManager): HttpsURLConnection {
    require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS is required" }
    val connection = url.openConnection() as HttpsURLConnection
    val settings = storageManager.settings.value
    if (
        url.host.equals(settings.trustedCertificateHost, ignoreCase = true) &&
            settings.trustedCertificateSha256.isNotBlank()
    ) {
        connection.sslSocketFactory = pinnedSocketFactory(settings.trustedCertificateSha256)
    }
    return connection
}

fun inspectServerCertificate(url: URL): ServerCertificateInfo {
    require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS is required" }
    val connection = url.openConnection() as HttpsURLConnection
    connection.sslSocketFactory = socketFactory(TrustAllCertificates)
    connection.hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
    connection.connectTimeout = CERTIFICATE_PROBE_TIMEOUT_MILLIS
    connection.readTimeout = CERTIFICATE_PROBE_TIMEOUT_MILLIS
    connection.instanceFollowRedirects = false
    return try {
        connection.connect()
        val certificate = connection.serverCertificates.first() as X509Certificate
        ServerCertificateInfo(sha256 = sha256(certificate), expiresAtMillis = certificate.notAfter.time)
    } finally {
        connection.disconnect()
    }
}

/**
 * Trusts exactly one certificate, by fingerprint.
 *
 * Stricter than the platform default rather than weaker: it accepts a single certificate the user was shown and
 * explicitly confirmed, and nothing else — not even a certificate a public CA would vouch for.
 */
@SuppressLint("CustomX509TrustManager")
private fun pinnedSocketFactory(expectedSha256: String) =
    socketFactory(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("Client certificates are not supported")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val certificate = chain?.firstOrNull() ?: throw CertificateException("Server sent no certificate")
                if (!sha256(certificate).equals(expectedSha256, ignoreCase = true)) {
                    throw CertificateException("Server certificate does not match the trusted fingerprint")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
    )

private fun socketFactory(trustManager: X509TrustManager) =
    SSLContext.getInstance("TLS")
        .apply { init(null, arrayOf<TrustManager>(trustManager), SecureRandom()) }
        .socketFactory

private fun sha256(certificate: X509Certificate): String =
    MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }

/**
 * Accepts any certificate, used only by [inspectServerCertificate].
 *
 * That call exists to read a fingerprint and show it to the user before they decide whether to trust it, which cannot
 * be done over a connection the platform has already rejected. It sends no request and carries no credentials; every
 * later connection to that host goes through [pinnedSocketFactory].
 */
@SuppressLint("CustomX509TrustManager")
private object TrustAllCertificates : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private const val CERTIFICATE_PROBE_TIMEOUT_MILLIS = 15_000

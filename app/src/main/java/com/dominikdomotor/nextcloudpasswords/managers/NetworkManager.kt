package com.dominikdomotor.nextcloudpasswords.managers

import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.Shares
import com.dominikdomotor.nextcloudpasswords.managers.network.FaviconDownloader
import com.dominikdomotor.nextcloudpasswords.managers.network.PasswordsDownloader
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

@Singleton
class NetworkManager
@Inject
constructor(
    private val toastManager: ToastManager,
    private val passwordsDownloader: PasswordsDownloader,
    private val faviconDownloader: FaviconDownloader,
    private val storageManager: StorageManager,
) {
    fun pullDataFromServer(
        onEverythingPulled: () -> Unit,
        onNewFaviconPulled: () -> Unit = {},
    ) {
        downloadPasswords {
            pullShares { onEverythingPulled() }
            downloadFavicons { onNewFaviconPulled() }
        }
        pullPartners {}
    }

    private fun downloadPasswords(func: () -> Unit) {
        passwordsDownloader.startDownload(func)
    }

    fun downloadFavicons(func: () -> Unit) {
        faviconDownloader.startDownload(func)
    }

    fun stopFaviconPull() {
        faviconDownloader.stopFaviconPull()
    }

    fun deletePassword(
        passwordToBeDeleted: Password,
        func: () -> Unit,
    ) {
        val strategy: ExclusionStrategy =
            object : ExclusionStrategy {
                override fun shouldSkipField(field: FieldAttributes): Boolean = field.name != "id"

                override fun shouldSkipClass(clazz: Class<*>?): Boolean = false
            }

        val gson = GsonBuilder().addSerializationExclusionStrategy(strategy).create()

        val jsonString = gson.toJson(passwordToBeDeleted)

        Thread {
                val url = URL("${storageManager.getSettings().server}/index.php/apps/passwords/api/1.0/password/delete")
                val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection

                httpConnection.requestMethod = "DELETE"
                httpConnection.setRequestProperty("Authorization", storageManager.getSettings().basicAuth)
                httpConnection.setRequestProperty("Connection", "keep-alive")
                httpConnection.setRequestProperty("Content-Type", "application/json")

                httpConnection.doOutput = true
                val outStream: OutputStream = httpConnection.outputStream
                val outStreamWriter = OutputStreamWriter(outStream, "UTF-8")
                outStreamWriter.write(jsonString)
                outStreamWriter.flush()
                outStreamWriter.close()
                outStream.close()

                if (httpConnection.responseCode in 200..299) {
                    toastManager.makeToast(R.string.password_successfully_deleted)

                    storageManager.removePassword(passwordToBeDeleted)

                    // 				passwords.removeIf { it.id == passwordToBeDeleted.id }
                    // 				sortAndSavePasswords()

                    func()
                } else {
                    toastManager.makeToast(R.string.something_went_wrong_try_again)
                }
                GF.println(
                    "Sent ${httpConnection.requestMethod} request to URL : $url; Response Code : ${httpConnection.responseCode}")
            }
            .start()
    }

    fun updatePassword(
        updatedPassword: Password,
        func: () -> Unit,
    ) {
        val strategy: ExclusionStrategy =
            object : ExclusionStrategy {
                override fun shouldSkipField(field: FieldAttributes): Boolean =
                    !(field.name == "id" ||
                        field.name == "password" ||
                        field.name == "label" ||
                        field.name == "username" ||
                        field.name == "url" ||
                        field.name == "notes" ||
                        field.name == "customFields" ||
                        field.name == "folder" ||
                        field.name == "favorite")

                override fun shouldSkipClass(clazz: Class<*>?): Boolean = false
            }

        val gson = GsonBuilder().addSerializationExclusionStrategy(strategy).create()
        val jsonString = gson.toJson(updatedPassword)

        Thread {
                val url = URL("${storageManager.getSettings().server}/index.php/apps/passwords/api/1.0/password/update")
                val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection

                httpConnection.requestMethod = "PATCH"
                httpConnection.setRequestProperty("Authorization", storageManager.getSettings().basicAuth)
                httpConnection.setRequestProperty("Connection", "keep-alive")
                httpConnection.setRequestProperty("Content-Type", "application/json")

                httpConnection.doOutput = true
                val outStream: OutputStream = httpConnection.outputStream
                val outStreamWriter = OutputStreamWriter(outStream, "UTF-8")
                outStreamWriter.write(jsonString)
                outStreamWriter.flush()
                outStreamWriter.close()
                outStream.close()

                if (httpConnection.responseCode in 200..299) {
                    toastManager.makeToast(R.string.password_successfully_updated)

                    storageManager.updatePassword {
                        it.forEachIndexed { index, password ->
                            if (password.id == updatedPassword.id) {
                                it[index].password = updatedPassword.password
                                it[index].label = updatedPassword.label
                                it[index].username = updatedPassword.username
                                it[index].url = updatedPassword.url
                                it[index].notes = updatedPassword.notes
                                it[index].customFields = updatedPassword.customFields
                                it[index].folder = updatedPassword.folder
                                it[index].favorite = updatedPassword.favorite
                            }
                        }
                    }

                    // 				passwords.forEachIndexed { index, password ->
                    // 					if (password.id == updatedPassword.id) {
                    // 						passwords[index] = updatedPassword
                    // 					}
                    // 				}
                    // 				EncryptedFileManager.store(Keys.passwords, Gson().toJson(passwords))
                    func()
                } else {
                    toastManager.makeToast(R.string.something_went_wrong_try_again)
                }
                GF.println(
                    "Sent ${httpConnection.requestMethod} request to URL : $url; Response Code : ${httpConnection.responseCode}")
            }
            .start()
    }

    fun createPassword(
        passwordToBeCreated: Password,
        func: () -> Unit,
    ) {
        try {
            Thread {
                    val strategy: ExclusionStrategy =
                        object : ExclusionStrategy {
                            override fun shouldSkipField(field: FieldAttributes): Boolean =
                                !(field.name == "password" ||
                                    field.name == "label" ||
                                    field.name == "username" ||
                                    field.name == "url" ||
                                    field.name == "notes" ||
                                    field.name == "customFields" ||
                                    field.name == "folder")

                            override fun shouldSkipClass(clazz: Class<*>?): Boolean = false
                        }

                    val passwordToBeCreatedWithHash: Password = passwordToBeCreated
                    passwordToBeCreatedWithHash.hash = sha1(passwordToBeCreated.password)

                    val gson = GsonBuilder().addSerializationExclusionStrategy(strategy).create()
                    val jsonString = gson.toJson(passwordToBeCreated)

                    GF.println(jsonString)

                    val httpConnection: HttpsURLConnection =
                        URL(
                                "${storageManager.getSettings().server}/index.php/apps/passwords/api/1.0/password/create",
                            )
                            .openConnection() as HttpsURLConnection

                    httpConnection.requestMethod = "POST"
                    httpConnection.setRequestProperty("Authorization", storageManager.getSettings().basicAuth)
                    httpConnection.setRequestProperty("Content-Type", "application/json")
                    httpConnection.setRequestProperty("Connection", "keep-alive")

                    httpConnection.doOutput = true
                    val outStream: OutputStream = httpConnection.outputStream
                    val outStreamWriter = OutputStreamWriter(outStream, "UTF-8")
                    outStreamWriter.write(jsonString)
                    outStreamWriter.flush()
                    outStreamWriter.close()
                    outStream.close()

                    when (httpConnection.responseCode) {
                        in 200..299 -> {
                            toastManager.makeToast(R.string.password_successfully_created)
                            // 						passwords.add(passwordToBeCreated)
                            // 						sortAndSavePasswords()
                            func()
                        }

                        412 -> {
                            // TODO Make E2E encryption work...
                        }

                        else -> {
                            // 						val answer = httpConnection.inputStream.bufferedReader().use { it.readText() }
                            // 						GF.prtln(answer)
                            toastManager.makeToast(R.string.something_went_wrong_try_again)
                        }
                    }
                }
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun pullPartners(func: () -> Unit) {
        try {
            Thread {
                    val url =
                        URL(
                            "${storageManager.getSettings().server}/index.php/apps/passwords/api/1.0/share/partners?limit=256")

                    val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
                    httpConnection.requestMethod = "GET"
                    httpConnection.setRequestProperty("Authorization", storageManager.getSettings().basicAuth)
                    httpConnection.setRequestProperty("Connection", "keep-alive")

                    when (httpConnection.responseCode) {
                        in 200..299 -> {
                            val builder = GsonBuilder()

                            storageManager.setPartners(
                                builder
                                    .create()
                                    .fromJson(
                                        httpConnection.inputStream.bufferedReader().use { it.readText() },
                                        object : TypeToken<MutableMap<String, String>>() {},
                                    ),
                            )
                            // 						EncryptedFileManager.store(Keys.partners, Gson().toJson(partners))
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
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pullShares(func: () -> Unit) {
        try {
            Thread {
                    val url = URL("${storageManager.getSettings().server}/index.php/apps/passwords/api/1.0/share/list")

                    val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
                    httpConnection.requestMethod = "GET"
                    httpConnection.setRequestProperty("Authorization", storageManager.getSettings().basicAuth)
                    httpConnection.setRequestProperty("Connection", "keep-alive")

                    when (httpConnection.responseCode) {
                        in 200..299 -> {
                            storageManager.setShares(
                                Gson()
                                    .fromJson(
                                        httpConnection.inputStream.bufferedReader().use { it.readText() },
                                        Shares::class.java,
                                    ),
                            )

                            // EncryptedFileManager.store(Keys.shares, Gson().toJson(shares))
                            // toastManager.makeToast(R.string.share_list_fetch_successful)
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
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logout(func: () -> Unit) {
        try {
            Thread {
                    val url = URL(storageManager.getSettings().server + "/ocs/v2.php/core/apppassword")

                    val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
                    httpConnection.requestMethod = "DELETE"
                    httpConnection.setRequestProperty("Authorization", storageManager.getSettings().basicAuth)
                    httpConnection.setRequestProperty("OCS-APIREQUEST", "true")
                    httpConnection.setRequestProperty("Connection", "keep-alive")

                    when (httpConnection.responseCode) {
                        in 200..299 -> {
                            storageManager.deleteAllData()
                            toastManager.makeToast(R.string.successfully_logged_out)
                            func()
                        }

                        401 -> {
                            storageManager.deleteAllData()
                            toastManager.makeToast(R.string.your_token_is_no_longer_valid_please_login_again)
                            func()
                        }

                        else -> {
                            toastManager.makeToast(R.string.something_went_wrong_try_again)
                            println("httpconnection.responsecode: " + httpConnection.responseCode)
                        }
                    }
                }
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

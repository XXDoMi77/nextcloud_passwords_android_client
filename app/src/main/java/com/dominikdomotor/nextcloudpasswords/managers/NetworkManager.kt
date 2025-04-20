package com.dominikdomotor.nextcloudpasswords.managers

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.webkit.URLUtil
import android.widget.Toast
import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Passwords
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.Shares
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.math.log

typealias NM = NetworkManager

object NetworkManager {
    private lateinit var applicationContext: Context

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun pullDataFromServer(onEverythingPulled: () -> Unit, onNewFaviconPulled: () -> Unit = {}) {
        pullPasswords {
            pullShares { onEverythingPulled() }
            pullFavicons { onNewFaviconPulled() }
        }
        pullPartners { }
    }

    private fun pullPasswords(func: () -> Unit) {
        GF.println("Pulling passwords...")
        try {
            Thread {
                val url =
                    URL("${SM.getSettings().server}/index.php/apps/passwords/api/1.0/password/list")
                val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
                httpConnection.requestMethod = "GET"
                httpConnection.setRequestProperty("Authorization", SM.getSettings().basicAuth)
                httpConnection.setRequestProperty("Connection", "keep-alive")

                when (httpConnection.responseCode) {
                    in 200..299 -> {
                        SM.setPasswords(
                            Gson().fromJson(
                                httpConnection.inputStream.bufferedReader().use { it.readText() },
                                Passwords::class.java
                            )
                        )
                        makeToast(R.string.password_list_fetch_successful)
                        func()
                    }

                    412 -> {
                        //TODO Make E2E encryption work...
                    }

                    else -> {
                        makeToast(R.string.something_went_wrong_try_again)
                    }
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
            GF.println("Something went wrong while trying to pull passwords from server")
        }
    }


    fun deletePassword(passwordToBeDeleted: Password, func: () -> Unit) {
        val strategy: ExclusionStrategy = object : ExclusionStrategy {
            override fun shouldSkipField(field: FieldAttributes): Boolean {
                return field.name != "id"
            }

            override fun shouldSkipClass(clazz: Class<*>?): Boolean {
                return false
            }
        }

        val gson = GsonBuilder()
            .addSerializationExclusionStrategy(strategy)
            .create()

        val jsonString = gson.toJson(passwordToBeDeleted)

        Thread {
            val url =
                URL("${SM.getSettings().server}/index.php/apps/passwords/api/1.0/password/delete")
            val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection

            httpConnection.requestMethod = "DELETE"
            httpConnection.setRequestProperty("Authorization", SM.getSettings().basicAuth)
            httpConnection.setRequestProperty("Connection", "keep-alive")
            httpConnection.setRequestProperty("Content-Type", "application/json");

            httpConnection.doOutput = true
            val outStream: OutputStream = httpConnection.outputStream
            val outStreamWriter = OutputStreamWriter(outStream, "UTF-8")
            outStreamWriter.write(jsonString)
            outStreamWriter.flush()
            outStreamWriter.close()
            outStream.close()

            if (httpConnection.responseCode in 200..299) {
                makeToast(R.string.password_successfully_deleted)

                SM.removePassword(passwordToBeDeleted)

//				passwords.removeIf { it.id == passwordToBeDeleted.id }
//				sortAndSavePasswords()

                func()
            } else {
                makeToast(R.string.something_went_wrong_try_again)
            }
            GF.println("\nSent ${httpConnection.requestMethod} request to URL : $url; Response Code : ${httpConnection.responseCode}")
        }.start()
    }


    fun updatePassword(updatedPassword: Password, func: () -> Unit) {
        val strategy: ExclusionStrategy = object : ExclusionStrategy {
            override fun shouldSkipField(field: FieldAttributes): Boolean {
                return !(field.name == "id" ||
                        field.name == "password" ||
                        field.name == "label" ||
                        field.name == "username" ||
                        field.name == "url" ||
                        field.name == "notes" ||
                        field.name == "customFields" ||
                        field.name == "folder" ||
                        field.name == "favorite")
            }

            override fun shouldSkipClass(clazz: Class<*>?): Boolean {
                return false
            }
        }

        val gson = GsonBuilder()
            .addSerializationExclusionStrategy(strategy)
            .create()
        val jsonString = gson.toJson(updatedPassword)

        Thread {

            val url =
                URL("${SM.getSettings().server}/index.php/apps/passwords/api/1.0/password/update")
            val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection

            httpConnection.requestMethod = "PATCH"
            httpConnection.setRequestProperty("Authorization", SM.getSettings().basicAuth)
            httpConnection.setRequestProperty("Connection", "keep-alive")
            httpConnection.setRequestProperty("Content-Type", "application/json");

            httpConnection.doOutput = true
            val outStream: OutputStream = httpConnection.outputStream
            val outStreamWriter = OutputStreamWriter(outStream, "UTF-8")
            outStreamWriter.write(jsonString)
            outStreamWriter.flush()
            outStreamWriter.close()
            outStream.close()

            if (httpConnection.responseCode in 200..299) {
                makeToast(R.string.password_successfully_updated)

                SM.updatePassword {
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

//				passwords.forEachIndexed { index, password ->
//					if (password.id == updatedPassword.id) {
//						passwords[index] = updatedPassword
//					}
//				}
//				EncryptedFileManager.store(Keys.passwords, Gson().toJson(passwords))
                func()

            } else {
                makeToast(R.string.something_went_wrong_try_again)
            }
            GF.println("\nSent ${httpConnection.requestMethod} request to URL : $url; Response Code : ${httpConnection.responseCode}")
        }.start()
    }


    fun createPassword(passwordToBeCreated: Password, func: () -> Unit) {
        try {
            Thread {
                val strategy: ExclusionStrategy = object : ExclusionStrategy {
                    override fun shouldSkipField(field: FieldAttributes): Boolean {
                        return !(field.name == "password" ||
                                field.name == "label" ||
                                field.name == "username" ||
                                field.name == "url" ||
                                field.name == "notes" ||
                                field.name == "customFields" ||
                                field.name == "folder")
                    }

                    override fun shouldSkipClass(clazz: Class<*>?): Boolean {
                        return false
                    }
                }

                val passwordToBeCreatedWithHash: Password = passwordToBeCreated
                passwordToBeCreatedWithHash.hash = sha1(passwordToBeCreated.password)

                val gson = GsonBuilder()
                    .addSerializationExclusionStrategy(strategy)
                    .create()
                val jsonString = gson.toJson(passwordToBeCreated)

                GF.println(jsonString)

                val httpConnection: HttpsURLConnection =
                    URL("${SM.getSettings().server}/index.php/apps/passwords/api/1.0/password/create").openConnection() as HttpsURLConnection

                httpConnection.requestMethod = "POST"
                httpConnection.setRequestProperty("Authorization", SM.getSettings().basicAuth)
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
                        makeToast(R.string.password_successfully_created)
//						passwords.add(passwordToBeCreated)
//						sortAndSavePasswords()
                        func()
                    }

                    412 -> {
                        //TODO Make E2E encryption work...
                    }

                    else -> {
//						val answer = httpConnection.inputStream.bufferedReader().use { it.readText() }
//						GF.prtln(answer)
                        makeToast(R.string.something_went_wrong_try_again)
                    }
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }



    private var pullFaviconsRunning = false

    private var executor: ExecutorService = Executors.newFixedThreadPool(4)

    fun stopFaviconPull(){
        executor.shutdownNow()
        pullFaviconsRunning = false
    }

    fun pullFavicons(func: () -> Unit) {
        if (!pullFaviconsRunning) {
            pullFaviconsRunning = true
            try {
                Thread {
                    val lock = Any()
                    var faviconsDownloaded = 0
//				val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

                    val passwordsCache = SM.getPasswords()
                    passwordsCache.forEach { password ->
                        if (!SM.getFavicons()
                                .containsKey(password.id) || SM.getFavicons()[password.id] == null
                        ) {
                            val worker = Runnable {
                                if (pullFaviconsRunning) {
                                    try {
//                                        val startTime =
//                                            System.currentTimeMillis() // Record start time

                                        val getFaviconURL: URL =
                                            if (password.url.isNotEmpty() and URLUtil.isValidUrl(
                                                    password.url
                                                )
                                            ) {
                                                URL(
                                                    "${SM.getSettings().server}/index.php/apps/passwords/api/1.0/service/favicon/" + URL(
                                                        password.url
                                                    ).host.split(".").takeLast(2)
                                                        .joinToString(".") + "/32"
                                                )
                                            } else {
                                                URL("${SM.getSettings().server}/index.php/apps/passwords/api/1.0/service/favicon/" + password.label + "/32")
                                            }
                                        with(getFaviconURL.openConnection() as HttpsURLConnection) {
                                            setRequestProperty(
                                                "Authorization",
                                                SM.getSettings().basicAuth
                                            )
                                            requestMethod = "GET"
                                            connectTimeout = 60000
                                            readTimeout = 60000
                                            GF.println("\nSent $requestMethod request to URL : $getFaviconURL; Response Code : $responseCode")
                                            if (responseCode in 200..299) {
                                                val bytesRead = inputStream.readBytes()
                                                synchronized(lock) {
                                                    SM.addFavicon(
                                                        password.id,
                                                        BitmapFactory.decodeByteArray(
                                                            bytesRead,
                                                            0,
                                                            bytesRead.size
                                                        )
                                                    )
//											favicons[password.id] = BitmapFactory.decodeByteArray(bytesRead, 0, bytesRead.size)
                                                }
                                            }
                                            else{
                                                stopFaviconPull()
                                            }
                                        }

//                                        val endTime = System.currentTimeMillis() // Record end time
//                                        val duration = endTime - startTime

//                                        if (duration < 1100) {  // Sleep only if the download took less than 2 seconds
//                                            Thread.sleep(1100 - duration)
//                                        }

                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    faviconsDownloaded++
                                    try {
                                        func()
                                    }
                                    catch (e: Exception){
                                        e.printStackTrace()
                                    }
                                }
                            }
                            try {
                                if (executor.isShutdown || executor.isTerminated) {
                                    executor = Executors.newFixedThreadPool(4)
                                }
                                executor.execute(worker)
                            }
                            catch (e: Exception){
                                e.printStackTrace()
                            }
                        }
                    }
                    executor.shutdown()
                    while (!executor.isTerminated) {
                        // Wait for all tasks to complete
                        try {
                            TimeUnit.MILLISECONDS.sleep(100) // Add a small delay
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                    pullFaviconsRunning = false

                    //if (executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS) && faviconsDownloaded != 0) {
                    //	EncryptedFileManager.store(Keys.favicons, convertFaviconsToString(favicons))
                    //}

                }.start()

            } catch (e: Exception) {
                e.printStackTrace()
                GF.println("Something went wrong while trying to pull favicons from server")
                pullFaviconsRunning = false
            }

        }
    }

    private fun pullPartners(func: () -> Unit) {
        try {
            Thread {
                val url =
                    URL("${SM.getSettings().server}/index.php/apps/passwords/api/1.0/share/partners?limit=256")

                val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
                httpConnection.requestMethod = "GET"
                httpConnection.setRequestProperty("Authorization", SM.getSettings().basicAuth)
                httpConnection.setRequestProperty("Connection", "keep-alive")

                when (httpConnection.responseCode) {
                    in 200..299 -> {

                        val builder = GsonBuilder()

                        SM.setPartners(
                            builder.create().fromJson(
                                httpConnection.inputStream.bufferedReader().use { it.readText() },
                                object : TypeToken<MutableMap<String, String>>() {})
                        )
//						EncryptedFileManager.store(Keys.partners, Gson().toJson(partners))
                        func()
                    }

                    412 -> {
                        //TODO Make E2E encryption work...
                    }

                    else -> {
                        makeToast(R.string.something_went_wrong_try_again)
                    }
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun pullShares(func: () -> Unit) {
        try {
            Thread {
                val url =
                    URL("${SM.getSettings().server}/index.php/apps/passwords/api/1.0/share/list")

                val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
                httpConnection.requestMethod = "GET"
                httpConnection.setRequestProperty("Authorization", SM.getSettings().basicAuth)
                httpConnection.setRequestProperty("Connection", "keep-alive")

                when (httpConnection.responseCode) {
                    in 200..299 -> {

                        SM.setShares(
                            Gson().fromJson(
                                httpConnection.inputStream.bufferedReader().use { it.readText() },
                                Shares::class.java
                            )
                        )

//						EncryptedFileManager.store(Keys.shares, Gson().toJson(shares))
                        makeToast(R.string.share_list_fetch_successful)
                        func()
                    }

                    412 -> {
                        //TODO Make E2E encryption work...
                    }

                    else -> {
                        makeToast(R.string.something_went_wrong_try_again)
                    }
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logout(func: () -> Unit) {
        try {
            Thread {
                val url = URL(SM.getSettings().server + "/ocs/v2.php/core/apppassword")

                val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
                httpConnection.requestMethod = "DELETE"
                httpConnection.setRequestProperty("Authorization", SM.getSettings().basicAuth)
                httpConnection.setRequestProperty("OCS-APIREQUEST", "true")
                httpConnection.setRequestProperty("Connection", "keep-alive")

                when (httpConnection.responseCode) {
                    in 200..299 -> {
                        SM.deleteAllData()
                        makeToast(R.string.successfully_logged_out)
                        func()
                    }

                    401 -> {
                        SM.deleteAllData()
                        makeToast(R.string.your_token_is_no_longer_valid_please_login_again)
                        func()
                    }

                    else -> {
                        makeToast(R.string.something_went_wrong_try_again)
                        println("httpconnection.responsecode: " + httpConnection.responseCode)
                    }
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun makeToast(message: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                applicationContext.getString(message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
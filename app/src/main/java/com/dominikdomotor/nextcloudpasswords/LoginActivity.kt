package com.dominikdomotor.nextcloudpasswords

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import java.util.Locale


class LoginActivity : AppCompatActivity() {
	@SuppressLint("SetJavaScriptEnabled") override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
//		window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
		supportActionBar?.hide()
		setContentView(R.layout.activity_login)
		
		//just some variables...
		val map = mutableMapOf<String, String>()
		val myWebView = findViewById<WebView>(R.id.login_webview)
		
		//additionalHttpHeaders, needed for the token request this lets Nextcloud know that we want to get a token
		map["OCS-APIREQUEST"] = "true"
		
		//enabling javascript and setting user agent which will be the name of the token
		
		myWebView.settings.userAgentString = "Nextcloud Passwords App " + getString(R.string.on)
		try {
			if (Build.BRAND.isNotEmpty())
				myWebView.settings.userAgentString += " " + Build.BRAND.toString()
					.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
			else
			if (Build.MANUFACTURER.isNotEmpty())
				myWebView.settings.userAgentString += " " + Build.MANUFACTURER.toString()
					.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
			if (Build.MODEL.isNotEmpty())
				myWebView.settings.userAgentString += " " + Build.MODEL.toString()
			else
			if (Build.DEVICE.isNotEmpty())
				myWebView.settings.userAgentString += " " + Build.DEVICE.toString()
		} catch (e:Exception){
			e.printStackTrace()
		}
		myWebView.settings.javaScriptEnabled = true
		
		//opens the password overview screen
		fun openPasswordsScreen() {
			val intent = Intent(this, OverviewActivity::class.java)
//            intent.putExtra("key", value)
			finish()
			startActivity(intent)
		}
		
		//separates the username and the token from the redirect which was initiated by nextcloud
		fun saveUsernameAndToken(redirect: String) {
			val server = redirect.substringAfter("server:").substringBefore("&user:")
			val username = redirect.substringAfter("&user:").substringBefore("&password:")
			val token = redirect.substringAfter("&password:")

//			println("Received token, username: $username\t token: $token")
			
			try {
				this.runOnUiThread{
					SharedPreferencesManager.getSharedPreferences().edit().putString(SPKeys.server, server).commit()
					SharedPreferencesManager.getSharedPreferences().edit().putString(SPKeys.username, username).commit()
					SharedPreferencesManager.getSharedPreferences().edit().putString(SPKeys.token, token).commit()
					SharedPreferencesManager.getSharedPreferences().edit().putBoolean(SPKeys.logged_in, true).commit()
					
					println("1234" + SharedPreferencesManager.getSharedPreferences().getString(SPKeys.server, SPKeys.not_found))
					println("1234" + SharedPreferencesManager.getSharedPreferences().getString(SPKeys.username, SPKeys.not_found))
					println("4567" + SharedPreferencesManager.getSharedPreferences().getString(SPKeys.token, SPKeys.not_found))
					println("1234" + SharedPreferencesManager.getSharedPreferences().getString(SPKeys.logged_in, SPKeys.not_found))
				}
				
			} catch (exception: Exception) {
				println(exception)
			}
		}
		
		myWebView.webViewClient = object : WebViewClient() {
			@Deprecated("Deprecated in Java") override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
				val url2 = intent.getStringExtra("server_URL").toString()
				// all links  with in ur site will be open inside the webview
				//links that start ur domain example(http://www.example.com/)
				
				println(url)
				
				if (url.startsWith("nc://login/server:")) {
					saveUsernameAndToken(url)
					openPasswordsScreen()
					return false
				}
				
				return if (url.startsWith(url2)) {
					false
				} else {
					false
//					view.context.startActivity(
//						Intent(Intent.ACTION_VIEW, Uri.parse(url))
//					)
//					true
				}
			}
		}
		
		findViewById<WebView>(R.id.login_webview).loadUrl(intent.getStringExtra("server_URL").toString() + "/index.php/login/flow", map)
	}
}
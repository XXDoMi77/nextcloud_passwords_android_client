package com.dominikdomotor.nextcloudpasswords.activities

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dominikdomotor.nextcloudpasswords.R
import java.net.URL
import javax.net.ssl.HttpsURLConnection


class EnterServerURLActivity : BaseActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
//		window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
		supportActionBar?.hide()
		setContentView(R.layout.activity_enter_server_url)

		//just some variables...
		val urlInput = findViewById<EditText>(R.id.URL_input)

		urlInput.requestFocus()
		window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

		//check url, if ok open next activity otherwise try to guess url
		fun openLoginActivity() {
			// trying to guess the url
			if (!URLUtil.isValidUrl(urlInput.text.toString())) {
				runOnUiThread{
					Toast.makeText(this, getString(R.string.not_a_valid_url_alert_message), Toast.LENGTH_LONG).show()
				}
				urlInput.setText(
					URLUtil.guessUrl(urlInput.text.toString().filter { !it.isWhitespace() }).replace("http://www.", "https://", true)
						.replace("http:", "https:", true)//.dropLastWhile { it == '/' || it.isWhitespace() }
				)
				urlInput.setSelection(urlInput.length())//placing cursor at the end of the tex
				// whitespace at the end of the url results in the authentication process not working, so trying to remove them and letting the user know
			} else if (urlInput.text.toString().contains(" ")) {
				runOnUiThread{
					Toast.makeText(this, getString(R.string.whitespaces_in_url_alert_message), Toast.LENGTH_LONG).show()
				}
				urlInput.setText(urlInput.text.toString().filter { !it.isWhitespace() })
				urlInput.setSelection(urlInput.length())//placing cursor at the end of the text
				// if everything is ok with the entered url the next activity is opened and the server url is passed
			} else if (URLUtil.isValidUrl(urlInput.text.toString())) {
				Thread {
					try {

						val url = URL(urlInput.text.toString() + "/ocs/v1.php")
						val httpConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
						httpConnection.requestMethod = "GET"
						httpConnection.doOutput = false
						if (httpConnection.responseCode in 200..299) {
							runOnUiThread {
								val intent = Intent(this, LoginActivity::class.java)
								intent.putExtra("server_URL", urlInput.text.toString())
								finish()
								startActivity(intent)
							}
						} else {
							runOnUiThread{
								Toast.makeText(this, getString(R.string.this_URL_doesnt_seem_to_point_to_a_nextcloud_server), Toast.LENGTH_LONG).show()
							}
						}
					} catch (e: Exception) {
						runOnUiThread {
							Toast.makeText(this, getString(R.string.this_URL_doesnt_seem_to_point_to_a_nextcloud_server), Toast.LENGTH_LONG).show()
						}
					}
				}.start()
			}
		}

		//handle keyboard enter press
		urlInput.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
			if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
				openLoginActivity()
				return@OnKeyListener true
			} else {
				false
			}
		})

		//handle button press
		findViewById<ImageButton>(R.id.enter_URL_button).setOnClickListener {
			openLoginActivity()
		}
	}
}
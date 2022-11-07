package com.dominikdomotor.nextcloudpasswords.ui.account

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.dominikdomotor.nextcloudpasswords.*
import com.dominikdomotor.nextcloudpasswords.databinding.FragmentAccountAndSettingsBinding
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.Password
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection


class AccountFragment : Fragment() {
	
	private var _binding: FragmentAccountAndSettingsBinding? = null
	
	// This property is only valid between onCreateView and
	// onDestroyView.
	private val binding get() = _binding!!
	
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
	): View {
		val accountViewModel = ViewModelProvider(this)[AccountViewModel::class.java]
		
		_binding = FragmentAccountAndSettingsBinding.inflate(inflater, container, false)

//		val textView: TextView = binding.textNotifications
//		accountViewModel.text.observe(viewLifecycleOwner) {
//			textView.text = it
//		}
		return binding.root
	}
	
	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
	
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		requireActivity().findViewById<TextView>(R.id.reload_password_icons).setOnClickListener {
			requireActivity().pullPasswordIcons{}
		}
		
		requireActivity().findViewById<TextView>(R.id.clear_password_icon_cache).setOnClickListener {
			requireActivity().clearFaviconCache()
		}
		
		requireActivity().findViewById<TextView>(R.id.clear_password_cache).setOnClickListener {
			requireActivity().clearPasswordCache()
		}
		
		requireActivity().findViewById<TextView>(R.id.logout).setOnClickListener {
			val username = App.sharedPreferences().getString(SPKeys.username, SPKeys.not_found)
			val token = App.sharedPreferences().getString(SPKeys.token, SPKeys.not_found)
			
			try {
				val thread = Thread {
					val getPasswordListURL = URL(App.sharedPreferences().getString(SPKeys.server, SPKeys.not_found) + "/ocs/v2.php/core/apppassword")
					with(getPasswordListURL.openConnection() as HttpsURLConnection) {
						val userCredentials = "${App.sharedPreferences().getString(SPKeys.username, SPKeys.not_found)}:${App.sharedPreferences().getString(SPKeys.token, SPKeys.not_found)}"
						val basicAuth = "Basic " + String(Base64.getEncoder().encode(userCredentials.toByteArray()))
						setRequestProperty("Authorization", basicAuth)
						setRequestProperty("OCS-APIREQUEST", "true")
						requestMethod = "DELETE";
						println("\nSent $requestMethod request to URL : $getPasswordListURL; Response Code : $responseCode")
						if (responseCode in 200..299) {
							requireActivity().runOnUiThread {
								App.sharedPreferences().edit().clear().commit()
								passwords = arrayOf(Password())
								//requireActivity().deleteSharedPreferences(SPKeys.secure_storage)
								val intent = Intent(activity, EnterServerURLActivity::class.java)
								requireActivity().finish()
								startActivity(intent)
								Toast.makeText(activity, getString(R.string.successfully_logged_out), Toast.LENGTH_LONG).show()
							}
						} else if (responseCode == 401) {
							requireActivity().runOnUiThread {
								App.sharedPreferences().edit().clear().commit()
								passwords = arrayOf(Password())
								//requireActivity().deleteSharedPreferences(SPKeys.secure_storage)
								val intent = Intent(activity, EnterServerURLActivity::class.java)
								requireActivity().finish()
								startActivity(intent)
								Toast.makeText(activity, getString(R.string.your_token_is_no_longer_valid_please_login_again), Toast.LENGTH_LONG).show()
							}
						} else {
							requireActivity().runOnUiThread {
								Toast.makeText(activity, getString(R.string.something_went_wrong_try_again), Toast.LENGTH_LONG).show()
							}
						}
						//						println(inputAsString)
						//						activity?.runOnUiThread{
						//							requireActivity().findViewById<TextView>(R.id.textView2).text = inputAsString
						//						}
					}
				}
				thread.start()
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}


	}
}
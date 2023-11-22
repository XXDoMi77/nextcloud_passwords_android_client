package com.dominikdomotor.nextcloudpasswords.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.dominikdomotor.nextcloudpasswords.*
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.Password
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection


class SettingsFragment : Fragment() {
	
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View? {
		
		return inflater.inflate(R.layout.fragment_settings, container, false)
	}
	
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		
		val efm = EncryptedFileManager(requireContext().applicationContext)
		
		requireActivity().findViewById<ConstraintLayout>(R.id.openAutofillOptionsSetting).setOnClickListener {
			try {
				
				PasswordManager.generateRandomPassword(requireContext().applicationContext)
				
				val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
				intent.data = Uri.parse("package:" + (context?.packageName))
				startActivity(intent)
			} catch (e: Exception) {
				e.printStackTrace()
			}

//			PasswordManager.pullPasswordIcons(requireActivity()) {}
		}

//		val numberPicker6 = requireActivity().findViewById<NumberPicker>(R.id.settingsOptionNumberPicker6)
////		val values = (256 downTo 6).map { it.toString() }.toTypedArray()
////		println("Values: ${values.joinToString(", ")}")
//		numberPicker6.minValue = 6
//		numberPicker6.maxValue = 256
//		numberPicker6.value = 20
//		numberPicker6.wrapSelectorWheel = false
////		println("values.size: " + values.size)
////		numberPicker6.displayedValues = values
////		numberPicker6.value = values.indexOfFirst { it == "20" }
////		println("index: " + values.indexOfFirst { it == "20" })
//		numberPicker6.setOn
//		numberPicker6.setFormatter { value ->
//			val reversedValue: Int = 256 - value
//			String.format(Locale.getDefault(), "%d", reversedValue)
//		}
		
		startNumberSelector(
			number = requireActivity().findViewById<EditText>(R.id.passwordLengthSettingNumber),
			add = requireActivity().findViewById<ImageButton>(R.id.passwordLengthSettingAdd),
			remove = requireActivity().findViewById<ImageButton>(R.id.passwordLengthSettingRemove),
			min = 6,
			max = 255
		)
		startNumberSelector(
			number = requireActivity().findViewById<EditText>(R.id.includeSymbolsSettingNumber),
			add = requireActivity().findViewById<ImageButton>(R.id.includeSymbolsSettingAdd),
			remove = requireActivity().findViewById<ImageButton>(R.id.includeSymbolsSettingRemove),
			min = 0,
			max = 255
		)
		
		requireActivity().findViewById<EditText>(R.id.passwordLengthSettingNumber)
			.setText(efm.read(SPKeys.settings_password_length))
		requireActivity().findViewById<EditText>(R.id.includeSymbolsSettingNumber)
			.setText(efm.read(SPKeys.settings_include_symbols_number))
		
		requireActivity().findViewById<Switch>(R.id.excludeSimilarCharactersSettingSwitch).isChecked =
			efm.read(SPKeys.settings_exclude_similar_numbers).toBoolean()
		
		requireActivity().findViewById<Switch>(R.id.excludeSimilarCharactersSettingSwitch).setOnClickListener {
			efm.store(SPKeys.settings_exclude_similar_numbers, requireActivity().findViewById<Switch>(R.id.excludeSimilarCharactersSettingSwitch).isChecked.toString())
		}
		
		
		// Assuming you have an EditText with the id 'editText' in your layout
		requireActivity().findViewById<EditText>(R.id.passwordLengthSettingNumber).addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
				// This method is called to notify you that characters within `s` are about to be replaced with new text with a length of `after`.
			}
			
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
				// This method is called to notify you that somewhere within `s`, the text has been replaced by new text with a length of `count`.
			}
			
			override fun afterTextChanged(s: Editable?) {
				// This method is called to notify you that somewhere within `s`, the text has been changed.
				// Do something with the entered text
				efm.store(SPKeys.settings_password_length, s.toString())
			}
		})
		
		// Assuming you have an EditText with the id 'editText' in your layout
		requireActivity().findViewById<EditText>(R.id.includeSymbolsSettingNumber).addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
				// This method is called to notify you that characters within `s` are about to be replaced with new text with a length of `after`.
			}
			
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
				// This method is called to notify you that somewhere within `s`, the text has been replaced by new text with a length of `count`.
			}
			
			override fun afterTextChanged(s: Editable?) {
				// This method is called to notify you that somewhere within `s`, the text has been changed.
				// Do something with the entered text
				efm.store(SPKeys.settings_include_symbols_number, s.toString())
			}
		})
		
		requireActivity().findViewById<ConstraintLayout>(R.id.clearPasswordCacheSetting).setOnClickListener {
			PasswordManager.clearStoredPasswordsFromSharedPreferences(requireContext().applicationContext)
		}
		
		requireActivity().findViewById<ConstraintLayout>(R.id.logoutSetting).setOnClickListener {
			try {
				val thread = Thread {
					val getPasswordListURL = URL(
						efm.read(SPKeys.server) + "/ocs/v2.php/core/apppassword"
					)
					with(getPasswordListURL.openConnection() as HttpsURLConnection) {
						val userCredentials = "${
							efm.read(SPKeys.username)
						}:${efm.read(SPKeys.token)}"
						val basicAuth = "Basic " + String(Base64.getEncoder().encode(userCredentials.toByteArray()))
						setRequestProperty("Authorization", basicAuth)
						setRequestProperty("OCS-APIREQUEST", "true")
						requestMethod = "DELETE";
						println("\nSent $requestMethod request to URL : $getPasswordListURL; Response Code : $responseCode")
						if (responseCode in 200..299) {
							requireActivity().runOnUiThread {
								requireContext().getSharedPreferences(SPKeys.secure_storage, Context.MODE_PRIVATE).edit().clear().commit()
								PasswordManager.emptyPasswords()
								//requireActivity().deleteSharedPreferences(SPKeys.secure_storage)
								val intent = Intent(requireActivity(), EnterServerURLActivity::class.java)
								requireActivity().finish()
								startActivity(intent)
								Toast.makeText(requireActivity(), getString(R.string.successfully_logged_out), Toast.LENGTH_LONG).show()
							}
						} else if (responseCode == 401) {
							requireActivity().runOnUiThread {
								requireContext().getSharedPreferences(SPKeys.secure_storage, Context.MODE_PRIVATE).edit().clear().commit()
								PasswordManager.clearPasswordsCache()
								//requireActivity().deleteSharedPreferences(SPKeys.secure_storage)
								val intent = Intent(requireActivity(), EnterServerURLActivity::class.java)
								requireActivity().finish()
								startActivity(intent)
								Toast.makeText(
									requireActivity(), getString(R.string.your_token_is_no_longer_valid_please_login_again), Toast.LENGTH_LONG
								).show()
							}
						} else {
							requireActivity().runOnUiThread {
								Toast.makeText(requireActivity(), getString(R.string.something_went_wrong_try_again), Toast.LENGTH_LONG).show()
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
	
	private fun startNumberSelector(number: EditText, remove: ImageButton, add: ImageButton, min: Int, max: Int) {
		view?.isHapticFeedbackEnabled = true;
		
		remove.setOnClickListener {
			val currentNumberString = number.text.toString()
			val currentNumber = if (currentNumberString.isNotEmpty()) currentNumberString.toInt() else 0
			val incrementedNumber: Int = if (currentNumber > min) {
				view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
				currentNumber - 1
			} else {
				currentNumber
			}
			number.setText(incrementedNumber.toString())
			
		}
		add.setOnClickListener {
			val currentNumberString = number.text.toString()
			val currentNumber = if (currentNumberString.isNotEmpty()) currentNumberString.toInt() else 0
			val incrementedNumber: Int = if (currentNumber < max) {
				view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
				currentNumber + 1
			} else {
				currentNumber
			}
			number.setText(incrementedNumber.toString())
		}
	}
}
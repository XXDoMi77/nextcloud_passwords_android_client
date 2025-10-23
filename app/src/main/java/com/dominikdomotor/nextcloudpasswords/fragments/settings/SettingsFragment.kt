package com.dominikdomotor.nextcloudpasswords.fragments.settings

import android.content.DialogInterface
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.activities.EnterServerURLActivity
import com.dominikdomotor.nextcloudpasswords.managers.NetworkManager
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    @Inject lateinit var networkManager: NetworkManager

    @Inject lateinit var storageManager: StorageManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().findViewById<ConstraintLayout>(R.id.openAutofillOptionsSetting).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                intent.data = Uri.parse("package:" + (context?.packageName))
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        startNumberSelector(
            number = requireActivity().findViewById<EditText>(R.id.passwordLengthSettingNumber),
            add = requireActivity().findViewById<ImageButton>(R.id.passwordLengthSettingAdd),
            remove = requireActivity().findViewById<ImageButton>(R.id.passwordLengthSettingRemove),
            min = 6,
            max = 255,
        )
        startNumberSelector(
            number = requireActivity().findViewById<EditText>(R.id.includeSymbolsSettingNumber),
            add = requireActivity().findViewById<ImageButton>(R.id.includeSymbolsSettingAdd),
            remove = requireActivity().findViewById<ImageButton>(R.id.includeSymbolsSettingRemove),
            min = 0,
            max = 255,
        )

        requireActivity()
            .findViewById<EditText>(R.id.passwordLengthSettingNumber)
            .setText(storageManager.getSettings().passwordLength.toString())
        requireActivity()
            .findViewById<EditText>(R.id.includeSymbolsSettingNumber)
            .setText(storageManager.getSettings().includedSymbolsQuantity.toString())

        val excludeSimilarCharactersSettingSwitch =
            requireActivity().findViewById<SwitchCompat>(R.id.excludeSimilarCharactersSettingSwitch)

        excludeSimilarCharactersSettingSwitch.isChecked = storageManager.getSettings().excludeSimilarCharacters

        requireActivity().findViewById<SwitchCompat>(R.id.excludeSimilarCharactersSettingSwitch).setOnClickListener {
            storageManager.updateSettings {
                it.excludeSimilarCharacters = excludeSimilarCharactersSettingSwitch.isChecked
            }
        }

        // Assuming you have an EditText with the id 'editText' in your layout
        requireActivity()
            .findViewById<EditText>(R.id.passwordLengthSettingNumber)
            .addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) {
                        // This method is called to notify you that characters within `s` are about to be replaced with
                        // new text with a length of `after`.
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) {
                        // This method is called to notify you that somewhere within `s`, the text has been replaced by
                        // new text with a length of `count`.
                    }

                    override fun afterTextChanged(s: Editable?) {
                        // This method is called to notify you that somewhere within `s`, the text has been changed.
                        // Do something with the entered text
                        storageManager.updateSettings { it.passwordLength = s.toString().toInt() }
                    }
                },
            )

        // Assuming you have an EditText with the id 'editText' in your layout
        requireActivity()
            .findViewById<EditText>(R.id.includeSymbolsSettingNumber)
            .addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) {
                        // This method is called to notify you that characters within `s` are about to be replaced with
                        // new text with a length of `after`.
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) {
                        // This method is called to notify you that somewhere within `s`, the text has been replaced by
                        // new text with a length of `count`.
                    }

                    override fun afterTextChanged(s: Editable?) {
                        // This method is called to notify you that somewhere within `s`, the text has been changed.
                        // Do something with the entered text
                        storageManager.updateSettings { it.includedSymbolsQuantity = s.toString().toInt() }
                    }
                },
            )

        requireActivity().findViewById<ConstraintLayout>(R.id.clearOfflinePasswordCacheSetting).setOnClickListener {
            networkManager.stopFaviconPull()
            // 			Thread.sleep(1000)
            storageManager.clearOfflinePasswordCache()
            storageManager.clearFaviconCache()
        }

        requireActivity().findViewById<ConstraintLayout>(R.id.logoutSetting).setOnClickListener {
            requireActivity().runOnUiThread {
                networkManager.stopFaviconPull()
                val dialogClickListener: DialogInterface.OnClickListener =
                    DialogInterface.OnClickListener { _, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                networkManager.logout {
                                    val intent =
                                        Intent(
                                            requireActivity(),
                                            EnterServerURLActivity::class.java,
                                        )
                                    requireActivity().finish()
                                    startActivity(intent)
                                }
                            }

                            DialogInterface.BUTTON_NEGATIVE -> {}
                        }
                    }
                val builder: AlertDialog.Builder = AlertDialog.Builder(requireActivity(), R.style.AlertDialogStyle)
                builder
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(R.string.yes, dialogClickListener)
                    .setNegativeButton(R.string.cancel, dialogClickListener)
                    .show()
            }
        }
    }

    private fun startNumberSelector(
        number: EditText,
        remove: ImageButton,
        add: ImageButton,
        min: Int,
        max: Int,
    ) {
        view?.isHapticFeedbackEnabled = true

        remove.setOnClickListener {
            val currentNumberString = number.text.toString()
            val currentNumber = if (currentNumberString.isNotEmpty()) currentNumberString.toInt() else 0
            val incrementedNumber: Int =
                if (currentNumber > min) {
                    view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentNumber - 1
                } else {
                    currentNumber
                }
            number.setText(incrementedNumber.toString())
        }
        add.setOnClickListener {
            val currentNumberString = number.text.toString()
            val currentNumber = if (currentNumberString.isNotEmpty()) currentNumberString.toInt() else 0
            val incrementedNumber: Int =
                if (currentNumber < max) {
                    view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentNumber + 1
                } else {
                    currentNumber
                }
            number.setText(incrementedNumber.toString())
        }
    }
}

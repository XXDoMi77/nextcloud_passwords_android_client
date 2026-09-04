package com.dominikdomotor.nextcloudpasswords.fragments.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.activities.EnterServerURLActivity
import com.dominikdomotor.nextcloudpasswords.data.AccentColor
import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
import com.dominikdomotor.nextcloudpasswords.dataclasses.ThemeSeedSource
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog
import com.dominikdomotor.nextcloudpasswords.ui.theme.ThemeApplier
import com.dominikdomotor.nextcloudpasswords.ui.theme.ThemeCache
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private val viewModel: SettingsViewModel by viewModels()

    /** Guards the switch/field listeners while state from the view model is being applied. */
    private var applyingState = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUpAutofillSection(view)
        setUpPasswordGenerationSection(view)
        setUpSecuritySection(view)

        view.findViewById<ConstraintLayout>(R.id.clearOfflinePasswordCacheSetting).setOnClickListener {
            AppDialog(requireActivity())
                .title(R.string.clear_offline_storage)
                .message(R.string.clear_offline_storage_confirmation)
                .button(R.string.cancel)
                .button(R.string.clear, destructive = true) { viewModel.clearCaches() }
                .showCompact()
        }
        view.findViewById<ConstraintLayout>(R.id.logoutSetting).setOnClickListener { confirmLogout() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { render(view, it) }
            }
        }
    }

    /**
     * Pushes view-model state into the widgets.
     *
     * The screen used to read settings once in `onViewCreated`, so a change made elsewhere (an E2E unlock storing the
     * passphrase, say) was not reflected until the fragment was recreated.
     */
    private fun render(view: View, settings: Settings) {
        applyingState = true

        view.findViewById<SwitchCompat>(R.id.inlineAutofillSuggestionsSettingSwitch).isChecked =
            settings.inlineAutofillSuggestions
        view.findViewById<SwitchCompat>(R.id.manualAutofillFallbackSettingSwitch).isChecked =
            settings.autofillManualFallback
        view.findViewById<SwitchCompat>(R.id.expandBottomSheetSettingSwitch).isChecked = settings.expandBottomSheet
        view.findViewById<SwitchCompat>(R.id.animateSearchResultsSettingSwitch).isChecked =
            settings.animateSearchResults
        view.findViewById<SwitchCompat>(R.id.excludeSimilarCharactersSettingSwitch).isChecked =
            settings.excludeSimilarCharacters
        view.findViewById<SwitchCompat>(R.id.allowScreenshotsSettingSwitch).isChecked = settings.allowScreenshots
        view.findViewById<SwitchCompat>(R.id.tintedTextSettingSwitch).isChecked = settings.tintedText

        view.setTextIfChanged(R.id.passwordLengthSettingNumber, settings.passwordLength.toString())
        view.setTextIfChanged(R.id.includeSymbolsSettingNumber, settings.includedSymbolsQuantity.toString())
        view.setTextIfChanged(R.id.includedSymbolCharacters, settings.includedSymbols)
        view.setTextIfChanged(R.id.similarCharacters, settings.similarCharacters)

        // The list only means anything while the exclusion is switched on.
        view.findViewById<TextInputLayout>(R.id.similarCharactersLayout).apply {
            isEnabled = settings.excludeSimilarCharacters
            alpha = if (settings.excludeSimilarCharacters) 1f else DISABLED_ALPHA
        }

        // Nothing to forget when none is stored, so the row states that instead of offering the action.
        val accent = AccentColor.of(settings)
        view.findViewById<View>(R.id.accentColourSwatch).background =
            GradientDrawable().apply {
                setColor(accent)
                cornerRadius = resources.getDimension(R.dimen.radius_item)
            }
        view
            .findViewById<TextView>(R.id.accentColourSettingDescription)
            .setText(
                if (AccentColor.isServerColour(settings)) R.string.accent_colour_from_server
                else R.string.accent_colour_custom
            )

        val (modeLabel, sourceLabel) = AppearanceDialog.summaryFor(settings.themeMode, settings.themeSeedSource)
        view.findViewById<TextView>(R.id.appearanceSettingDescription).text =
            getString(R.string.theme_setting_description, getString(modeLabel), getString(sourceLabel))

        val stored = settings.e2ePassphrase.isNotEmpty()
        view
            .findViewById<TextView>(R.id.storedE2ePassphraseDescription)
            .setText(if (stored) R.string.forget_stored_e2e_passphrase else R.string.no_e2e_passphrase_stored)
        view.findViewById<ConstraintLayout>(R.id.storedE2ePassphraseSetting).apply {
            isEnabled = stored
            alpha = if (stored) 1f else DISABLED_ALPHA
        }

        applyingState = false
    }

    private fun setUpAutofillSection(view: View) {
        view.findViewById<ConstraintLayout>(R.id.openAutofillOptionsSetting).setOnClickListener {
            val intent =
                Intent(AndroidSettings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                    .setData("package:${requireContext().packageName}".toUri())
            runCatching { startActivity(intent) }
        }
        view.bindSwitch(R.id.inlineAutofillSuggestionsSettingSwitch) { s, on -> s.inlineAutofillSuggestions = on }
        view.bindSwitch(R.id.manualAutofillFallbackSettingSwitch) { s, on -> s.autofillManualFallback = on }
        view.findViewById<ConstraintLayout>(R.id.autofillBlockedAppsSetting).setOnClickListener {
            showBlockedAppsDialog()
        }
        view.findViewById<ConstraintLayout>(R.id.autofillHintWordsSetting).setOnClickListener { showHintWordsDialog() }
    }

    private fun showHintWordsDialog() {
        val settings = viewModel.settings.value
        AutofillHintWordsDialog.show(
            requireActivity(),
            settings.autofillUsernameWords,
            settings.autofillPasswordWords,
        ) { username, password ->
            viewModel.updateHintWords(username, password)
        }
    }

    private fun setUpPasswordGenerationSection(view: View) {
        bindNumberSelector(
            number = view.findViewById(R.id.passwordLengthSettingNumber),
            remove = view.findViewById(R.id.passwordLengthSettingRemove),
            add = view.findViewById(R.id.passwordLengthSettingAdd),
            min = MIN_PASSWORD_LENGTH,
            max = MAX_NUMERIC_SETTING,
        ) { value ->
            viewModel.update { it.passwordLength = value }
        }

        bindNumberSelector(
            number = view.findViewById(R.id.includeSymbolsSettingNumber),
            remove = view.findViewById(R.id.includeSymbolsSettingRemove),
            add = view.findViewById(R.id.includeSymbolsSettingAdd),
            min = 0,
            max = MAX_NUMERIC_SETTING,
        ) { value ->
            viewModel.update { it.includedSymbolsQuantity = value }
        }

        bindCharacterList(
            layout = view.findViewById(R.id.includedSymbolCharactersLayout),
            field = view.findViewById(R.id.includedSymbolCharacters),
            default = Settings.DEFAULT_SYMBOLS,
        ) { value ->
            viewModel.update { it.includedSymbols = value }
        }

        bindCharacterList(
            layout = view.findViewById(R.id.similarCharactersLayout),
            field = view.findViewById(R.id.similarCharacters),
            default = Settings.DEFAULT_SIMILAR_CHARACTERS,
        ) { value ->
            viewModel.update { it.similarCharacters = value }
        }

        view.bindSwitch(R.id.excludeSimilarCharactersSettingSwitch) { s, on -> s.excludeSimilarCharacters = on }
        view.findViewById<ConstraintLayout>(R.id.accentColourSetting).setOnClickListener { showAccentColourDialog() }
        view.findViewById<ConstraintLayout>(R.id.appearanceSetting).setOnClickListener { showAppearanceDialog() }

        // Not bindSwitch: this one regenerates the palette and restarts the activity, which a plain settings write
        // does not do.
        view.bindSwitchRow(R.id.tintedTextSettingSwitch) { enabled -> applyAppearance { it.tintedText = enabled } }
        view.bindSwitch(R.id.expandBottomSheetSettingSwitch) { s, on -> s.expandBottomSheet = on }
        view.bindSwitch(R.id.animateSearchResultsSettingSwitch) { s, on -> s.animateSearchResults = on }
    }

    private fun setUpSecuritySection(view: View) {
        view.bindSwitchRow(R.id.allowScreenshotsSettingSwitch) { enabled ->
            viewModel.update { it.allowScreenshots = enabled }
            requireActivity().window.apply {
                if (enabled) clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        view.findViewById<ConstraintLayout>(R.id.storedE2ePassphraseSetting).setOnClickListener {
            AppDialog(requireActivity())
                .message(R.string.forget_e2e_passphrase_confirmation)
                .button(R.string.cancel)
                .button(R.string.forget, destructive = true) { viewModel.forgetStoredPassphrase() }
                .showCompact()
        }
    }

    /**
     * Saves an appearance change and restarts the activity so it takes effect.
     *
     * The tabs are shown and hidden rather than recreated, so nothing short of an activity restart re-inflates them
     * with the new palette. Whether one is needed is decided by comparing the theme cache before and after: the cache
     * holds exactly the values that change how the app is painted, so "did the cache change" is the same question as
     * "does anything need repainting", and no caller has to work that out for itself.
     *
     * Setting the night mode already triggers a restart, which is why the explicit one is in the `else` - doing both
     * restarts the activity twice and flashes the old colours in between. This lives in a click handler and not in the
     * settings observer on purpose: `render()` runs on every emission, and recreating from there would loop forever.
     */
    private fun applyAppearance(update: (Settings) -> Unit) {
        val before = ThemeCache.read(requireContext())
        viewModel.update(update)
        ThemeCache.write(requireContext(), viewModel.settings.value)
        if (ThemeCache.read(requireContext()) == before) return

        val nightMode = ThemeApplier.nightModeFor(viewModel.settings.value.themeMode)
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) AppCompatDelegate.setDefaultNightMode(nightMode)
        else requireActivity().recreate()
    }

    private fun showAppearanceDialog() {
        val settings = viewModel.settings.value
        AppearanceDialog.show(requireActivity(), settings.themeMode, settings.themeSeedSource) { mode, source ->
            applyAppearance {
                it.themeMode = mode
                it.themeSeedSource = source
            }
        }
    }

    private fun showAccentColourDialog() {
        val settings = viewModel.settings.value
        AccentColorDialog.show(
            requireActivity(),
            current = AccentColor.of(settings),
            serverDefault = AccentColor.serverDefault(settings),
        ) { chosen ->
            // Null means the picker landed back on the server's colour, which is stored as "no override".
            // Choosing a colour is also the whole point, so it becomes the seed the palette is generated from -
            // otherwise the picker would set a swatch the app then ignored because the source still said SERVER.
            // Resetting hands it back, which is what makes the reset button mean "go back to the server's colour".
            applyAppearance {
                it.accentColorOverride = chosen?.let(AccentColor::format).orEmpty()
                it.themeSeedSource = if (chosen == null) ThemeSeedSource.SERVER else ThemeSeedSource.CUSTOM
            }
        }
    }

    private fun showBlockedAppsDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val blocked = viewModel.settings.value.autofillBlockedApps.toSet()
            val apps = AutofillBlockedAppsDialog.loadApps(requireContext(), blocked)
            if (!isAdded) return@launch
            AutofillBlockedAppsDialog.show(requireActivity(), apps, blocked) { selected ->
                viewModel.update { it.autofillBlockedApps = selected.sorted() }
            }
        }
    }

    private fun confirmLogout() {
        AppDialog(requireActivity())
            .message(R.string.are_you_sure)
            .button(R.string.cancel)
            .button(R.string.yes, destructive = true) {
                viewModel.logout {
                    startActivity(Intent(requireActivity(), EnterServerURLActivity::class.java))
                    requireActivity().finish()
                }
            }
            .showCompact()
    }

    /**
     * Wires the −/+ buttons and keeps the field in sync.
     *
     * `toIntOrNull` tolerates the field being momentarily empty while the user retypes a value.
     */
    /**
     * A plus/minus pair around a numeric field.
     *
     * Deliberately not locale-formatted: the same field is read back with `toIntOrNull`, and localised digits or a
     * thousands separator would make the value unparseable.
     */
    @SuppressLint("SetTextI18n")
    private fun bindNumberSelector(
        number: EditText,
        remove: ImageButton,
        add: ImageButton,
        min: Int,
        max: Int,
        onChanged: (Int) -> Unit,
    ) {
        fun step(delta: Int) {
            val current = number.text.toString().toIntOrNull() ?: min
            val next = (current + delta).coerceIn(min, max)
            if (next != current) {
                number.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                number.setText(next.toString())
            }
        }
        remove.setOnClickListener { step(-1) }
        add.setOnClickListener { step(1) }
        number.doAfterTextChanged { text ->
            if (applyingState) return@doAfterTextChanged
            text?.toString()?.toIntOrNull()?.takeIf { it in min..max }?.let(onChanged)
        }
    }

    /**
     * A free-text character list with a reset affordance.
     *
     * The reset writes through the same path as typing, so the field, the stored value and the generator never disagree
     * — the text change is what saves, and [render] then echoes it back.
     */
    private fun bindCharacterList(
        layout: TextInputLayout,
        field: EditText,
        default: String,
        onChanged: (String) -> Unit,
    ) {
        field.doAfterTextChanged { text -> if (!applyingState) onChanged(text?.toString().orEmpty()) }
        layout.setEndIconOnClickListener {
            field.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            field.setText(default)
        }
    }

    private fun View.bindSwitch(id: Int, apply: (Settings, Boolean) -> Unit) {
        bindSwitchRow(id) { checked -> viewModel.update { apply(it, checked) } }
    }

    /**
     * Wires a switch and the whole row it sits in to the same action.
     *
     * The row is what people actually aim at: the switch is a small target pinned to the far edge, and every other row
     * on this screen already opens on a tap anywhere along it, so hitting one of these and having nothing happen reads
     * as the screen being broken. Taken from the switch's own parent rather than a row id passed in, so a row cannot be
     * given the affordance in the layout and left without a handler behind it.
     *
     * Returns the switch for callers that need it for anything else.
     */
    private fun View.bindSwitchRow(id: Int, onToggled: (Boolean) -> Unit): SwitchCompat {
        val switch = findViewById<SwitchCompat>(id)
        switch.setOnClickListener { if (!applyingState) onToggled(switch.isChecked) }
        (switch.parent as? View)?.setOnClickListener {
            if (applyingState) return@setOnClickListener
            switch.isChecked = !switch.isChecked
            onToggled(switch.isChecked)
        }
        return switch
    }

    /** Avoids moving the cursor while the user is typing in the field being rendered. */
    private fun View.setTextIfChanged(id: Int, value: String) {
        val field = findViewById<EditText>(id)
        if (field.text.toString() != value) field.setText(value)
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_NUMERIC_SETTING = 255
        const val DISABLED_ALPHA = 0.4f
    }
}

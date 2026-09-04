package com.dominikdomotor.nextcloudpasswords.activities

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.databinding.ActivityOverviewBinding
import com.dominikdomotor.nextcloudpasswords.fragments.BackHandler
import com.dominikdomotor.nextcloudpasswords.fragments.folders.FoldersFragment
import com.dominikdomotor.nextcloudpasswords.fragments.passwords.PasswordsFragment
import com.dominikdomotor.nextcloudpasswords.fragments.settings.SettingsFragment
import com.dominikdomotor.nextcloudpasswords.managers.E2eSessionResult
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog
import com.dominikdomotor.nextcloudpasswords.ui.theme.applySystemBarAppearance
import com.dominikdomotor.nextcloudpasswords.ui.theme.themeColor
import com.google.android.material.R as MaterialR
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OverviewActivity : BaseActivity() {
    private lateinit var binding: ActivityOverviewBinding
    private val viewModel: OverviewViewModel by viewModels()

    private var e2eDialog: AppDialog? = null
    private var exitDialog: AppDialog? = null
    private var selectedTabId = R.id.navigation_passwords

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !viewModel.loaded.value }
        super.onCreate(savedInstanceState)

        binding = ActivityOverviewBinding.inflate(layoutInflater)
        supportActionBar?.hide()
        applyWindowInsets()
        applySystemBars()
        setContentView(binding.root)

        setUpTabs(savedInstanceState)
        setUpBackNavigation()
        observeViewModel()
    }

    /**
     * Single owner of the back gesture: the visible tab gets first refusal, then back returns to the password list, and
     * only from there does it leave the app.
     */
    private fun setUpBackNavigation() {
        onBackPressedDispatcher.addCallback(this) {
            val visible = supportFragmentManager.findFragmentByTag(tagFor(selectedTabId))
            if ((visible as? BackHandler)?.handleBack() == true) return@addCallback

            if (selectedTabId != R.id.navigation_passwords) {
                binding.navView.selectedItemId = R.id.navigation_passwords
                return@addCallback
            }

            confirmExit()
        }
    }

    /** Leaving a password manager by accident is annoying, so back from the list asks first. */
    private fun confirmExit() {
        if (exitDialog?.isShowing == true) return
        exitDialog =
            AppDialog(this)
                .message(R.string.exit_app_confirmation)
                .button(R.string.cancel)
                .button(R.string.yes, destructive = true) { finish() }
                .onDismiss { exitDialog = null }
                .showCompact()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Only redirect once the store has actually been read, or a cold start would
                    // bounce to the login screen before the saved session is known.
                    viewModel.loaded.collect { loaded -> if (loaded && viewModel.signedOut.value) goToLogin() }
                }
                launch {
                    viewModel.signedOut.collect { signedOut -> if (signedOut && viewModel.loaded.value) goToLogin() }
                }
                launch {
                    viewModel.passphraseRequired.collect { required ->
                        if (required && e2eDialog?.isShowing != true) showE2ePassphraseDialog()
                    }
                }
            }
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, EnterServerURLActivity::class.java))
        finish()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = 0)
            // The gesture bar is transparent, so the strip it occupies is painted by whatever sits behind it - and
            // that is the bottom navigation. Giving the inset to the root instead ended the navigation bar above the
            // strip and left the window background showing through below it: two tones with a seam between them.
            binding.navView.updatePadding(bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * The two bars sit on different tones here, so they are told apart.
     *
     * The status bar is over the window surface; the gesture bar is over the bottom navigation, which is a container
     * tone above it. A single decision for both - which is what reading the night mode amounted to - got one of them
     * wrong whenever a seed made those two tones fall on opposite sides of readable.
     */
    private fun applySystemBars() {
        applySystemBarAppearance(
            statusBarBackground = themeColor(MaterialR.attr.colorSurface),
            navigationBarBackground = themeColor(MaterialR.attr.colorSurfaceContainer),
        )
    }

    /** Keeps all three tabs alive and swaps visibility, so each keeps its scroll and search state. */
    private fun setUpTabs(savedInstanceState: Bundle?) {
        val fragments =
            mapOf(
                R.id.navigation_folders to (supportFragmentManager.findFragmentByTag(FOLDERS_TAG) ?: FoldersFragment()),
                R.id.navigation_passwords to
                    (supportFragmentManager.findFragmentByTag(PASSWORDS_TAG) ?: PasswordsFragment()),
                R.id.navigation_settings to
                    (supportFragmentManager.findFragmentByTag(SETTINGS_TAG) ?: SettingsFragment()),
            )
        selectedTabId = savedInstanceState?.getInt(SELECTED_TAB_KEY) ?: R.id.navigation_passwords

        supportFragmentManager
            .beginTransaction()
            .apply {
                fragments.forEach { (itemId, fragment) ->
                    if (!fragment.isAdded) add(R.id.nav_host_fragment_activity_overview, fragment, tagFor(itemId))
                    applyVisibility(fragment, itemId == selectedTabId)
                }
            }
            .commitNow()

        binding.navView.menu.findItem(selectedTabId).isChecked = true
        binding.navView.setOnItemSelectedListener { item ->
            if (item.itemId != selectedTabId) {
                // Tabs slide in the direction of travel, so moving right feels like moving right.
                val forwards = TAB_ORDER.indexOf(item.itemId) > TAB_ORDER.indexOf(selectedTabId)
                supportFragmentManager
                    .beginTransaction()
                    .setCustomAnimations(
                        if (forwards) R.anim.slide_in_from_end else R.anim.slide_in_from_start,
                        if (forwards) R.anim.slide_out_to_start else R.anim.slide_out_to_end,
                    )
                    .apply { fragments.forEach { (id, f) -> applyVisibility(f, id == item.itemId) } }
                    .commit()
                selectedTabId = item.itemId
            }
            // Also on a re-tap of the current tab: someone who cleared the offline copy and comes back to an empty
            // list will press the tab again before they think to pull.
            if (item.itemId == R.id.navigation_passwords) viewModel.syncIfCacheEmpty()
            true
        }
    }

    private fun androidx.fragment.app.FragmentTransaction.applyVisibility(fragment: Fragment, visible: Boolean) {
        if (visible) {
            show(fragment)
            setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
        } else {
            hide(fragment)
            setMaxLifecycle(fragment, Lifecycle.State.STARTED)
        }
    }

    private fun tagFor(itemId: Int) =
        when (itemId) {
            R.id.navigation_folders -> FOLDERS_TAG
            R.id.navigation_settings -> SETTINGS_TAG
            else -> PASSWORDS_TAG
        }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SELECTED_TAB_KEY, selectedTabId)
        super.onSaveInstanceState(outState)
    }

    private fun showE2ePassphraseDialog() {
        val padding = (DIALOG_PADDING_DP * resources.displayMetrics.density).toInt()
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, 0)
            }
        val passphraseInput =
            EditText(this).apply {
                hint = getString(R.string.e2e_passphrase)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        val storePassphrase =
            CheckBox(this).apply {
                text = getString(R.string.store_e2e_passphrase)
                isChecked = storageManager.settings.value.e2ePassphrase.isNotEmpty()
            }
        content.addView(passphraseInput)
        content.addView(storePassphrase)

        val dialog = AppDialog(this)
        var unlocking = false
        dialog
            .title(R.string.e2e_unlock_required)
            .content(content)
            .button(R.string.cancel) { viewModel.dismissPassphrasePrompt() }
            // Stays open on a wrong passphrase so the error lands on the field the user is looking at.
            .button(R.string.unlock, dismissOnClick = false) {
                if (unlocking) return@button
                passphraseInput.error = null
                unlocking = true
                viewModel.unlock(passphraseInput.text.toString(), storePassphrase.isChecked) { result ->
                    unlocking = false
                    when (result) {
                        E2eSessionResult.READY -> dialog.dismiss()
                        E2eSessionResult.INVALID_PASSPHRASE ->
                            passphraseInput.error = getString(R.string.invalid_e2e_passphrase)
                        E2eSessionResult.UNSUPPORTED_CHALLENGE ->
                            passphraseInput.error = getString(R.string.unsupported_e2e_challenge)
                        else -> passphraseInput.error = getString(R.string.something_went_wrong_try_again)
                    }
                }
            }
            .onDismiss { e2eDialog = null }
            .showCompact()
        e2eDialog = dialog
    }

    private companion object {
        /** Left to right as the bottom bar shows them, which is what decides the slide direction. */
        val TAB_ORDER = listOf(R.id.navigation_folders, R.id.navigation_passwords, R.id.navigation_settings)

        const val SELECTED_TAB_KEY = "selected_tab"
        const val FOLDERS_TAG = "overview_folders"
        const val PASSWORDS_TAG = "overview_passwords"
        const val SETTINGS_TAG = "overview_settings"
        const val DIALOG_PADDING_DP = 20
    }
}

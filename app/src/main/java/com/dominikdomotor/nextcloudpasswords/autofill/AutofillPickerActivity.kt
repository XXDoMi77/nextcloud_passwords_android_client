package com.dominikdomotor.nextcloudpasswords.autofill

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.activities.BaseActivity
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.managers.FaviconStore
import com.dominikdomotor.nextcloudpasswords.ui.PasswordStatus
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject

@AndroidEntryPoint
class AutofillPickerActivity : BaseActivity() {
    @Inject lateinit var faviconStore: FaviconStore

    private lateinit var usernameIds: List<AutofillId>
    private lateinit var passwordIds: List<AutofillId>
    private lateinit var passwords: List<Password>
    private var focusedId: AutofillId? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Own process, so state written by the main process has to be re-read explicitly.
        storageManager.reloadFromStorage()
        setContentView(R.layout.activity_autofill_picker)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.autofill_picker_root)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        usernameIds = intent.autofillIds(EXTRA_USERNAME_IDS)
        passwordIds = intent.autofillIds(EXTRA_PASSWORD_IDS)
        focusedId = intent.autofillId(EXTRA_FOCUSED_ID)
        if (usernameIds.isEmpty() && passwordIds.isEmpty() && focusedId == null) {
            cancel()
            return
        }

        passwords = storageManager.passwords.value.sortedBy { it.label.lowercase() }
        val search = findViewById<EditText>(R.id.autofill_picker_search)
        val list = findViewById<ListView>(R.id.autofill_picker_list)
        findViewById<MaterialToolbar>(R.id.autofill_picker_toolbar).setNavigationOnClickListener { cancel() }

        fun render(query: String) {
            val matches =
                passwords.filter {
                    query.isBlank() ||
                        it.label.contains(query, ignoreCase = true) ||
                        it.username.contains(query, ignoreCase = true) ||
                        it.url.contains(query, ignoreCase = true)
                }
            list.adapter = PickerAdapter(matches)
        }

        search.doAfterTextChanged { render(it?.toString().orEmpty()) }
        val initialQuery = intent.getStringExtra(EXTRA_INITIAL_QUERY).orEmpty()
        search.setText(initialQuery)
        search.setSelection(initialQuery.length)
        if (initialQuery.isEmpty()) render("")
    }

    private fun returnCredential(password: Password) {
        val presentation = presentation(password.label)
        val dataset =
            AutofillDatasetFactory.credentialDataset(
                password.username,
                password.password,
                usernameIds,
                passwordIds,
                presentation,
                null,
            )
        returnDataset(dataset)
    }

    private fun returnSingleValue(password: Password, value: String) {
        val targetId = focusedId ?: return
        val dataset = AutofillDatasetFactory.singleFieldDataset(targetId, value, presentation(password.label))
        returnDataset(dataset)
    }

    /**
     * Hands the chosen dataset back to the requesting app.
     *
     * The ephemeral flag keeps this one-off dataset out of the platform's session cache. It arrived in API 31 and its
     * value is a plain string that gets inlined, so passing it on 29 and 30 is an extra the platform simply ignores.
     */
    @SuppressLint("InlinedApi")
    private fun returnDataset(dataset: android.service.autofill.Dataset) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                .putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT_EPHEMERAL_DATASET, true),
        )
        finish()
    }

    private fun presentation(label: String) =
        android.widget.RemoteViews(packageName, R.layout.autofill_dataset_presentation).apply {
            setTextViewText(R.id.autofill_presentation_title, label)
            setTextViewText(R.id.autofill_presentation_subtitle, getString(R.string.app_name))
        }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED, Intent().putExtras(Bundle.EMPTY))
        finish()
    }

    private fun Intent.autofillIds(key: String): List<AutofillId> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(key, AutofillId::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION") getParcelableArrayListExtra<AutofillId>(key).orEmpty()
        }

    private fun Intent.autofillId(key: String): AutofillId? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION") getParcelableExtra(key)
        }

    private inner class PickerAdapter(private val items: List<Password>) : BaseAdapter() {
        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Password = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view =
                convertView
                    ?: LayoutInflater.from(this@AutofillPickerActivity)
                        .inflate(R.layout.password_overview_item, parent, false)
            val password = getItem(position)
            view.findViewById<TextView>(R.id.textview_password_label).text = password.label
            view.findViewById<TextView>(R.id.textview_username).text = password.username
            val iconView = view.findViewById<ImageView>(R.id.imageview_password_listview_item)
            faviconStore.peek(password.id)?.let(iconView::setImageBitmap)
                ?: iconView.setImageResource(R.drawable.icon_foreground_24)
            view.findViewById<ImageButton>(R.id.imagebutton_copy_username).apply {
                isEnabled = focusedId != null
                isFocusable = false
                setOnClickListener { returnSingleValue(password, password.username) }
            }
            view.findViewById<ImageButton>(R.id.imagebutton_copy_password).apply {
                isEnabled = focusedId != null
                isFocusable = false
                setColorFilter(
                    androidx.core.content.ContextCompat.getColor(
                        this@AutofillPickerActivity,
                        PasswordStatus.from(password.status).colorResId,
                    )
                )
                setOnClickListener { returnSingleValue(password, password.password) }
            }
            view.findViewById<View>(R.id.constraint_layout_cardview_password).setOnClickListener {
                if (usernameIds.isEmpty() && passwordIds.isEmpty()) {
                    returnSingleValue(password, password.username)
                } else {
                    returnCredential(password)
                }
            }
            return view
        }
    }

    companion object {
        const val EXTRA_USERNAME_IDS = "autofill_username_ids"
        const val EXTRA_PASSWORD_IDS = "autofill_password_ids"
        const val EXTRA_FOCUSED_ID = "autofill_focused_id"
        const val EXTRA_INITIAL_QUERY = "autofill_initial_query"
    }
}

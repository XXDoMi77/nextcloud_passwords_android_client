package com.dominikdomotor.nextcloudpasswords.fragments.passwords

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Passwords
import com.dominikdomotor.nextcloudpasswords.managers.NetworkManager
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

class PasswordsRecyclerViewAdapter(
    private val activity: Activity,
    private val networkManager: NetworkManager,
    private val storageManager: StorageManager,
) : RecyclerView.Adapter<PasswordsRecyclerViewAdapter.ViewHolder>() {
    // 	, private val passwordList: Array<PasswordsListDataClass>
    // 	private var list1 = arrayOf("Name 1","Name 2","Name 3")
    // 	private var list2 = arrayOf("Username 1","Username 2","Username 3")

    private var loadedPasswordList: List<Password> = storageManager.getPasswords()
    private var filterEnabled = false
    private var searchQuery = ""

    fun reloadPasswords() {
        if (filterEnabled) {
            filterPasswordList(searchQuery)
        } else {
            showAllPasswords()
        }
    }

    fun showAllPasswords() {
        filterEnabled = false
        searchQuery = ""
        if (storageManager.getPasswords().isNotEmpty()) {
            loadedPasswordList = storageManager.getPasswords()
        }
        activity.runOnUiThread { this.notifyDataSetChanged() }
    }

    fun filterPasswordList(filter: String) {
        filterEnabled = true
        searchQuery = filter
        loadedPasswordList =
            storageManager
                .getPasswords()
                .toList()
                .filter {
                    it.label.contains(filter, true) ||
                        it.username.contains(filter, true) ||
                        it.url.contains(
                            filter,
                            true,
                        )
                }
                .sortedBy { it.label.startsWith(filter, true) }
                .reversed()

        val passwords = Passwords()
        passwords.addAll(
            loadedPasswordList.filter {
                it.label.contains(filter, true) ||
                    it.username.contains(
                        filter,
                        true,
                    ) ||
                    it.url.contains(filter, true)
            },
        )
        passwords.sortBy { it.label.startsWith(filter, true) }
        passwords.reverse()

        activity.runOnUiThread { this.notifyDataSetChanged() }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.password_overview_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = loadedPasswordList.size

    @Throws(URISyntaxException::class)
    fun getDomainName(url: String?): String {
        val uri = URI(url)
        val domain: String = uri.host
        return if (domain.startsWith("www.")) domain.substring(4) else domain
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        try {
            if (storageManager.getFavicons().contains(loadedPasswordList[position].id)) {
                holder.imageViewPasswordListviewItem.setImageBitmap(
                    storageManager.getFavicons()[loadedPasswordList[position].id])
            } else {
                holder.imageViewPasswordListviewItem.setImageResource(R.drawable.icon_foreground_36)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        holder.textviewPasswordLabel.text = loadedPasswordList[position].label
        holder.textviewUsername.text = loadedPasswordList[position].username
        holder.imageButtonCopyUsername.setOnClickListener {
            setClipboard(activity, loadedPasswordList[position].username, "username")

            val snack =
                Snackbar.make(
                    activity.findViewById(R.id.coordinatorLayoutForNotifications),
                    activity.getString(R.string.username).replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    } +
                        " " +
                        activity.getString(
                            R.string.copied_to_clipboard,
                        ),
                    Snackbar.LENGTH_SHORT,
                )
            snack.show()

            // 			val snack = Snackbar.make(it,loadedPasswordList[position].label + " " +
            // activity.getString(R.string.username) + " " +
            // activity.getString(R.string.copied_to_clipboard),Snackbar.LENGTH_LONG)
            // 			snack.anchorView = activity.findViewById(R.id.nav_view)
            // 			snack.mar
            // 			snack.show()
        }
        when (loadedPasswordList[position].status) {
            0 -> holder.imageButtonCopyPassword.setColorFilter(Color.parseColor("#FF00FF00"))
            1 -> holder.imageButtonCopyPassword.setColorFilter(Color.parseColor("#FFFFEA00"))
            2 -> holder.imageButtonCopyPassword.setColorFilter(Color.parseColor("#FFFF0000"))
            3 -> holder.imageButtonCopyPassword.setColorFilter(Color.parseColor("#FFFF0000"))
        }
        holder.imageButtonCopyPassword.setOnClickListener {
            setClipboard(activity, loadedPasswordList[position].password, "password")
            val snack =
                Snackbar.make(
                    activity.findViewById(R.id.coordinatorLayoutForNotifications),
                    activity.getString(R.string.password).replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    } +
                        " " +
                        activity.getString(
                            R.string.copied_to_clipboard,
                        ),
                    Snackbar.LENGTH_SHORT,
                )
            snack.show()
        }

        holder.constraintLayoutCardViewPassword.setOnClickListener {
            // on below line we are creating a new bottom sheet dialog.
            val bottomSheetDialog = BottomSheetDialog(activity)

            // 			dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

            // on below line we are inflating a layout file which we have created.
            val view = activity.layoutInflater.inflate(R.layout.password_edit_bottom_sheet_dialog, null)

            // on below line we are creating a variable for our button
            // which we are using to dismiss our dialog.
            // 			val btnClose = view.findViewById<Button>(R.id.idBtnDismiss)

            view.findViewById<ImageButton>(R.id.imageButtonClose).setOnClickListener { bottomSheetDialog.dismiss() }
            // below line is use to set cancelable to avoid
            // closing of dialog box when clicking on the screen.
            bottomSheetDialog.setCancelable(true)

            // on below line we are setting
            // content view to our view.
            bottomSheetDialog.setContentView(view)

            // on below line we are calling
            // a show method to display a dialog.
            bottomSheetDialog.show()

            bottomSheetDialog.setOnDismissListener { reloadPasswords() }

            GF.println("Editable: " + loadedPasswordList[position].editable.toString())

            val myLinearLayout = view.findViewById<LinearLayout>(R.id.myLinearLayout)

            val viewRoot = view.findViewById<ViewGroup>(android.R.id.content)

            val titleView = View.inflate(view.context, R.layout.password_edit_bottom_sheet_dialog_title_item, viewRoot)
            titleView.findViewById<TextView>(R.id.title).text = "Details"
            myLinearLayout.addView(titleView)

            val nameItem =
                View.inflate(view.context, R.layout.password_edit_bottom_sheet_dialog_property_item, viewRoot)
            nameItem.findViewById<TextView>(R.id.textview_top).text = activity.getString(R.string.name)
            nameItem.findViewById<EditText>(R.id.edittext_middle_left).setText(loadedPasswordList[position].label)
            nameItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = false
            val nameItemOriginalDrawable: Drawable =
                nameItem.findViewById<EditText>(R.id.edittext_middle_left).background
            nameItem
                .findViewById<EditText>(R.id.edittext_middle_left)
                .setBackgroundResource(android.R.color.transparent)
            nameItem.findViewById<ImageButton>(R.id.imagebutton_right).setOnClickListener {
                setClipboard(activity, loadedPasswordList[position].label, "name")
            }
            myLinearLayout.addView(nameItem)

            val usernameItem =
                View.inflate(view.context, R.layout.password_edit_bottom_sheet_dialog_property_item, viewRoot)
            usernameItem.findViewById<TextView>(R.id.textview_top).text =
                activity.getString(R.string.username).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            usernameItem
                .findViewById<EditText>(R.id.edittext_middle_left)
                .setText(loadedPasswordList[position].username)
            usernameItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = false
            val usernameItemOriginalDrawable: Drawable =
                usernameItem.findViewById<EditText>(R.id.edittext_middle_left).background
            usernameItem
                .findViewById<EditText>(R.id.edittext_middle_left)
                .setBackgroundResource(android.R.color.transparent)
            usernameItem.findViewById<ImageButton>(R.id.imagebutton_right).setOnClickListener {
                setClipboard(activity, loadedPasswordList[position].username, "name")
            }
            myLinearLayout.addView(usernameItem)

            val passwordItem =
                View.inflate(view.context, R.layout.password_edit_bottom_sheet_dialog_property_item, viewRoot)
            passwordItem.findViewById<TextView>(R.id.textview_top).text =
                activity.getString(R.string.password).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            passwordItem
                .findViewById<EditText>(R.id.edittext_middle_left)
                .setText(loadedPasswordList[position].password)
            passwordItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = false
            val passwordItemOriginalDrawable: Drawable =
                passwordItem.findViewById<EditText>(R.id.edittext_middle_left).background
            passwordItem
                .findViewById<EditText>(R.id.edittext_middle_left)
                .setBackgroundResource(android.R.color.transparent)
            passwordItem.findViewById<ImageButton>(R.id.imagebutton_right).setOnClickListener {
                setClipboard(activity, loadedPasswordList[position].password, "name")
            }
            myLinearLayout.addView(passwordItem)

            val urlItem = View.inflate(view.context, R.layout.password_edit_bottom_sheet_dialog_property_item, viewRoot)
            urlItem.findViewById<TextView>(R.id.textview_top).text =
                activity.getString(R.string.url).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            urlItem.findViewById<EditText>(R.id.edittext_middle_left).setText(loadedPasswordList[position].url)
            urlItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = false
            val urlItemOriginalDrawable: Drawable = urlItem.findViewById<EditText>(R.id.edittext_middle_left).background
            urlItem.findViewById<EditText>(R.id.edittext_middle_left).setBackgroundResource(android.R.color.transparent)
            urlItem.findViewById<ImageButton>(R.id.imagebutton_right).setOnClickListener {
                setClipboard(activity, loadedPasswordList[position].url, "name")
            }
            myLinearLayout.addView(urlItem)

            var beingEdited = false
            when (loadedPasswordList[position].editable) {
                true -> {
                    view
                        .findViewById<ImageButton>(R.id.imageButtonPencil)
                        .drawable
                        .setTint(Color.parseColor("#FF00FF00"))
                    view.findViewById<ImageButton>(R.id.imageButtonPencil).setOnClickListener {
                        beingEdited = !beingEdited
                        if (!beingEdited) {
                            nameItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = false
                            nameItem.findViewById<EditText>(R.id.edittext_middle_left).background = null
                            usernameItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = false
                            usernameItem.findViewById<EditText>(R.id.edittext_middle_left).background = null
                            passwordItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = false
                            passwordItem.findViewById<EditText>(R.id.edittext_middle_left).background = null
                            urlItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = false
                            urlItem.findViewById<EditText>(R.id.edittext_middle_left).background = null

                            val updatedPassword: Password = loadedPasswordList[position]

                            updatedPassword.label =
                                nameItem.findViewById<EditText>(R.id.edittext_middle_left).text.toString()
                            updatedPassword.username =
                                usernameItem.findViewById<EditText>(R.id.edittext_middle_left).text.toString()
                            updatedPassword.password =
                                passwordItem.findViewById<EditText>(R.id.edittext_middle_left).text.toString()
                            updatedPassword.url =
                                urlItem.findViewById<EditText>(R.id.edittext_middle_left).text.toString()

                            view
                                .findViewById<ImageButton>(R.id.imageButtonPencil)
                                .drawable
                                .setTint(Color.parseColor("#FFFFFF00"))
                            networkManager.updatePassword(updatedPassword) {
                                view
                                    .findViewById<ImageButton>(R.id.imageButtonPencil)
                                    .setImageResource(R.drawable.icon_edit_36)
                                when (loadedPasswordList[position].editable) {
                                    true ->
                                        view
                                            .findViewById<ImageButton>(R.id.imageButtonPencil)
                                            .drawable
                                            .setTint(
                                                Color.parseColor("#FF00FF00"),
                                            )

                                    false ->
                                        view
                                            .findViewById<ImageButton>(R.id.imageButtonPencil)
                                            .drawable
                                            .setTint(
                                                Color.parseColor("#FFFF0000"),
                                            )
                                }
                                activity.runOnUiThread { this.notifyDataSetChanged() }
                            }
                        } else {
                            nameItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = true
                            nameItem.findViewById<EditText>(R.id.edittext_middle_left).background =
                                nameItemOriginalDrawable
                            usernameItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = true
                            usernameItem.findViewById<EditText>(R.id.edittext_middle_left).background =
                                usernameItemOriginalDrawable
                            passwordItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = true
                            passwordItem.findViewById<EditText>(R.id.edittext_middle_left).background =
                                passwordItemOriginalDrawable
                            urlItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = true
                            urlItem.findViewById<EditText>(R.id.edittext_middle_left).background =
                                urlItemOriginalDrawable
                            view
                                .findViewById<ImageButton>(R.id.imageButtonPencil)
                                .setImageResource(R.drawable.icon_save_36)
                            view
                                .findViewById<ImageButton>(R.id.imageButtonPencil)
                                .drawable
                                .setTint(Color.parseColor("#FF00FF00"))
                        }
                    }
                }

                false -> {
                    view
                        .findViewById<ImageButton>(R.id.imageButtonPencil)
                        .drawable
                        .setTint(Color.parseColor("#FFFF0000"))
                    view.findViewById<ImageButton>(R.id.imageButtonPencil).setOnClickListener {
                        activity.runOnUiThread {
                            Toast.makeText(
                                    activity,
                                    activity.getString(R.string.this_password_is_not_editable),
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                        }
                    }
                }
            }

            view.findViewById<ImageButton>(R.id.imageButtonStar).drawable.setTint(Color.parseColor("#ECA700"))

            if (loadedPasswordList[position].favorite) {
                print("\n\n\nIt's my favourite!\n\n\n")
                view.findViewById<ImageButton>(R.id.imageButtonStar).setImageResource(R.drawable.icon_star_filled_36)
                view.findViewById<ImageButton>(R.id.imageButtonStar).drawable.setTint(Color.parseColor("#ECA700"))
            }

            // 			view.findViewById<ImageButton>(R.id.imageButtonStar).setOnClickListener {
            // 				if (loadedPasswordList[position].favorite) {
            // 					this.loadedPasswordList[position].favorite = false
            //
            //	view.findViewById<ImageButton>(R.id.imageButtonStar).setImageResource(R.drawable.icon_star_outline_36)
            // 					view.findViewById<ImageButton>(R.id.imageButtonStar).drawable.setTint(Color.parseColor("#ECA700"))
            // 				} else {
            // 					loadedPasswordList[position].favorite = true
            //
            //	view.findViewById<ImageButton>(R.id.imageButtonStar).setImageResource(R.drawable.icon_star_filled_36)
            // 					view.findViewById<ImageButton>(R.id.imageButtonStar).drawable.setTint(Color.parseColor("#ECA700"))
            // 				}
            // 				PWM.updatePassword(loadedPasswordList[position]) {
            // 					this.notifyDataSetChanged()
            // 				}
            // 			}

            when (loadedPasswordList[position].status) {
                0 ->
                    view
                        .findViewById<ImageButton>(R.id.imageButtonShield)
                        .drawable
                        .setTint(Color.parseColor("#FF00FF00"))
                1 ->
                    view
                        .findViewById<ImageButton>(R.id.imageButtonShield)
                        .drawable
                        .setTint(Color.parseColor("#FFFFEA00"))
                2 ->
                    view
                        .findViewById<ImageButton>(R.id.imageButtonShield)
                        .drawable
                        .setTint(Color.parseColor("#FFFF0000"))
                3 ->
                    view
                        .findViewById<ImageButton>(R.id.imageButtonShield)
                        .drawable
                        .setTint(Color.parseColor("#FFFF0000"))
            }

            view.findViewById<ImageButton>(R.id.imageButtonShield).setOnClickListener {
                when (loadedPasswordList[position].status) {
                    0 ->
                        activity.runOnUiThread {
                            Toast.makeText(
                                    activity, activity.getString(R.string.the_password_is_secure), Toast.LENGTH_LONG)
                                .show()
                        }

                    1 ->
                        activity.runOnUiThread {
                            Toast.makeText(
                                    activity,
                                    activity.getString(R.string.the_password_is_either_outdated_or_a_duplicate),
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                        }

                    2 ->
                        activity.runOnUiThread {
                            Toast.makeText(
                                    activity,
                                    activity.getString(R.string.the_password_is_insecure),
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                        }

                    3 ->
                        activity.runOnUiThread {
                            Toast.makeText(
                                    activity,
                                    activity.getString(R.string.the_security_status_of_the_password_was_not_checked),
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                        }
                }
            }

            view.findViewById<ImageButton>(R.id.imageButtonTrashcan).setOnClickListener {
                val dialogClickListener: DialogInterface.OnClickListener =
                    DialogInterface.OnClickListener { _, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                bottomSheetDialog.cancel()
                                networkManager.deletePassword(loadedPasswordList[position]) {
                                    activity.runOnUiThread { this.reloadPasswords() }
                                }
                            }

                            DialogInterface.BUTTON_NEGATIVE -> {}
                        }
                    }

                val builder: AlertDialog.Builder = AlertDialog.Builder(activity, R.style.AlertDialogStyle)
                builder
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(R.string.yes, dialogClickListener)
                    .setNegativeButton(R.string.cancel, dialogClickListener)
                    .show()
            }

            var shareTitleItemSpawned = true
            for (shareItem in storageManager.getShares()) {
                if (shareItem.password == loadedPasswordList[position].id) {
                    if (shareTitleItemSpawned) {
                        val shareTitleItem =
                            View.inflate(view.context, R.layout.password_edit_bottom_sheet_dialog_title_item, viewRoot)
                        shareTitleItem.findViewById<TextView>(R.id.title).text = activity.getString(R.string.shares)
                        myLinearLayout.addView(shareTitleItem)
                        shareTitleItemSpawned = false
                    }
                    val shareItemView =
                        View.inflate(view.context, R.layout.password_edit_bottom_sheet_dialog_share_item, viewRoot)
                    shareItemView.findViewById<TextView>(R.id.share_name).text = shareItem.receiver.name
                    myLinearLayout.addView(shareItemView)
                }
            }
        }
        // 		holder.textviewPasswordLabel.text = list1[position]
        // 		holder.textviewUsername.text = list2[position]
    }

    private fun setClipboard(
        context: Context,
        text: String,
        label: String,
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData = ClipData.newPlainText(label, text)
        // 		val persistableBundle = PersistableBundle()
        // 		persistableBundle.putBoolean("android.content.extra.IS_SENSITIVE", true)
        // 		persistableBundle.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        // 		clip.description.extras = persistableBundle

        // If your app targets a lower API level
        clip.apply {
            description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }

        clipboard.setPrimaryClip(clip)
    }

    inner class ViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        var textviewPasswordLabel: TextView
        var textviewUsername: TextView
        var imageButtonCopyUsername: ImageButton
        var imageButtonCopyPassword: ImageButton
        var imageViewPasswordListviewItem: ImageView
        var constraintLayoutCardViewPassword: ConstraintLayout

        init {
            textviewPasswordLabel = itemView.findViewById(R.id.textview_password_label)
            textviewUsername = itemView.findViewById(R.id.textview_username)
            imageButtonCopyUsername = itemView.findViewById(R.id.imagebutton_copy_username)
            imageButtonCopyPassword = itemView.findViewById(R.id.imagebutton_copy_password)
            imageViewPasswordListviewItem = itemView.findViewById(R.id.imageview_password_listview_item)
            constraintLayoutCardViewPassword = itemView.findViewById(R.id.constraint_layout_cardview_password)
        }
    }
}

package com.dominikdomotor.nextcloudpasswords.ui.passwords

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import android.provider.DocumentsContract.Root
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.dominikdomotor.nextcloudpasswords.App
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.passwords
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.Password
import com.dominikdomotor.nextcloudpasswords.updatePassword
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import java.net.URI
import java.net.URISyntaxException
import java.util.*


class PasswordsRecyclerViewAdapter(private val activity: Activity) : RecyclerView.Adapter<PasswordsRecyclerViewAdapter.ViewHolder>() {

//	, private val passwordList: Array<PasswordsListDataClass>
//	private var list1 = arrayOf("Name 1","Name 2","Name 3")
//	private var list2 = arrayOf("Username 1","Username 2","Username 3")
	
	
	private var loadedPasswordList = arrayOf(Password())
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
		if (passwords.isNotEmpty()) {
			loadedPasswordList = passwords
		}
		this.notifyDataSetChanged()
	}
	
	fun filterPasswordList(filter: String) {
		filterEnabled = true
		searchQuery = filter
		loadedPasswordList = ((passwords.filter { it.label.contains(filter, true) }).sortedBy { it.label.startsWith(filter, true) }).reversed().toTypedArray()
		activity.runOnUiThread {
			this.notifyDataSetChanged()
		}
	}
	
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PasswordsRecyclerViewAdapter.ViewHolder {
		val v = LayoutInflater.from(parent.context).inflate(R.layout.password_listview_item, parent, false)
		return ViewHolder(v)
	}
	
	override fun getItemCount(): Int {
		return if (passwords[0].id.isNotEmpty()) {
			loadedPasswordList.size
		} else {
			0
		}
	}
	
	@Throws(URISyntaxException::class) fun getDomainName(url: String?): String {
		val uri = URI(url)
		val domain: String = uri.host
		return if (domain.startsWith("www.")) domain.substring(4) else domain
	}
	
	override fun onBindViewHolder(holder: PasswordsRecyclerViewAdapter.ViewHolder, position: Int) {
		try {
			if (loadedPasswordList[position].favicon.isNotEmpty()) {
				val bitMap: Bitmap = BitmapFactory.decodeByteArray(loadedPasswordList[position].favicon, 0, loadedPasswordList[position].favicon.size)
				holder.imageViewPasswordListviewItem.setImageBitmap(bitMap)
			}else{
				holder.imageViewPasswordListviewItem.setImageResource(R.drawable.icon_foreground_36)
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
		
		holder.textviewPasswordLabel.text = loadedPasswordList[position].label
		holder.textviewUsername.text = loadedPasswordList[position].username
		holder.imageButtonCopyUsername.setOnClickListener {
			setClipboard(activity, loadedPasswordList[position].username, "username")
			
			val snack = Snackbar.make(activity.findViewById(R.id.myCoordinatorLayout), activity.getString(R.string.username).capitalize() + " " + activity.getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT)
			snack.show()

//			val snack = Snackbar.make(it,loadedPasswordList[position].label + " " + activity.getString(R.string.username) + " " + activity.getString(R.string.copied_to_clipboard),Snackbar.LENGTH_LONG)
//			snack.anchorView = activity.findViewById(R.id.nav_view)
//			snack.mar
//			snack.show()
		}
		when (loadedPasswordList[position].status) {
			0 -> holder.imageButtonCopyPassword.setColorFilter(Color.parseColor("#FF00FF00"))
			1 -> holder.imageButtonCopyPassword.setColorFilter(Color.parseColor("#FFFFEA00"))
			2 -> holder.imageButtonCopyPassword.setColorFilter(Color.parseColor("#FFFF0000"))
			3 -> holder.imageButtonCopyPassword.setColorFilter(Color.parseColor("#FFFF0000"))
		}
		holder.imageButtonCopyPassword.setOnClickListener {
			setClipboard(activity, loadedPasswordList[position].password, "password")
			val snack = Snackbar.make(activity.findViewById(R.id.myCoordinatorLayout), activity.getString(R.string.password).capitalize() + " " + activity.getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT)
			snack.show()
		}
		
		holder.constraintLayoutCardViewPassword.setOnClickListener {
			// on below line we are creating a new bottom sheet dialog.
			val dialog = BottomSheetDialog(activity)
			
			// on below line we are inflating a layout file which we have created.
			val view = activity.layoutInflater.inflate(R.layout.password_edit_bottom_sheet, null)
			
			// on below line we are creating a variable for our button
			// which we are using to dismiss our dialog.
//			val btnClose = view.findViewById<Button>(R.id.idBtnDismiss)
			
			view.findViewById<ImageButton>(R.id.imageButtonClose).setOnClickListener {
				dialog.dismiss()
			}
			// below line is use to set cancelable to avoid
			// closing of dialog box when clicking on the screen.
			dialog.setCancelable(true)
			
			// on below line we are setting
			// content view to our view.
			dialog.setContentView(view)
			
			// on below line we are calling
			// a show method to display a dialog.
			dialog.show()
			
			dialog.setOnDismissListener {
				reloadPasswords()
			}
			
			println("Editable: " + loadedPasswordList[position].editable.toString())
			
			val myLinearLayout = view.findViewById<LinearLayout>(R.id.myLinearLayout)
			
			val viewRoot = view.findViewById<ViewGroup>(android.R.id.content)
			
			val nameItem = View.inflate(view.context, R.layout.password_edit_textview_item, viewRoot)
			nameItem.findViewById<TextView>(R.id.textview_top).text = activity.getString(R.string.name)
			nameItem.findViewById<EditText>(R.id.edittext_middle_left).setText(loadedPasswordList[position].label)
			nameItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = false
			val nameItemOriginalDrawable: Drawable = nameItem.findViewById<EditText>(R.id.edittext_middle_left).background
			nameItem.findViewById<EditText>(R.id.edittext_middle_left).setBackgroundResource(android.R.color.transparent)
			nameItem.findViewById<ImageButton>(R.id.imagebutton_right).setOnClickListener {
				setClipboard(activity, loadedPasswordList[position].label, "name")
			}
			myLinearLayout.addView(nameItem)
			
			
			val usernameItem = View.inflate(view.context, R.layout.password_edit_textview_item, viewRoot)
			usernameItem.findViewById<TextView>(R.id.textview_top).text = activity.getString(R.string.username).capitalize()
			usernameItem.findViewById<EditText>(R.id.edittext_middle_left).setText(loadedPasswordList[position].username)
			usernameItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = false
			val usernameItemOriginalDrawable: Drawable = usernameItem.findViewById<EditText>(R.id.edittext_middle_left).background
			usernameItem.findViewById<EditText>(R.id.edittext_middle_left).setBackgroundResource(android.R.color.transparent)
			usernameItem.findViewById<ImageButton>(R.id.imagebutton_right).setOnClickListener {
				setClipboard(activity, loadedPasswordList[position].username, "name")
			}
			myLinearLayout.addView(usernameItem)
			
			
			val passwordItem = View.inflate(view.context, R.layout.password_edit_textview_item, viewRoot)
			passwordItem.findViewById<TextView>(R.id.textview_top).text = activity.getString(R.string.password).capitalize()
			passwordItem.findViewById<EditText>(R.id.edittext_middle_left).setText(loadedPasswordList[position].password)
			passwordItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = false
			val passwordItemOriginalDrawable: Drawable = passwordItem.findViewById<EditText>(R.id.edittext_middle_left).background
			passwordItem.findViewById<EditText>(R.id.edittext_middle_left).setBackgroundResource(android.R.color.transparent)
			passwordItem.findViewById<ImageButton>(R.id.imagebutton_right).setOnClickListener {
				setClipboard(activity, loadedPasswordList[position].password, "name")
			}
			myLinearLayout.addView(passwordItem)
			
			val urlItem = View.inflate(view.context, R.layout.password_edit_textview_item, viewRoot)
			urlItem.findViewById<TextView>(R.id.textview_top).text = activity.getString(R.string.url).capitalize()
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
					view.findViewById<ImageButton>(R.id.imageButtonPencil).drawable.setTint(Color.parseColor("#FF00FF00"))
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
							
							loadedPasswordList[position].label = nameItem.findViewById<EditText>(R.id.edittext_middle_left).text.toString()
							loadedPasswordList[position].username = usernameItem.findViewById<EditText>(R.id.edittext_middle_left).text.toString()
							loadedPasswordList[position].password = passwordItem.findViewById<EditText>(R.id.edittext_middle_left).text.toString()
							loadedPasswordList[position].url = urlItem.findViewById<EditText>(R.id.edittext_middle_left).text.toString()
							
							
							view.findViewById<ImageButton>(R.id.imageButtonPencil).drawable.setTint(Color.parseColor("#FFFFFF00"))
							activity.updatePassword(loadedPasswordList[position]) {
								view.findViewById<ImageButton>(R.id.imageButtonPencil).setImageResource(R.drawable.ic_baseline_edit_36)
								when (loadedPasswordList[position].editable) {
									true -> view.findViewById<ImageButton>(R.id.imageButtonPencil).drawable.setTint(Color.parseColor("#FF00FF00"))
									false -> view.findViewById<ImageButton>(R.id.imageButtonPencil).drawable.setTint(Color.parseColor("#FFFF0000"))
								}
							}
						} else {
							nameItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = true
							nameItem.findViewById<EditText>(R.id.edittext_middle_left).background = nameItemOriginalDrawable
							usernameItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = true
							usernameItem.findViewById<EditText>(R.id.edittext_middle_left).background = usernameItemOriginalDrawable
							passwordItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = true
							passwordItem.findViewById<EditText>(R.id.edittext_middle_left).background = passwordItemOriginalDrawable
							urlItem.findViewById<EditText>(R.id.edittext_middle_left).isEnabled = true
							urlItem.findViewById<EditText>(R.id.edittext_middle_left).background = urlItemOriginalDrawable
							view.findViewById<ImageButton>(R.id.imageButtonPencil).setImageResource(R.drawable.ic_baseline_save_36)
							view.findViewById<ImageButton>(R.id.imageButtonPencil).drawable.setTint(Color.parseColor("#FF00FF00"))
						}
					}
				}
				false -> {
					view.findViewById<ImageButton>(R.id.imageButtonPencil).drawable.setTint(Color.parseColor("#FFFF0000"))
					view.findViewById<ImageButton>(R.id.imageButtonPencil).setOnClickListener {
						App.makeToast(activity.getString(R.string.this_password_is_not_editable), Toast.LENGTH_LONG)
					}
				}
			}
			
			when (loadedPasswordList[position].status) {
				0 -> view.findViewById<ImageButton>(R.id.imageButtonShield).drawable.setTint(Color.parseColor("#FF00FF00"))
				1 -> view.findViewById<ImageButton>(R.id.imageButtonShield).drawable.setTint(Color.parseColor("#FFFFEA00"))
				2 -> view.findViewById<ImageButton>(R.id.imageButtonShield).drawable.setTint(Color.parseColor("#FFFF0000"))
				3 -> view.findViewById<ImageButton>(R.id.imageButtonShield).drawable.setTint(Color.parseColor("#FFFF0000"))
			}
			
			view.findViewById<ImageButton>(R.id.imageButtonShield).setOnClickListener {
				when (loadedPasswordList[position].status) {
					0 -> App.makeToast(activity.getString(R.string.the_password_is_secure), Toast.LENGTH_LONG)
					1 -> App.makeToast(activity.getString(R.string.the_password_is_either_outdated_or_a_duplicate), Toast.LENGTH_LONG)
					2 -> App.makeToast(activity.getString(R.string.the_password_is_insecure), Toast.LENGTH_LONG)
					3 -> App.makeToast(activity.getString(R.string.the_security_status_of_the_password_was_not_checked), Toast.LENGTH_LONG)
				}
			}
		}
//		holder.textviewPasswordLabel.text = list1[position]
//		holder.textviewUsername.text = list2[position]
	}
	
	private fun setClipboard(context: Context, text: String, label: String) {
		val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		val clip: ClipData = ClipData.newPlainText(label, text)
//		val persistableBundle = PersistableBundle()
//		persistableBundle.putBoolean("android.content.extra.IS_SENSITIVE", true)
//		persistableBundle.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
//		clip.description.extras = persistableBundle

// If your app targets a lower API level
		clip.apply {
			description.extras = PersistableBundle().apply {
				putBoolean("android.content.extra.IS_SENSITIVE", true)
			}
		}
		
		clipboard.setPrimaryClip(clip)
	}
	
	inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
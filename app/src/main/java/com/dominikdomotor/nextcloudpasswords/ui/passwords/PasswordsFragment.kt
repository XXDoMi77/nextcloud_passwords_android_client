package com.dominikdomotor.nextcloudpasswords.ui.passwords

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dominikdomotor.nextcloudpasswords.*
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import me.zhanghai.android.fastscroll.FastScrollerBuilder


class PasswordsFragment : Fragment() {
	
	private lateinit var passwordsRecyclerViewAdapter: PasswordsRecyclerViewAdapter
	private lateinit var linearLayoutManager: LinearLayoutManager
	
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View? {
		
		return inflater.inflate(R.layout.fragment_passwords, container, false)
	}
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		requireActivity().onBackPressedDispatcher.addCallback(this) {
			if (activity?.findViewById<SearchView>(R.id.search)?.isIconified == false) {
				activity?.findViewById<SearchView>(R.id.search)?.setQuery("", true)
				activity?.findViewById<SearchView>(R.id.search)?.isIconified = true
			} else {
				activity?.finish()
			}
		}
	}
	
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		
		activity?.findViewById<androidx.appcompat.widget.Toolbar>(R.id.myToolbar)?.inflateMenu(R.menu.passwords_overview_menu)
		activity?.findViewById<SearchView>(R.id.search)?.maxWidth = Integer.MAX_VALUE
		
		activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.setOnClickListener {
			it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.start_rotating_clockwise))
			PasswordManager.getPartners(requireActivity())
			PasswordManager.pullPasswords(requireActivity()) {
				activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.animation?.repeatCount = 0
//				activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.clearAnimation()
				passwordsRecyclerViewAdapter.showAllPasswords()
			}
			try {
//				startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE))
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		
		activity?.findViewById<SearchView>(R.id.search)?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
			override fun onQueryTextSubmit(query: String): Boolean {
				return false
			}
			
			override fun onQueryTextChange(query: String): Boolean {
				if (query.isNotEmpty()) {
					passwordsRecyclerViewAdapter.filterPasswordList(query)
				} else {
					passwordsRecyclerViewAdapter.showAllPasswords()
				}
				return false
			}
		})
		
		val recyclerviewPasswords = activity?.findViewById<RecyclerView>(R.id.recyclerview_passwords)!!
		val dividerItemDecoration = DividerItemDecoration(
			recyclerviewPasswords.context, DividerItemDecoration.VERTICAL
		)
		recyclerviewPasswords.addItemDecoration(dividerItemDecoration)
		recyclerviewPasswords.setHasFixedSize(true)
		passwordsRecyclerViewAdapter = PasswordsRecyclerViewAdapter(requireActivity())
		linearLayoutManager = LinearLayoutManager(activity)
		recyclerviewPasswords.adapter = passwordsRecyclerViewAdapter
		linearLayoutManager.initialPrefetchItemCount = 10
		recyclerviewPasswords.layoutManager = linearLayoutManager
		passwordsRecyclerViewAdapter.showAllPasswords()
		val fastScrollerBuilder = FastScrollerBuilder(recyclerviewPasswords)
		
		
		fastScrollerBuilder.useMd2Style()
		val fastScroller = fastScrollerBuilder.build()
		
		fastScroller.setPadding(0, 0, 0, 0)
		
		
		
		recyclerviewPasswords.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				super.onScrolled(recyclerView, dx, dy)
				if (dy > 0) {
//					println("down")
					activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.hide()
					// Scrolling down
				} else if (dy < 0) {
//					println("up")
					if (activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.isShown == false) {
						activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.show()
					}
					// Scrolling up
				}
			}
		})
		
		if (PasswordManager.getPasswords()[0].id.isEmpty()) {
			if (SharedPreferencesManager.getSharedPreferences().contains(SPKeys.logged_in)) {
				if (SharedPreferencesManager.getSharedPreferences().getBoolean(SPKeys.logged_in, false)) {
					activity?.findViewById<ActionMenuItemView>(R.id.refresh)
						?.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.start_rotating_clockwise))
					PasswordManager.getPartners(requireActivity())
					PasswordManager.pullPasswords(requireActivity()) {
						passwordsRecyclerViewAdapter.showAllPasswords()
						activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.clearAnimation()
//						activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.animation?.repeatCount = 0
						PasswordManager.pullPasswordIcons(requireActivity()) {
							passwordsRecyclerViewAdapter.showAllPasswords()
//							linearLayoutManager.smoothScrollToPosition(recyclerviewPasswords, RecyclerView.State(), passwordsRecyclerViewAdapter.itemCount)
//							linearLayoutManager.smoothScrollToPosition(recyclerviewPasswords, RecyclerView.State(), 0)
						}
					}
				}
			}
		}
		
		activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.setOnClickListener {
			val bottomSheetDialog = BottomSheetDialog(requireContext())
			
			val passwordCreateView = this.layoutInflater.inflate(R.layout.password_create_bottom_sheet_dialog, null)
			
			passwordCreateView.findViewById<ImageButton>(R.id.cancel_password_creation).setOnClickListener {
				bottomSheetDialog.dismiss()
			}
			bottomSheetDialog.setCancelable(true)
			bottomSheetDialog.setContentView(passwordCreateView)
			bottomSheetDialog.show()
			
			bottomSheetDialog.findViewById<ImageButton>(R.id.generateRandomPasswordButton)?.setOnClickListener{
				bottomSheetDialog.findViewById<EditText>(R.id.create_password_password)?.setText(PasswordManager.generateRandomPassword())
			}
			bottomSheetDialog.findViewById<EditText>(R.id.create_password_password)?.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
			
			val urlInput = passwordCreateView.findViewById<EditText>(R.id.create_password_url)
			
			passwordCreateView.findViewById<ImageButton>(R.id.create_passwords_checkmark_button).setOnClickListener {
				
				if (passwordCreateView.findViewById<EditText>(R.id.create_password_label).text.toString().isEmpty()) {
					requireActivity().runOnUiThread {
						Toast.makeText(requireActivity(), getString(R.string.label_is_required_for_password_creation), Toast.LENGTH_LONG).show()
					}
				} else if (passwordCreateView.findViewById<EditText>(R.id.create_password_password).text.toString().isEmpty()) {
					requireActivity().runOnUiThread {
						Toast.makeText(requireActivity(), getString(R.string.password_is_required_for_password_creation), Toast.LENGTH_LONG).show()
					}
				} else if (urlInput.text.toString().isNotEmpty()) {
					if (!URLUtil.isValidUrl(urlInput.text.toString())) {
						requireActivity().runOnUiThread {
							Toast.makeText(requireActivity(), getString(R.string.not_a_valid_url_alert_message), Toast.LENGTH_LONG).show()
						}
						urlInput.setText(
							URLUtil.guessUrl(urlInput.text.toString().filter { !it.isWhitespace() }).replace("http://www.", "https://", true)
								.replace("http:", "https:", true)//.dropLastWhile { it == '/' || it.isWhitespace() }
						)
						urlInput.setSelection(urlInput.length())//placing cursor at the end of the tex
						// whitespace at the end of the url results in the authentication process not working, so trying to remove them and letting the user know
					} else if (urlInput.text.toString().contains(" ")) {
						requireActivity().runOnUiThread {
							Toast.makeText(requireActivity(), getString(R.string.whitespaces_in_url_alert_message), Toast.LENGTH_LONG).show()
						}
						urlInput.setText(urlInput.text.toString().filter { !it.isWhitespace() })
						urlInput.setSelection(urlInput.length())//placing cursor at the end of the text
						// if everything is ok with the entered url the next activity is opened and the server url is passed
					} else if (URLUtil.isValidUrl(urlInput.text.toString())) {
						PasswordManager.createPassword(
							requireActivity(),
							label = passwordCreateView.findViewById<EditText>(R.id.create_password_label).text.toString(),
							username = passwordCreateView.findViewById<EditText>(R.id.create_password_username).text.toString(),
							password = passwordCreateView.findViewById<EditText>(R.id.create_password_password).text.toString(),
							url = passwordCreateView.findViewById<EditText>(R.id.create_password_url).text.toString(),
							notes = passwordCreateView.findViewById<EditText>(R.id.create_password_notes).text.toString()
						) {
							requireActivity().runOnUiThread {
								bottomSheetDialog.dismiss()
							}
						}
						
					}
				} else {
					PasswordManager.createPassword(
						requireActivity(),
						label = passwordCreateView.findViewById<EditText>(R.id.create_password_label).text.toString(),
						username = passwordCreateView.findViewById<EditText>(R.id.create_password_username).text.toString(),
						password = passwordCreateView.findViewById<EditText>(R.id.create_password_password).text.toString(),
						url = passwordCreateView.findViewById<EditText>(R.id.create_password_url).text.toString(),
						notes = passwordCreateView.findViewById<EditText>(R.id.create_password_notes).text.toString()
					) {
						requireActivity().runOnUiThread {
							bottomSheetDialog.dismiss()
						}
					}
				}
			}
		}
		
	}
}
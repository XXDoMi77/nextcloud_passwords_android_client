package com.dominikdomotor.nextcloudpasswords.fragments.passwords

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dominikdomotor.nextcloudpasswords.*
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.managers.NM
import com.dominikdomotor.nextcloudpasswords.managers.SM
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import me.zhanghai.android.fastscroll.FastScrollerBuilder


class PasswordsFragment : Fragment() {
	
	private lateinit var passwordsRecyclerViewAdapter: PasswordsRecyclerViewAdapter
	private lateinit var linearLayoutManager: LinearLayoutManager
	
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
		
		try {
			
			activity?.findViewById<androidx.appcompat.widget.Toolbar>(R.id.myToolbar)?.inflateMenu(R.menu.passwords_overview_menu)
			activity?.findViewById<SearchView>(R.id.search)?.maxWidth = Integer.MAX_VALUE
			
			activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.setOnClickListener {
				it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.start_rotating_clockwise))
//			NM.pullPartners(requireActivity(), requireContext().applicationContext)
				NM.pullDataFromServer({
					activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.animation?.repeatCount = 0
//				activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.clearAnimation()
					passwordsRecyclerViewAdapter.showAllPasswords()
				})
				try {
//				startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE))
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}
			
			
			val recyclerviewPasswords = activity?.findViewById<RecyclerView>(R.id.recyclerview_passwords)!!
			val dividerItemDecoration = DividerItemDecoration(
				recyclerviewPasswords.context, DividerItemDecoration.VERTICAL
			)
			recyclerviewPasswords.addItemDecoration(dividerItemDecoration)
			recyclerviewPasswords.setHasFixedSize(true)
			linearLayoutManager = LinearLayoutManager(activity)
			linearLayoutManager.initialPrefetchItemCount = 10
			recyclerviewPasswords.layoutManager = linearLayoutManager
			
			//checking if user is logged in
			if (SM.getSettings().loggedIn && SM.getPasswords().isNotEmpty()) {
				GF.println("pull passwords")
				requireActivity().runOnUiThread {
					passwordsRecyclerViewAdapter = PasswordsRecyclerViewAdapter(requireActivity())
					recyclerviewPasswords.adapter = passwordsRecyclerViewAdapter
					val fastScrollerBuilder = FastScrollerBuilder(recyclerviewPasswords)
					fastScrollerBuilder.useMd2Style()
					val fastScroller = fastScrollerBuilder.build()
					
					fastScroller.setPadding(0, 0, 0, 0)
					
					recyclerviewPasswords.addOnScrollListener(object : RecyclerView.OnScrollListener() {
						override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
							super.onScrolled(recyclerView, dx, dy)
							if (dy > 0) {
								activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.hide()
								// Scrolling down
							} else if (dy < 0) {
								if (activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.isShown == false) {
									activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.show()
								}
								// Scrolling up
							}
						}
					})
				}
				NM.pullFavicons {
					requireActivity().runOnUiThread {
						recyclerviewPasswords.adapter?.notifyDataSetChanged()
					}
				}
			} else {
				activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.start_rotating_clockwise))
				NM.pullDataFromServer(
					onEverythingPulled = {
						requireActivity().runOnUiThread {
							
							passwordsRecyclerViewAdapter = PasswordsRecyclerViewAdapter(requireActivity())
							recyclerviewPasswords.adapter = passwordsRecyclerViewAdapter
							val fastScrollerBuilder = FastScrollerBuilder(recyclerviewPasswords)
							fastScrollerBuilder.useMd2Style()
							val fastScroller = fastScrollerBuilder.build()
							
							fastScroller.setPadding(0, 0, 0, 0)
							
							recyclerviewPasswords.addOnScrollListener(object : RecyclerView.OnScrollListener() {
								override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
									super.onScrolled(recyclerView, dx, dy)
									if (dy > 0) {
										activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.hide()
										// Scrolling down
									} else if (dy < 0) {
										if (activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.isShown == false) {
											activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.show()
										}
										// Scrolling up
									}
								}
							})
							activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.animation?.repeatCount = 0
						}
					},
					onNewFaviconPulled = {
						requireActivity().runOnUiThread {
							recyclerviewPasswords.adapter?.notifyDataSetChanged()
						}
					}
				)
				
			}
			
			activity?.findViewById<SearchView>(R.id.search)?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
				override fun onQueryTextSubmit(query: String): Boolean {
					return false
				}
				
				override fun onQueryTextChange(query: String): Boolean {
					if (query.isNotEmpty()) {
						passwordsRecyclerViewAdapter.filterPasswordList(query)
//						passwordsRecyclerViewAdapter.showAllPasswords()
					} else {
						passwordsRecyclerViewAdapter.showAllPasswords()
					}
					return false
				}
			})
			
			activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.setOnClickListener {
				val bottomSheetDialog = BottomSheetDialog(requireContext())

				val passwordCreateView = this.layoutInflater.inflate(R.layout.password_create_bottom_sheet_dialog, null)

				passwordCreateView.findViewById<ImageButton>(R.id.cancel_password_creation).setOnClickListener {
					bottomSheetDialog.dismiss()
				}

				bottomSheetDialog.setCancelable(false)
				bottomSheetDialog.setContentView(passwordCreateView)

				bottomSheetDialog.setOnShowListener {
					val bottomSheet = (it as BottomSheetDialog).findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
					val behavior = BottomSheetBehavior.from(bottomSheet!!)
					behavior.state = BottomSheetBehavior.STATE_EXPANDED
//					behavior.peekHeight = 0
				}

				bottomSheetDialog.show()
			
//			var popupWindow: PopupWindow
//
//			activity?.findViewById<FloatingActionButton>(R.id.add_password_floatingactionbutton)?.setOnClickListener {
//				val inflater = layoutInflater
//				val passwordCreateView = inflater.inflate(R.layout.password_create_bottom_sheet_dialog, null)
//
//
//
////				val width = LinearLayout.LayoutParams.MATCH_PARENT
////				val height = LinearLayout.LayoutParams.MATCH_PARENT
//				val width = requireView().width.minus(100)
//				val height = requireView().height.minus(100)
//				val focusable = true // lets taps outside the popup also dismiss it
//				popupWindow = PopupWindow(passwordCreateView, width, height, focusable)
//				popupWindow.animationStyle = R.style.PopupAnimation
//				// show the popup window
//				// which view you pass in doesn't matter, it is only used for the window token
//				popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)
//
//				passwordCreateView.findViewById<ImageButton>(R.id.cancel_password_creation).setOnClickListener {
//					popupWindow.dismiss()
//				}


				
//				passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordNotes).setOn
				
				passwordCreateView.findViewById<ImageButton>(R.id.generateRandomPasswordButton)?.setOnClickListener {
					passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordPassword)?.editText?.setText(generateRandomPassword())
				}
				passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordPassword)?.editText?.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
				
				val urlInput = passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordURL)?.editText
				
				passwordCreateView.findViewById<ImageButton>(R.id.create_passwords_checkmark_button).setOnClickListener {
					
					if (passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordLabel).editText?.text.toString().isEmpty()) {
						requireActivity().runOnUiThread {
							Toast.makeText(requireActivity(), getString(R.string.label_is_required_for_password_creation), Toast.LENGTH_LONG).show()
						}
					} else if (passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordPassword).editText?.text.toString().isEmpty()) {
						requireActivity().runOnUiThread {
							Toast.makeText(requireActivity(), getString(R.string.password_is_required_for_password_creation), Toast.LENGTH_LONG).show()
						}
					} else if (urlInput?.text.toString().isNotEmpty()) {
						if (!URLUtil.isValidUrl(urlInput?.text.toString())) {
							requireActivity().runOnUiThread {
								Toast.makeText(requireActivity(), getString(R.string.not_a_valid_url_alert_message), Toast.LENGTH_LONG).show()
							}
							urlInput?.setText(
								URLUtil.guessUrl(urlInput.text.toString().filter { !it.isWhitespace() }).replace("http://www.", "https://", true)
									.replace("http:", "https:", true)//.dropLastWhile { it == '/' || it.isWhitespace() }
							)
							urlInput?.setSelection(urlInput.length())//placing cursor at the end of the tex
							// whitespace at the end of the url results in the authentication process not working, so trying to remove them and letting the user know
						} else if (urlInput?.text.toString().contains(" ")) {
							requireActivity().runOnUiThread {
								Toast.makeText(requireActivity(), getString(R.string.whitespaces_in_url_alert_message), Toast.LENGTH_LONG).show()
							}
							urlInput?.setText(urlInput.text.toString().filter { !it.isWhitespace() })
							urlInput?.setSelection(urlInput.length())//placing cursor at the end of the text
							// if everything is ok with the entered url the next activity is opened and the server url is passed
						} else if (URLUtil.isValidUrl(urlInput?.text.toString())) {
							val password = Password()
							password.label = passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordLabel)?.editText?.text.toString()
							password.username = passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordUsername)?.editText?.text.toString()
							password.password = passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordPassword)?.editText?.text.toString()
							password.url = urlInput?.text.toString()
							password.notes = passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordNotes)?.editText?.text.toString()
							
							NM.createPassword(password) {
								requireActivity().runOnUiThread {
									bottomSheetDialog.dismiss()
									activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.start_rotating_clockwise))
									NM.pullDataFromServer({
										GF.println("I am being executed")
										requireActivity().runOnUiThread {
											passwordsRecyclerViewAdapter.showAllPasswords()
											activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.animation?.repeatCount = 0
										}
									})
								}
							}
							
						}
					} else {
						val password = Password()
						password.label = passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordLabel).editText?.text.toString()
						password.username = passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordUsername).editText?.text.toString()
						password.password = passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordPassword).editText?.text.toString()
						password.url = urlInput?.text.toString()
						password.notes = passwordCreateView.findViewById<TextInputLayout>(R.id.createPasswordNotes).editText?.text.toString()
						
						NM.createPassword(password) {
							requireActivity().runOnUiThread {
								bottomSheetDialog.dismiss()
								activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.start_rotating_clockwise))
								NM.pullDataFromServer({
									GF.println("I am being executed")
									requireActivity().runOnUiThread {
										passwordsRecyclerViewAdapter.showAllPasswords()
										activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.animation?.repeatCount = 0
									}
								})
							}
						}
					}
				}
			}
			
			
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	private fun generateRandomPassword(): String {
		val length: Int = SM.getSettings().passwordLength
		val numberOfSpecialCharacters: Int = SM.getSettings().includedSymbolsQuantity
		val excludeSimilarCharacters: Boolean = SM.getSettings().excludeSimilarCharacters
		val lettersNumbers = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
		val specialCharacters = "#*+,-.:<=>?@^_~"
		val similarCharacters = "iIlL1oO0uvcemnwWbdpqsS5"
		
		var availableCharacters = lettersNumbers.filter { it !in similarCharacters }

//		if (numberOfSpecialCharacters > 0){
//			availableCharacters += specialCharacters
//		}
		
		if (!excludeSimilarCharacters) {
			availableCharacters += similarCharacters
		}
		
		GF.println("availableCharacters: $availableCharacters")
		
		// Generate the first part of the string with random characters
		val randomPasswordWithoutSpecialCharacters = (1..length)
			.map { availableCharacters.random() }
			.joinToString("")
		
		
		GF.println("randomPasswordWithoutSpecialCharacters: $randomPasswordWithoutSpecialCharacters")
		
		// Create a list of indices where replacements will occur
		val indicesToReplace = (randomPasswordWithoutSpecialCharacters.indices).shuffled().take(numberOfSpecialCharacters)
		
		// Replace characters at the selected indices with characters from the replacementCharacterSet
		val result = randomPasswordWithoutSpecialCharacters.mapIndexed { index, char ->
			if (index in indicesToReplace) {
				specialCharacters.random()
			} else {
				char
			}
		}.joinToString("")
		
		
		GF.println("generatedPassword: $result")
		
		return result
	}
}
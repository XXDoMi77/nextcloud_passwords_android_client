package com.dominikdomotor.nextcloudpasswords.ui.passwords

import android.graphics.drawable.Drawable
import android.opengl.Visibility
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.SearchView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SmoothScroller
import com.dominikdomotor.nextcloudpasswords.*
import com.dominikdomotor.nextcloudpasswords.databinding.FragmentPasswordsBinding
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupTextProvider


class PasswordsFragment : Fragment() {
	private var _binding: FragmentPasswordsBinding? = null
	
	// This property is only valid between onCreateView and
	// onDestroyView.
	private val binding get() = _binding!!
	
	private lateinit var passwordsRecyclerViewAdapter: PasswordsRecyclerViewAdapter
	private lateinit var linearLayoutManager: LinearLayoutManager
	
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
	): View {
		
		_binding = FragmentPasswordsBinding.inflate(inflater, container, false)
		val root: View = binding.root
		
		// This callback will only be called when MyFragment is at least Started.
		// This callback will only be called when MyFragment is at least Started.
		val callback: OnBackPressedCallback = object : OnBackPressedCallback(true /* enabled by default */) {
			override fun handleOnBackPressed() {
				// Handle the back button event
				if (activity?.findViewById<SearchView>(R.id.search)?.isIconified == false) {
					activity?.findViewById<SearchView>(R.id.search)?.setQuery("", true)
					activity?.findViewById<SearchView>(R.id.search)?.isIconified = true
				}
			}
		}
		requireActivity().onBackPressedDispatcher.addCallback(requireActivity(), callback)
		// The callback can be enabled or disabled here or in handleOnBackPressed()
		
		return root
	}
	
	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
	
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.myToolbar.inflateMenu(R.menu.passwords_overview_menu)
		
		activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.setOnClickListener {
			it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.start_rotating_clockwise))
			requireActivity().pullPasswords {
				requireActivity().findViewById<ActionMenuItemView>(R.id.refresh)?.animation?.repeatCount = 0
				passwordsRecyclerViewAdapter.showAllPasswords()
				//activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.clearAnimation()
			}
			try {
//				startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE))
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		
		activity?.findViewById<SearchView>(R.id.search)?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
			override fun onQueryTextSubmit(query: String): Boolean {
				println("nah: $query")
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
		var fastScrollerBuilder = FastScrollerBuilder(recyclerviewPasswords)
//		fastScrollerBuilder.setPopupTextProvider(PopupTextProvider { position -> passwords[position].label[0].toString() })
//		ContextCompat.getDrawable(requireActivity(), R.drawable.line_drawable)?.let { fastScrollerBuilder.setTrackDrawable(it) }
//		ContextCompat.getDrawable(requireActivity(), R.drawable.icon_foreground_36)?.let { fastScrollerBuilder.setThumbDrawable(it) }
		fastScrollerBuilder.useMd2Style()
		fastScrollerBuilder.build()
		
		if (passwords[0].id.isEmpty()){
			if (App.sharedPreferences().contains(SPKeys.logged_in)) {
				if (App.sharedPreferences().getBoolean(SPKeys.logged_in, false)) {
					activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.start_rotating_clockwise))
					requireActivity().pullPasswords {
						passwordsRecyclerViewAdapter.showAllPasswords()
						activity?.findViewById<ActionMenuItemView>(R.id.refresh)?.animation?.repeatCount = 0
						requireActivity().pullPasswordIcons{
							passwordsRecyclerViewAdapter.showAllPasswords()
//							linearLayoutManager.smoothScrollToPosition(recyclerviewPasswords, RecyclerView.State(), passwordsRecyclerViewAdapter.itemCount)
//							linearLayoutManager.smoothScrollToPosition(recyclerviewPasswords, RecyclerView.State(), 0)
						}
					}
				}
			}
		}
	}
}
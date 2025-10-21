package com.dominikdomotor.nextcloudpasswords.fragments.folders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.dominikdomotor.nextcloudpasswords.databinding.FragmentFoldersBinding

class FoldersFragment : Fragment() {
	
	private var _binding: FragmentFoldersBinding? = null
	
	// This property is only valid between onCreateView and
	// onDestroyView.
	private val binding get() = _binding!!
	
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
	): View {
		val foldersViewModel = ViewModelProvider(this).get(FoldersViewModel::class.java)
		
		_binding = FragmentFoldersBinding.inflate(inflater, container, false)
		val root: View = binding.root
		
		val textView: TextView = binding.textHome
		foldersViewModel.text.observe(viewLifecycleOwner) {
			textView.text = it
		}
		return root
	}
	
	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
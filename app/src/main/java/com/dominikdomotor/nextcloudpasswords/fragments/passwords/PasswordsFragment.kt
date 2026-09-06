package com.dominikdomotor.nextcloudpasswords.fragments.passwords

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.data.FaviconWarmUpOrder
import com.dominikdomotor.nextcloudpasswords.databinding.FragmentPasswordsBinding
import com.dominikdomotor.nextcloudpasswords.fragments.BackHandler
import com.dominikdomotor.nextcloudpasswords.fragments.hideFloatingActionsOnScroll
import com.dominikdomotor.nextcloudpasswords.fragments.setVisible
import com.dominikdomotor.nextcloudpasswords.managers.FaviconStore
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import com.dominikdomotor.nextcloudpasswords.ui.FaviconBinder
import com.dominikdomotor.nextcloudpasswords.ui.PasswordListAdapter
import com.dominikdomotor.nextcloudpasswords.ui.usePaletteStyle
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.FastScrollerBuilder

@AndroidEntryPoint
class PasswordsFragment : Fragment(), BackHandler {
    @Inject lateinit var uiMessageManager: UiMessageManager
    @Inject lateinit var faviconStore: FaviconStore

    private val viewModel: PasswordsViewModel by viewModels()
    private val actionsViewModel: PasswordActionsViewModel by activityViewModels()

    private var _binding: FragmentPasswordsBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var adapter: PasswordListAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var fastScroller: FastScroller? = null
    private var listAnimator: RecyclerView.ItemAnimator? = null

    /** Ids the list held before a create whose new row has not been found yet; null when nothing is waiting. */
    private var idsBeforeCreate: Set<String>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPasswordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUpToolbar()
        setUpList()
        setUpSearch()

        binding.passwordsPullRefresh.onRefresh = viewModel::refresh
        binding.addPasswordFloatingactionbutton.setOnClickListener { showCreatePasswordDialog() }

        observeViewModel()
    }

    /** Back closes the search field first; otherwise the activity decides. */
    override fun handleBack(): Boolean {
        if (_binding == null || binding.passwordSearchLayout.visibility != View.VISIBLE) return false
        closeSearch()
        return true
    }

    private fun setUpToolbar() {
        binding.passwordToolbar.apply {
            inflateMenu(R.menu.passwords_overview_menu)
            setOnMenuItemClickListener { item ->
                // Search is the only toolbar action: pulling refreshes, and the app also syncs on
                // its own when it comes to the foreground.
                if (item.itemId == R.id.search) {
                    openSearch()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun setUpList() {
        adapter =
            PasswordListAdapter(
                favicons = FaviconBinder(faviconStore, viewLifecycleOwner.lifecycleScope),
                onClick = { detailsController().show(it) },
                onCopyUsername = viewModel::copyUsername,
                onCopyPassword = viewModel::copyPassword,
            )
        binding.recyclerviewPasswords.apply {
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            setHasFixedSize(true)
            linearLayoutManager = LinearLayoutManager(context).apply { initialPrefetchItemCount = PREFETCH_ITEMS }
            layoutManager = linearLayoutManager
            adapter = this@PasswordsFragment.adapter
            hideFloatingActionsOnScroll(binding.addPasswordFloatingactionbutton)
        }
        listAnimator = binding.recyclerviewPasswords.itemAnimator
        fastScroller = FastScrollerBuilder(binding.recyclerviewPasswords).usePaletteStyle(requireContext()).build()
        binding.recyclerviewPasswords.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    // Re-prioritise once the user settles somewhere new in the list.
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        warmFaviconsVisibleFirst()
                        adapter.playPendingHighlight()
                    }
                }
            }
        )
    }

    /**
     * Asks for every cached favicon, rows on screen first.
     *
     * All of them get decoded, not only the visible ones — but one at a time and in this order, so what the user is
     * looking at resolves immediately and the rest fill in behind it.
     */
    private fun warmFaviconsVisibleFirst() {
        if (_binding == null) return
        val ids = adapter.currentList.map { it.id }
        if (ids.isEmpty()) return

        val first = linearLayoutManager.findFirstVisibleItemPosition()
        val last = linearLayoutManager.findLastVisibleItemPosition()
        val ordered =
            if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) ids
            else FaviconWarmUpOrder.visibleFirst(ids, first, last)
        viewModel.warmFavicons(ordered)
    }

    private fun setUpSearch() {
        binding.passwordSearchInput.doAfterTextChanged { viewModel.search(it?.toString().orEmpty()) }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    var lastQuery: String? = null
                    viewModel.items.collect { items ->
                        // Every keystroke re-ranks the list, so the best match is row 0 — but the RecyclerView keeps
                        // whatever offset it had and would leave the user looking at the middle of the results.
                        // Only on a query change: a background sync must not yank the list out from under them.
                        val query = viewModel.query.value
                        val queryChanged = query != lastQuery
                        lastQuery = query
                        adapter.submitList(items) {
                            if (queryChanged) linearLayoutManager.scrollToPosition(0)
                            updateFastScroller(items.size)
                            // Wait for the layout pass the new list triggers: until it runs, the layout manager
                            // still reports positions from the previous list, so "visible first" would prioritise
                            // rows that have moved or disappeared.
                            _binding?.recyclerviewPasswords?.post {
                                warmFaviconsVisibleFirst()
                                revealPendingCreation()
                            }
                        }
                    }
                }
                launch { faviconStore.updates.collect(adapter::notifyFaviconChanged) }
                launch {
                    viewModel.suppressSearchAnimation.collect { suppress ->
                        binding.recyclerviewPasswords.itemAnimator = if (suppress) null else listAnimator
                    }
                }
                launch { viewModel.isRefreshing.collect(binding.passwordsSyncIndicator::setVisible) }
            }
        }
    }

    private fun openSearch() {
        binding.passwordToolbar.visibility = View.GONE
        binding.passwordSearchLayout.visibility = View.VISIBLE
        binding.passwordSearchInput.requestFocus()
        binding.passwordSearchInput.post { insetsController()?.show(WindowInsetsCompat.Type.ime()) }
    }

    private fun closeSearch() {
        binding.passwordSearchInput.setText("")
        binding.passwordSearchLayout.visibility = View.GONE
        binding.passwordToolbar.visibility = View.VISIBLE
        insetsController()?.hide(WindowInsetsCompat.Type.ime())
    }

    private fun insetsController() =
        activity?.window?.let { WindowCompat.getInsetsController(it, binding.passwordSearchInput) }

    private fun folderPicker() = FolderPicker(requireActivity()) { actionsViewModel.foldersSnapshot() }

    private fun detailsController() =
        PasswordDetailsController(requireActivity(), actionsViewModel, folderPicker(), uiMessageManager)

    private fun showCreatePasswordDialog() {
        PasswordEditorSheet(requireActivity(), actionsViewModel.settings, folderPicker()).show { password, outcome ->
            // The create call answers with nothing but success, so the new row is whichever id the list did not hold
            // before it.
            val idsBefore = adapter.currentList.mapTo(mutableSetOf()) { it.id }
            actionsViewModel.create(
                password,
                onCreated = {
                    outcome()
                    revealCreatedPassword(idsBefore)
                },
                onFailed = outcome::failed,
            )
        }
    }

    /**
     * Scrolls to the password that was just created and flashes it.
     *
     * The refreshed list may not have reached the adapter yet when the request returns, so the ids are kept and
     * [revealPendingCreation] tries again from the next list update.
     */
    private fun revealCreatedPassword(idsBefore: Set<String>) {
        if (!scrollToCreatedPassword(idsBefore)) idsBeforeCreate = idsBefore
    }

    private fun revealPendingCreation() {
        // One attempt only: a row that is not there by now is filtered out by the search, and a later sync must not
        // flash whatever it happens to bring in.
        val idsBefore = idsBeforeCreate ?: return
        idsBeforeCreate = null
        scrollToCreatedPassword(idsBefore)
    }

    private fun scrollToCreatedPassword(idsBefore: Set<String>): Boolean {
        val recyclerView = _binding?.recyclerviewPasswords ?: return true
        val index = adapter.currentList.indexOfFirst { it.id !in idsBefore }
        if (index < 0) return false
        adapter.highlight(adapter.currentList[index].id)
        recyclerView.smoothScrollToPosition(index)
        return true
    }

    /**
     * The fast scroller is only useful once the list is long enough to be worth dragging, so it is pushed off-screen
     * below that threshold.
     */
    private fun updateFastScroller(itemCount: Int) {
        fastScroller?.setPadding(0, 0, if (itemCount > FAST_SCROLL_THRESHOLD) 0 else OFF_SCREEN_PADDING, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter.cancelHighlight()
        idsBeforeCreate = null
        fastScroller = null
        listAnimator = null
        _binding = null
    }

    private companion object {
        const val PREFETCH_ITEMS = 10
        const val FAST_SCROLL_THRESHOLD = 50
        const val OFF_SCREEN_PADDING = 10_000
    }
}

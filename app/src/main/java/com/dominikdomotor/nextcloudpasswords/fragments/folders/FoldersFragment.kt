package com.dominikdomotor.nextcloudpasswords.fragments.folders

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.databinding.FragmentFoldersBinding
import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.fragments.BackHandler
import com.dominikdomotor.nextcloudpasswords.fragments.hideFloatingActionsOnScroll
import com.dominikdomotor.nextcloudpasswords.fragments.passwords.FolderPicker
import com.dominikdomotor.nextcloudpasswords.fragments.passwords.PasswordActionsViewModel
import com.dominikdomotor.nextcloudpasswords.fragments.passwords.PasswordDetailsController
import com.dominikdomotor.nextcloudpasswords.fragments.passwords.PasswordEditorSheet
import com.dominikdomotor.nextcloudpasswords.fragments.setVisible
import com.dominikdomotor.nextcloudpasswords.managers.FaviconStore
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog
import com.dominikdomotor.nextcloudpasswords.ui.FaviconBinder
import com.dominikdomotor.nextcloudpasswords.ui.theme.themeColor
import com.google.android.material.R as MaterialR
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.FastScrollerBuilder

@AndroidEntryPoint
class FoldersFragment : Fragment(), BackHandler {
    @Inject lateinit var uiMessageManager: UiMessageManager
    @Inject lateinit var faviconStore: FaviconStore

    private val viewModel: FoldersViewModel by viewModels()
    private val actionsViewModel: PasswordActionsViewModel by activityViewModels()

    private var _binding: FragmentFoldersBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var adapter: FoldersRecyclerViewAdapter
    private var fastScroller: FastScroller? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFoldersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter =
            FoldersRecyclerViewAdapter(
                favicons = FaviconBinder(faviconStore, viewLifecycleOwner.lifecycleScope),
                onFolderOpen = { viewModel.open(it.id) },
                onFolderEdit = ::showFolderEditor,
                onFolderDelete = ::confirmDelete,
                onPasswordClick = { detailsController().show(it) },
                onCopyUsername = viewModel::copyUsername,
                onCopyPassword = viewModel::copyPassword,
            )
        binding.foldersList.layoutManager = LinearLayoutManager(requireContext())
        binding.foldersList.adapter = adapter
        binding.foldersList.hideFloatingActionsOnScroll(binding.addPassword, binding.addFolder)
        fastScroller = FastScrollerBuilder(binding.foldersList).useMd2Style().build()

        binding.addFolder.setOnClickListener { showFolderEditor(null) }
        binding.addFolder.contentDescription = getString(R.string.create_folder)
        binding.addPassword.setOnClickListener { showCreatePasswordDialog() }
        binding.addPassword.contentDescription = getString(R.string.create_password)
        binding.foldersPullRefresh.onRefresh = viewModel::refresh

        observeViewModel()
    }

    /** Back walks up the folder tree first; at the root the activity decides. */
    override fun handleBack(): Boolean = viewModel.navigateUp()

    /**
     * One collector for the whole view lifecycle.
     *
     * Folder navigation flows through the view model's `currentFolderId`, so opening a folder no longer starts an
     * extra, never-cancelled collector the way calling `refresh()` used to.
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collect { items -> adapter.submitList(items) { updateFastScroller(items.size) } }
                }
                launch { viewModel.breadcrumbs.collect(::renderBreadcrumbs) }
                launch { faviconStore.updates.collect(adapter::notifyFaviconChanged) }
                launch { viewModel.isRefreshing.collect(binding.foldersSyncIndicator::setVisible) }
            }
        }
    }

    private fun renderBreadcrumbs(path: List<Folder>) {
        binding.breadcrumbContainer.removeAllViews()
        val currentId = viewModel.currentFolderId.value

        addBreadcrumb(getString(R.string.home), Folder.ROOT_ID, currentId)
        path.forEach { folder ->
            addSeparator()
            addBreadcrumb(folder.label, folder.id, currentId)
        }
        binding.pathScroll.post { binding.pathScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun addBreadcrumb(label: String, id: String, currentId: String) {
        val density = resources.displayMetrics.density
        val isCurrent = id == currentId
        binding.breadcrumbContainer.addView(
            TextView(requireContext()).apply {
                text = label
                background = ContextCompat.getDrawable(requireContext(), R.drawable.folder_breadcrumb_background)
                gravity = Gravity.CENTER
                minHeight = (BREADCRUMB_HEIGHT_DP * density).toInt()
                setPadding((BREADCRUMB_PADDING_DP * density).toInt(), 0, (BREADCRUMB_PADDING_DP * density).toInt(), 0)
                setTextColor(
                    requireContext()
                        .themeColor(
                            if (isCurrent) MaterialR.attr.colorOnSurface else MaterialR.attr.colorOnSurfaceVariant
                        )
                )
                textSize = BREADCRUMB_TEXT_SIZE_SP
                if (!isCurrent) setOnClickListener { viewModel.open(id) }
            }
        )
    }

    private fun addSeparator() {
        val padding = (SEPARATOR_PADDING_DP * resources.displayMetrics.density).toInt()
        binding.breadcrumbContainer.addView(
            TextView(requireContext()).apply {
                text = getString(R.string.breadcrumb_separator)
                gravity = Gravity.CENTER
                minHeight = (BREADCRUMB_HEIGHT_DP * resources.displayMetrics.density).toInt()
                setPadding(padding, 0, padding, 0)
                setTextColor(requireContext().themeColor(MaterialR.attr.colorOnSurfaceVariant))
                textSize = BREADCRUMB_TEXT_SIZE_SP
            }
        )
    }

    private fun folderPicker() = FolderPicker(requireActivity()) { actionsViewModel.foldersSnapshot() }

    private fun detailsController() =
        PasswordDetailsController(requireActivity(), actionsViewModel, folderPicker(), uiMessageManager)

    private fun showCreatePasswordDialog() {
        PasswordEditorSheet(requireActivity(), actionsViewModel.settings, folderPicker()).show(
            defaultFolderId = viewModel.currentFolderId.value
        ) { password, dismiss ->
            actionsViewModel.create(password) { dismiss() }
        }
    }

    private fun showFolderEditor(folder: Folder?) {
        val content = layoutInflater.inflate(R.layout.dialog_folder_editor, null)
        val nameLayout = content.findViewById<TextInputLayout>(R.id.folder_name_layout)
        val nameInput = content.findViewById<EditText>(R.id.folder_name)
        nameInput.setText(folder?.label.orEmpty())

        var parentId = folder?.parent?.takeIf { it.isNotBlank() } ?: viewModel.currentFolderId.value

        // A folder may not be moved into itself or any of its descendants; that would make a cycle.
        val excluded = folder?.let(viewModel::invalidParentsFor).orEmpty()
        if (folder != null && parentId in excluded) parentId = Folder.ROOT_ID

        // The parent is only worth asking about when editing. Creating a folder while standing in one
        // already says where it goes, so the picker only asked the user to restate where they were.
        val parentLayout = content.findViewById<TextInputLayout>(R.id.folder_parent_layout)
        if (folder == null) {
            parentLayout.visibility = View.GONE
        } else {
            val picker = folderPicker()
            val parentInput = content.findViewById<EditText>(R.id.folder_parent)
            parentInput.setText(picker.folderName(parentId))
            val openPicker =
                View.OnClickListener {
                    picker.show(excluded) { id, name ->
                        parentId = id
                        parentInput.setText(name)
                    }
                }
            parentInput.setOnClickListener(openPicker)
            parentLayout.setEndIconOnClickListener(openPicker)
        }

        val dialog = AppDialog(requireActivity())
        dialog
            .title(if (folder == null) R.string.create_folder else R.string.edit_folder)
            .content(content)
            .button(R.string.cancel)
            .button(if (folder == null) R.string.create_folder else R.string.save, dismissOnClick = false) {
                val label = nameInput.text.toString().trim()
                when {
                    label.isEmpty() -> nameLayout.error = getString(R.string.folder_name_required)
                    folder == null -> {
                        viewModel.createFolder(label, parentId)
                        dialog.dismiss()
                    }
                    else -> {
                        viewModel.updateFolder(folder, label, parentId)
                        dialog.dismiss()
                    }
                }
            }
            .showCompact()
    }

    private fun confirmDelete(folder: Folder) {
        AppDialog(requireActivity())
            .message(getString(R.string.delete_folder_confirmation, folder.label))
            .button(R.string.cancel)
            .button(R.string.yes, destructive = true) { viewModel.deleteFolder(folder) }
            .showCompact()
    }

    private fun updateFastScroller(itemCount: Int) {
        fastScroller?.setPadding(0, 0, if (itemCount > FAST_SCROLL_THRESHOLD) 0 else OFF_SCREEN_PADDING, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fastScroller = null
        _binding = null
    }

    private companion object {
        const val BREADCRUMB_HEIGHT_DP = 36
        const val BREADCRUMB_PADDING_DP = 12
        const val SEPARATOR_PADDING_DP = 6
        const val BREADCRUMB_TEXT_SIZE_SP = 14f
        const val FAST_SCROLL_THRESHOLD = 50
        const val OFF_SCREEN_PADDING = 10_000
    }
}

package com.dominikdomotor.nextcloudpasswords.fragments.folders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.data.AccentColor
import com.dominikdomotor.nextcloudpasswords.data.ApiResult
import com.dominikdomotor.nextcloudpasswords.data.FolderTree
import com.dominikdomotor.nextcloudpasswords.data.PasswordRepository
import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import com.dominikdomotor.nextcloudpasswords.ui.SecureClipboard
import com.dominikdomotor.nextcloudpasswords.ui.reportFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the folder browser.
 *
 * The current folder is a [StateFlow] that feeds one long-lived [combine]. The fragment used to start a fresh
 * `repeatOnLifecycle` collector on every navigation without cancelling the previous one, so collectors accumulated for
 * the life of the view.
 */
@HiltViewModel
class FoldersViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: PasswordRepository,
    private val uiMessageManager: UiMessageManager,
    private val clipboard: SecureClipboard,
) : ViewModel() {
    private val _currentFolderId = MutableStateFlow(Folder.ROOT_ID)
    val currentFolderId: StateFlow<String> = _currentFolderId.asStateFlow()

    /**
     * True while a refresh the user asked for is running, wherever they started it.
     *
     * Shared across tabs, so pulling here and switching to the other list keeps showing progress.
     */
    val isRefreshing: StateFlow<Boolean> = repository.isUserRefreshing

    /** The accent to paint the sync sweep with: the user's colour, else the server's, else the built-in one. */
    val accentColor: StateFlow<Int> =
        repository.settings
            .map { AccentColor.of(context, it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                AccentColor.of(context, repository.settings.value),
            )

    val items: StateFlow<List<FolderListItem>> =
        combine(repository.folders, repository.passwords, _currentFolderId) { folders, passwords, currentId ->
                buildList {
                    folders
                        .filter { FolderTree.isChildOf(it, currentId) }
                        .forEach { folder ->
                            add(FolderListItem.FolderRow(folder, passwords.count { it.folder == folder.id }))
                        }
                    passwords.filter { it.folder == currentId }.forEach { add(FolderListItem.PasswordRow(it)) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Root-to-current folder chain, used to render the breadcrumb trail. */
    val breadcrumbs: StateFlow<List<Folder>> =
        combine(repository.folders, _currentFolderId) { folders, currentId -> FolderTree.pathTo(folders, currentId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val canNavigateUp: StateFlow<Boolean> =
        _currentFolderId
            .map { it != Folder.ROOT_ID }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    init {
        viewModelScope.launch { repository.load() }
    }

    fun open(folderId: String) {
        _currentFolderId.value = folderId
    }

    /** Moves to the parent folder. Returns false when already at the root. */
    fun navigateUp(): Boolean {
        val currentId = _currentFolderId.value
        if (currentId == Folder.ROOT_ID) return false
        val current = repository.folders.value.firstOrNull { it.id == currentId }
        _currentFolderId.value = current?.parent?.takeIf { it.isNotBlank() } ?: Folder.ROOT_ID
        return true
    }

    /** A refresh the user asked for, so it confirms success; the automatic ones stay quiet. */
    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            if (repository.sync(userInitiated = true).reportFailure(uiMessageManager).isSuccess) {
                uiMessageManager.show(R.string.password_list_fetch_successful)
            }
            // After the indicator clears: favicon downloads are rate limited and can run long.
            repository.downloadFavicons()
        }
    }

    fun createFolder(label: String, parent: String) =
        runFolderOperation(R.string.folder_successfully_created) { repository.createFolder(label, parent) }

    fun updateFolder(folder: Folder, label: String, parent: String) =
        runFolderOperation(R.string.folder_successfully_updated) { repository.updateFolder(folder, label, parent) }

    fun deleteFolder(folder: Folder) =
        runFolderOperation(R.string.folder_successfully_deleted) {
            repository.deleteFolder(folder).also {
                // Do not strand the user inside a folder that no longer exists.
                if (it.isSuccess && _currentFolderId.value == folder.id) {
                    _currentFolderId.value = folder.parent.takeIf { p -> p.isNotBlank() } ?: Folder.ROOT_ID
                }
            }
        }

    /** Folders that may not become [folder]'s parent, because doing so would create a cycle. */
    fun invalidParentsFor(folder: Folder): Set<String> = repository.invalidParentsFor(folder)

    fun copyUsername(password: Password) = clipboard.copy(password.username, "username")

    fun copyPassword(password: Password) = clipboard.copy(password.password, "password")

    private fun runFolderOperation(successMessageResId: Int, operation: suspend () -> ApiResult<Unit>) {
        viewModelScope.launch {
            when (val result = operation()) {
                is ApiResult.Success -> uiMessageManager.show(successMessageResId)
                is ApiResult.Failure -> result.reportFailure(uiMessageManager)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

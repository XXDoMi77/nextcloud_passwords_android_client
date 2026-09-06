package com.dominikdomotor.nextcloudpasswords.data

import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder

/** Pure folder-hierarchy queries, kept free of Android types so they can be unit tested. */
object FolderTree {
    /**
     * [folderId] plus every folder beneath it.
     *
     * These are exactly the folders that may not become [folderId]'s parent: choosing one would detach the subtree into
     * a cycle. The picker previously excluded only the folder itself.
     */
    fun selfAndDescendants(folders: List<Folder>, folderId: String): Set<String> {
        val result = mutableSetOf(folderId)
        var frontier = setOf(folderId)
        while (frontier.isNotEmpty()) {
            // `result.add` returning false also stops a malformed parent chain looping forever.
            frontier = folders.filter { it.parent in frontier && result.add(it.id) }.mapTo(mutableSetOf()) { it.id }
        }
        return result
    }

    /** Root-to-[folderId] chain, empty when the folder is unknown or is the root itself. */
    fun pathTo(folders: List<Folder>, folderId: String): List<Folder> {
        val path = ArrayDeque<Folder>()
        val seen = mutableSetOf<String>()
        var current = folders.firstOrNull { it.id == folderId }
        while (current != null && seen.add(current.id)) {
            path.addFirst(current)
            val parentId = current.parent
            current = folders.firstOrNull { it.id == parentId }
        }
        return path.toList()
    }

    /** Whether [folder] sits directly inside [parentId]; the root is also the home of blank parents. */
    fun isChildOf(folder: Folder, parentId: String): Boolean =
        folder.parent == parentId || (parentId == Folder.ROOT_ID && folder.parent.isEmpty())
}

package com.dominikdomotor.nextcloudpasswords.data

import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {
    private val root = Folder.ROOT_ID
    private val work = folder("work", parent = root)
    private val servers = folder("servers", parent = "work")
    private val backups = folder("backups", parent = "servers")
    private val personal = folder("personal", parent = root)
    private val all = listOf(work, servers, backups, personal)

    @Test
    fun selfAndDescendantsCoversTheWholeSubtree() {
        assertEquals(setOf("work", "servers", "backups"), FolderTree.selfAndDescendants(all, "work"))
    }

    @Test
    fun selfAndDescendantsExcludesUnrelatedBranches() {
        assertFalse("personal" in FolderTree.selfAndDescendants(all, "work"))
    }

    @Test
    fun leafHasOnlyItself() {
        assertEquals(setOf("backups"), FolderTree.selfAndDescendants(all, "backups"))
    }

    /** A folder moved under its own descendant would detach the subtree from the root. */
    @Test
    fun descendantIsNotAValidParent() {
        val invalid = FolderTree.selfAndDescendants(all, "work")
        assertTrue("servers" in invalid)
        assertTrue("backups" in invalid)
    }

    @Test
    fun cyclicParentChainTerminates() {
        val a = folder("a", parent = "b")
        val b = folder("b", parent = "a")
        assertEquals(setOf("a", "b"), FolderTree.selfAndDescendants(listOf(a, b), "a"))
        assertEquals(listOf("a", "b"), FolderTree.pathTo(listOf(a, b), "a").map { it.id }.sorted())
    }

    @Test
    fun pathToRunsFromRootDownwards() {
        assertEquals(listOf("work", "servers", "backups"), FolderTree.pathTo(all, "backups").map { it.id })
    }

    @Test
    fun pathToUnknownFolderIsEmpty() {
        assertTrue(FolderTree.pathTo(all, "does-not-exist").isEmpty())
    }

    @Test
    fun blankParentCountsAsRootChild() {
        val orphan = folder("orphan", parent = "")
        assertTrue(FolderTree.isChildOf(orphan, root))
        assertFalse(FolderTree.isChildOf(orphan, "work"))
    }

    @Test
    fun explicitRootParentCountsAsRootChild() {
        assertTrue(FolderTree.isChildOf(work, root))
        assertTrue(FolderTree.isChildOf(servers, "work"))
        assertFalse(FolderTree.isChildOf(servers, root))
    }

    private fun folder(id: String, parent: String) = Folder(id = id, label = id, parent = parent)
}

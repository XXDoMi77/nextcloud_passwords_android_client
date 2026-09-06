package com.dominikdomotor.nextcloudpasswords.data

import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.Owner
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.Receiver
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.SharesItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareDirectionTest {
    @Test
    fun aShareGrantedToSomebodyElseIsOutgoing() {
        val shares = listOf(share(id = "1", owner = "me", receiver = "alice"))

        assertEquals(listOf("1"), ShareDirection.outgoing(shares, "me").map { it.id })
        assertNull(ShareDirection.incoming(shares, "me"))
    }

    /** The case this exists for: the sheet used to render this as "shared with me". */
    @Test
    fun aShareGrantedToThisAccountIsIncoming() {
        val shares = listOf(share(id = "1", owner = "alice", receiver = "me"))

        assertEquals(emptyList<String>(), ShareDirection.outgoing(shares, "me").map { it.id })
        assertEquals("1", ShareDirection.incoming(shares, "me")?.id)
    }

    @Test
    fun bothDirectionsCanExistOnOnePassword() {
        val shares =
            listOf(
                share(id = "in", owner = "alice", receiver = "me"),
                share(id = "out", owner = "me", receiver = "bob"),
            )

        assertEquals(listOf("out"), ShareDirection.outgoing(shares, "me").map { it.id })
        assertEquals("in", ShareDirection.incoming(shares, "me")?.id)
    }

    /** The stored login name and the id the server reports on a share can differ in case. */
    @Test
    fun theReceiverIsMatchedCaseInsensitively() {
        val shares = listOf(share(id = "1", owner = "alice", receiver = "Me"))

        assertEquals("1", ShareDirection.incoming(shares, "me")?.id)
        assertEquals(emptyList<String>(), ShareDirection.outgoing(shares, "me").map { it.id })
    }

    /** Before settings have loaded the username is empty; that must not swallow a real share. */
    @Test
    fun aBlankUsernameMatchesNothing() {
        val shares = listOf(share(id = "1", owner = "alice", receiver = ""))

        assertNull(ShareDirection.incoming(shares, ""))
        assertEquals(listOf("1"), ShareDirection.outgoing(shares, "").map { it.id })
    }

    private fun share(id: String, owner: String, receiver: String) =
        SharesItem(
            client = "",
            created = 0,
            editable = true,
            expires = null,
            id = id,
            owner = Owner(id = owner, name = owner),
            password = "p1",
            receiver = Receiver(id = receiver, name = receiver),
            shareable = true,
            updatePending = false,
            updated = 0,
        )
}

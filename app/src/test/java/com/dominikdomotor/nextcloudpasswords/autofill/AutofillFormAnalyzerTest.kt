package com.dominikdomotor.nextcloudpasswords.autofill

import com.dominikdomotor.nextcloudpasswords.autofill.AutofillFormAnalyzer.Bounds
import com.dominikdomotor.nextcloudpasswords.autofill.AutofillFormAnalyzer.Candidate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Forms are built by hand here rather than captured, because the interesting cases are the ones a real device does not
 * hand you on demand: the form with no hints at all, the sign-up page that looks like a login, the search box sitting
 * on its own.
 */
class AutofillFormAnalyzerTest {
    @Test
    fun aFormThatDeclaresItsFieldsIsTakenAtItsWord() {
        val verdicts =
            AutofillFormAnalyzer.resolve(
                listOf(
                    field(0, declared = AutofillFieldType.USERNAME, at = 0),
                    field(1, declared = AutofillFieldType.PASSWORD, at = 1),
                )
            )

        assertEquals(AutofillFieldType.USERNAME, verdicts[0])
        assertEquals(AutofillFieldType.PASSWORD, verdicts[1])
    }

    /** Guessing alongside a form that already knows itself is how a correct form gains a wrong extra match. */
    @Test
    fun oneDeclaredFieldStopsAllGuessing() {
        val verdicts =
            AutofillFormAnalyzer.resolve(
                listOf(
                    field(0, at = 0), // no signal at all
                    field(1, declared = AutofillFieldType.PASSWORD, at = 1),
                )
            )

        assertEquals(AutofillFieldType.UNKNOWN, verdicts[0])
        assertEquals(AutofillFieldType.PASSWORD, verdicts[1])
    }

    @Test
    fun theFieldAboveThePasswordIsTheUsername() {
        val verdicts =
            AutofillFormAnalyzer.resolve(
                listOf(field(0, at = 0), field(1, passwordByType = true, at = 1))
            )

        assertEquals(AutofillFieldType.USERNAME, verdicts[0])
        assertEquals(AutofillFieldType.PASSWORD, verdicts[1])
    }

    /** Geometry, not traversal order: a browser's tree routinely lists fields in neither order nor nesting. */
    @Test
    fun pairingFollowsTheLayoutRatherThanTheTreeOrder() {
        val verdicts =
            AutofillFormAnalyzer.resolve(
                listOf(
                    field(0, passwordByType = true, at = 1), // password comes first in the tree
                    field(1, at = 0), // but the username is above it on screen
                )
            )

        assertEquals(AutofillFieldType.PASSWORD, verdicts[0])
        assertEquals(AutofillFieldType.USERNAME, verdicts[1])
    }

    @Test
    fun aFieldFarAboveThePasswordIsNotItsUsername() {
        val verdicts =
            AutofillFormAnalyzer.resolve(
                listOf(field(0, at = 0), field(1, passwordByType = true, at = 20))
            )

        assertEquals(AutofillFieldType.UNKNOWN, verdicts[0])
        assertEquals(AutofillFieldType.PASSWORD, verdicts[1])
    }

    @Test
    fun aFieldBesideThePasswordIsNotAboveIt() {
        val password = field(1, passwordByType = true, at = 1)
        val beside = field(0, at = 0).copy(bounds = Bounds(left = 2000, top = 0, right = 2400, bottom = 100))

        val verdicts = AutofillFormAnalyzer.resolve(listOf(beside, password))

        assertEquals(AutofillFieldType.UNKNOWN, verdicts[0])
    }

    /** The reverse pairing: a named username with the password below it. */
    @Test
    fun theFieldBelowAKnownUsernameIsThePassword() {
        val verdicts =
            AutofillFormAnalyzer.resolve(
                listOf(field(0, usernameByWord = true, at = 0), field(1, at = 1))
            )

        assertEquals(AutofillFieldType.USERNAME, verdicts[0])
        assertEquals(AutofillFieldType.PASSWORD, verdicts[1])
    }

    /**
     * The guard on that reverse pairing. Filling a password into a visible field is the one mistake worth being
     * cautious about, so a second candidate below is enough to call the whole thing off.
     */
    @Test
    fun aSecondNamedFieldStopsThePasswordBeingGuessed() {
        val verdicts =
            AutofillFormAnalyzer.resolve(
                listOf(
                    field(0, usernameByWord = true, at = 0),
                    field(1, usernameByWord = true, at = 1),
                    field(2, at = 2),
                )
            )

        assertEquals(AutofillFieldType.UNKNOWN, verdicts[2])
    }

    @Test
    fun aLoneTextFieldIsNotALoginForm() {
        val verdicts = AutofillFormAnalyzer.resolve(listOf(field(0, at = 0)))

        assertEquals(AutofillFieldType.UNKNOWN, verdicts[0])
    }

    /** Unless the user tapped it, which is a request rather than a guess. */
    @Test
    fun aFocusedNamedFieldIsOfferedEvenWithoutAPassword() {
        val verdicts =
            AutofillFormAnalyzer.resolve(listOf(field(0, usernameByWord = true, focused = true, at = 0)))

        assertEquals(AutofillFieldType.USERNAME, verdicts[0])
    }

    @Test
    fun aSearchBoxIsNeverALoginField() {
        val verdicts =
            AutofillFormAnalyzer.resolve(
                listOf(field(0, usernameByWord = true, looksLikeSearch = true, focused = true, at = 0))
            )

        assertEquals(AutofillFieldType.UNKNOWN, verdicts[0])
    }

    @Test
    fun aOneTimeCodeFieldIsLeftAlone() {
        val verdicts =
            AutofillFormAnalyzer.resolve(
                listOf(
                    field(0, at = 0),
                    field(1, passwordByType = true, at = 1),
                    field(2, declared = AutofillFieldType.IGNORE, at = 2),
                )
            )

        assertEquals(AutofillFieldType.IGNORE, verdicts[2])
    }

    @Test
    fun nonEditableViewsAreNeverClassified() {
        val verdicts =
            AutofillFormAnalyzer.resolve(
                listOf(field(0, editable = false, at = 0), field(1, passwordByType = true, at = 1))
            )

        assertEquals(AutofillFieldType.UNKNOWN, verdicts[0])
    }

    /** A structure that reports no geometry still has to work; traversal order is the fallback. */
    @Test
    fun withoutBoundsTheFieldBeforeThePasswordIsUsed() {
        val verdicts =
            AutofillFormAnalyzer.resolve(
                listOf(
                    field(0, at = 0).copy(bounds = null),
                    field(1, passwordByType = true, at = 1).copy(bounds = null),
                )
            )

        assertEquals(AutofillFieldType.USERNAME, verdicts[0])
        assertEquals(AutofillFieldType.PASSWORD, verdicts[1])
    }

    /** [at] is a row index: rows are 100 tall with 40 between them, so consecutive rows are adjacent. */
    private fun field(
        id: Int,
        at: Int,
        editable: Boolean = true,
        focused: Boolean = false,
        declared: AutofillFieldType? = null,
        passwordByType: Boolean = false,
        usernameByWord: Boolean = false,
        looksLikeSearch: Boolean = false,
    ) =
        Candidate(
            id = id,
            editable = editable,
            focused = focused,
            declared = declared,
            passwordByType = passwordByType,
            usernameByType = false,
            passwordByWord = false,
            usernameByWord = usernameByWord,
            looksLikeSearch = looksLikeSearch,
            bounds = Bounds(left = 0, top = at * 140, right = 800, bottom = at * 140 + 100),
        )
}

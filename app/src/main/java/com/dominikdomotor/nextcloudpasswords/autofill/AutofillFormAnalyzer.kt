package com.dominikdomotor.nextcloudpasswords.autofill

/**
 * Decides which fields of one form are the username and the password.
 *
 * Field detection used to be per-node: every view was judged on its own properties and nothing else. That cannot see
 * what a person sees. A login form is two boxes with a relationship - the username is the one *above the password* -
 * and half the forms in the world leave the username box with no hint, no id worth reading and no input type beyond
 * "text". Judged alone it is indistinguishable from a search box.
 *
 * So this looks at the form. It runs in two modes:
 *
 *  - **The form says what its fields are.** If any field carries a real autofill hint, all the hints are trusted and
 *    no guessing happens at all. Mixing the two is what produces a spurious match on a form that was already correct.
 *  - **Nothing says anything.** Then passwords are found by input type, and the username is the field paired with one:
 *    directly above it on screen, or failing that the last editable field before it.
 *
 * Kept free of Android types so it can be unit tested against hand-built forms.
 *
 * The two-mode split, the "field next to the password" pairing, the guard against classifying a lone field, and the
 * search exclusion are all approaches taken by Keepass2Android (https://github.com/PhilippC/keepass2android, GPLv3,
 * Philipp Crocoll). This is an independent implementation of those ideas rather than a port of its code; the geometric
 * pairing below is not theirs.
 */
internal object AutofillFormAnalyzer {
    /**
     * A field's own properties, with nothing said about its neighbours yet.
     *
     * [id] is the field's position in traversal order, which doubles as its key and as the tie-breaker when geometry
     * cannot separate two candidates.
     */
    data class Candidate(
        val id: Int,
        val editable: Boolean,
        val focused: Boolean,
        /** USERNAME or PASSWORD when the field states it outright, IGNORE for a one-time code, null when it says nothing. */
        val declared: AutofillFieldType?,
        val passwordByType: Boolean,
        val usernameByType: Boolean,
        val passwordByWord: Boolean,
        val usernameByWord: Boolean,
        val looksLikeSearch: Boolean,
        val bounds: Bounds?,
    )

    /** A field's rectangle on screen. */
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val height: Int
            get() = bottom - top

        /** Whether two fields share any horizontal extent, which is what makes one "above" the other rather than beside it. */
        fun overlapsHorizontally(other: Bounds): Boolean = left < other.right && other.left < right
    }

    /** The verdict for every candidate, keyed by [Candidate.id]. Fields not mentioned were left UNKNOWN. */
    fun resolve(candidates: List<Candidate>): Map<Int, AutofillFieldType> {
        val verdicts = candidates.associate { it.id to (it.declared ?: AutofillFieldType.UNKNOWN) }.toMutableMap()

        // Mode one: the form declares itself. Trust it completely - a form that names one field usually names them
        // all, and guessing alongside is how a correct form acquires a wrong extra match.
        val declared = candidates.filter { it.declared == AutofillFieldType.USERNAME || it.declared == AutofillFieldType.PASSWORD }
        if (declared.isNotEmpty()) return verdicts

        // Mode two: work it out. Only real inputs are eligible, and a one-time code field never is.
        val fields = candidates.filter { it.editable && it.declared != AutofillFieldType.IGNORE && !it.looksLikeSearch }

        // Input type first: a field the keyboard masks is a password whatever it is called. Words are the fallback,
        // for the sites that style their own masked input.
        val byType = fields.filter { it.passwordByType }.ifEmpty { fields.filter { it.passwordByWord } }
        val named = fields.filter { (it.usernameByWord || it.usernameByType) && it !in byType }

        // Whichever end is known, the other is next to it: the username above the password, or the password below the
        // username. The second direction is deliberately the stricter of the two - see [inferredPasswordBelow].
        val passwords = byType.ifEmpty { listOfNotNull(inferredPasswordBelow(named, fields)) }
        val usernames = named.ifEmpty { passwords.mapNotNull { counterpartOf(it, fields, passwords) }.distinct() }

        // Without a password field, one lone text box is not evidence of a login form - unless the user has actually
        // tapped it, which is a request rather than a guess.
        if (passwords.isEmpty() && usernames.none { it.focused }) return verdicts

        usernames.forEach { verdicts[it.id] = AutofillFieldType.USERNAME }
        passwords.filter { it !in usernames }.forEach { verdicts[it.id] = AutofillFieldType.PASSWORD }
        return verdicts
    }

    /**
     * The field that belongs with this password.
     *
     * Geometry first: the nearest editable field sitting directly above it, sharing some horizontal extent, close
     * enough to be part of the same form. That is how a person reads the pairing, and unlike traversal order it
     * survives a tree whose order has nothing to do with the layout - which is most of them, inside a browser.
     *
     * Traversal order is the fallback for a structure that reports no useful geometry at all.
     */
    private fun counterpartOf(password: Candidate, fields: List<Candidate>, passwords: List<Candidate>): Candidate? {
        val others = fields.filter { it !in passwords }
        val box = password.bounds

        // Where there is geometry, it decides - including when it decides against. Falling through to tree order
        // after a candidate has been rejected for sitting too far away, or beside rather than above, would quietly
        // reinstate the very field the check just ruled out.
        if (box != null && others.any { it.bounds != null }) {
            val above =
                others.filter { it.bounds != null && it.bounds.bottom <= box.top && it.bounds.overlapsHorizontally(box) }
            // Scaled to the field itself rather than a pixel count, so it means the same on any density or zoom. A
            // couple of field heights covers a label and normal spacing; a gap larger than that is another section.
            return above
                .minByOrNull { box.top - it.bounds!!.bottom }
                ?.takeIf { box.top - it.bounds!!.bottom <= box.height * MAX_GAP_IN_FIELD_HEIGHTS }
        }

        // Only for a structure that reports no geometry at all.
        return others.lastOrNull { it.id < password.id }
    }

    /**
     * The password below a username, when nothing declared itself a password.
     *
     * The mirror of [counterpartOf], and held to a stricter standard, because the two mistakes are not equally bad. A
     * wrong username guess types an address into the wrong box. A wrong password guess types a password into a field
     * that is not masked, in front of the user, and possibly submits it somewhere it does not belong.
     *
     * So this only fires when there is exactly one username to reason from, the field below it is adjacent on screen
     * rather than merely later in the tree, and that field claims nothing else about itself. A sign-up form reading
     * "Email" then "Full name" is the case this is guarding against, and geometry alone would not tell the two apart.
     */
    private fun inferredPasswordBelow(named: List<Candidate>, fields: List<Candidate>): Candidate? {
        val username = named.singleOrNull() ?: return null
        val box = username.bounds ?: return null

        val below =
            fields
                .filter { it != username && it.bounds != null && it.bounds.top >= box.bottom }
                .filterNot { it.usernameByWord || it.usernameByType }
                .filter { it.bounds!!.overlapsHorizontally(box) }
        val nearest = below.minByOrNull { it.bounds!!.top - box.bottom } ?: return null
        return nearest.takeIf { it.bounds!!.top - box.bottom <= box.height * MAX_GAP_IN_FIELD_HEIGHTS }
    }

    private const val MAX_GAP_IN_FIELD_HEIGHTS = 3
}

package com.dominikdomotor.nextcloudpasswords.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.autofill.inline.UiVersions
import androidx.core.content.ContextCompat
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.R as AppR
import com.dominikdomotor.nextcloudpasswords.activities.OverviewActivity
import com.dominikdomotor.nextcloudpasswords.autofill.debug.AutofillCapture
import com.dominikdomotor.nextcloudpasswords.autofill.debug.AutofillCaptureStore
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.managers.FaviconStore
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import com.dominikdomotor.nextcloudpasswords.ui.PasswordStatus
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject

@AndroidEntryPoint
class MyAutofillService : AutofillService() {
    @Inject lateinit var storageManager: StorageManager
    @Inject lateinit var faviconStore: FaviconStore

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        if (cancellationSignal.isCanceled) return
        try {
            // This service runs in its own process, so it needs an explicit read of what the main
            // process has written.
            val startedAt = SystemClock.elapsedRealtime()
            storageManager.reloadFromStorage()
            val loadedAt = SystemClock.elapsedRealtime()

            val fillContext = request.fillContexts.lastOrNull()
            val structure = fillContext?.structure
            if (structure == null || structure.activityComponent.packageName == packageName) {
                callback.onSuccess(null)
                return
            }

            val settings = storageManager.settings.value
            val requestingPackage = structure.activityComponent.packageName

            // Recorded before anything can reject the request: what matters is that Chrome asked at all, which is
            // what tells the settings screen the opt-in has been made and the walkthrough is no longer needed.
            if (requestingPackage in CHROME_PACKAGES && !settings.autofillSeenChrome) {
                storageManager.updateSettings { it.autofillSeenChrome = true }
            }
            if (requestingPackage in settings.autofillBlockedApps) {
                GF.println("Autofill: $requestingPackage is blocked by the user")
                callback.onSuccess(null)
                return
            }

            // Per-request, not per-service: fill requests can overlap, and instance fields let one
            // request's field ids leak into another's response.
            val fields =
                ParsedFields.from(
                    structure,
                    AutofillHintWords(settings.autofillUsernameWords, settings.autofillPasswordWords),
                )
            val fallbackId =
                (fillContext.focusedId ?: fields.focusedId)?.takeIf {
                    settings.autofillManualFallback && it in fields.unknownIds
                }
            val fillableIds = (fields.usernameIds + fields.passwordIds + listOfNotNull(fallbackId)).toList()

            // Debug builds keep the structure so the inspector can show why a field was or was not recognised.
            // `enabled` is a compile time constant, so this and everything it reaches is gone from a release build.
            if (AutofillCaptureStore.enabled) {
                AutofillCaptureStore.write(
                    this,
                    AutofillCapture.from(structure, requestingPackage, fields.webDomain) { index ->
                        fields.verdicts[index]?.name
                    },
                )
            }
            GF.println(
                "Autofill: $requestingPackage domain=${fields.webDomain} " +
                    "username=${fields.usernameIds.size} password=${fields.passwordIds.size} " +
                    "unknown=${fields.unknownIds.size} fallback=${fallbackId != null} " +
                    "manualFallback=${settings.autofillManualFallback} " +
                    // The single-field rows target the focused field, so a request that arrives without one
                    // silently falls back to whatever was detected - which is the case worth spotting.
                    "focused=${fillContext.focusedId != null}/${fields.focusedId != null}"
            )
            if (fillableIds.isEmpty() || cancellationSignal.isCanceled) {
                GF.println("Autofill: nothing fillable found, returning no datasets")
                callback.onSuccess(null)
                return
            }

            val applicationName = applicationName(structure)
            val inlineRequest =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && settings.inlineAutofillSuggestions) {
                    request.inlineSuggestionsRequest
                } else {
                    null
                }
            val matches =
                AutofillCredentialMatcher.matchingPasswords(
                        storageManager.passwords.value,
                        fields.webDomain,
                        applicationName,
                    )
                    .take(MAX_CREDENTIAL_DATASETS)

            val response = FillResponse.Builder()
            if (fillableIds.isNotEmpty()) {
                response.setSaveInfo(
                    SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_GENERIC, fillableIds.toTypedArray()).build()
                )
            }
            // Three rows per entry where the form allows it: fill both, fill only the username, fill only the
            // password. A suggestion row has one click target, so a row per choice is the only way each choice can be
            // a single tap - and the single-field rows are what rescues a field this service classified wrongly.
            // One single-field dataset per fillable field, rather than one for whichever field happened to be
            // focused when the request arrived.
            //
            // A response is built once and then reused as the user moves between fields, and the platform shows only
            // the datasets that can fill whatever is focused now. Targeting just the field focused at request time
            // therefore worked on that field and silently dropped both rows on every other one - the user saw three
            // choices in one box and one in the next. Covering each field costs a few more datasets in the response,
            // none of which are ever shown together: the list stays three rows per entry wherever the cursor is.
            var inlineIndex = 0
            matches.forEach { password ->
                val key = password.id.ifBlank { password.label.hashCode().toString() }
                if (fields.usernameIds.isNotEmpty() && fields.passwordIds.isNotEmpty()) {
                    response.addDataset(
                        AutofillDatasetFactory.credentialDataset(
                            password.username,
                            password.password,
                            fields.usernameIds,
                            fields.passwordIds,
                            createPresentation(password.label, password.username, password),
                            createInlinePresentation(password.label, password.username, inlineRequest, inlineIndex++),
                            "credential-" + key,
                        )
                    )
                }
                // Whatever this service decided a field was, the user can put either value in it. That is the point
                // of these two: the case they exist for is a username box read as a password, and a row that obeys
                // the wrong verdict cannot correct it.
                fillableIds.forEachIndexed { field, target ->
                    val putUsername = getString(AppR.string.autofill_fill_username)
                    response.addDataset(
                        AutofillDatasetFactory.credentialDataset(
                            password.username,
                            password.password,
                            listOf(target),
                            emptyList(),
                            createPresentation(password.label, putUsername, password, Badge.USERNAME),
                            createInlinePresentation(password.label, putUsername, inlineRequest, inlineIndex),
                            "username-$key-$field",
                        )
                    )
                    val putPassword = getString(AppR.string.autofill_fill_password)
                    response.addDataset(
                        AutofillDatasetFactory.credentialDataset(
                            password.username,
                            password.password,
                            emptyList(),
                            listOf(target),
                            createPresentation(password.label, putPassword, password, Badge.PASSWORD),
                            createInlinePresentation(password.label, putPassword, inlineRequest, inlineIndex + 1),
                            "password-$key-$field",
                        )
                    )
                }
                // Only one field's pair is ever on screen at once, so the inline slots they occupy are the same two
                // however many fields the form has.
                inlineIndex += 2
            }
            fillableIds.forEach { id ->
                response.addDataset(
                    createSearchDataset(
                        id,
                        fields,
                        inlineRequest,
                        matches.size,
                        AutofillSearchQuery.from(fields.webDomain, applicationName),
                    )
                )
            }
            // A cold fill request has to start this process, build the graph and decrypt the whole stored document
            // before it can answer, and the popup does not appear until it does. Broken down so a slow one can be
            // blamed on the right part rather than guessed at.
            val builtAt = SystemClock.elapsedRealtime()
            GF.println(
                "Autofill: offering ${matches.size} credential(s) + search on ${fillableIds.size} field(s) " +
                    "in ${builtAt - startedAt}ms (decrypt ${loadedAt - startedAt}ms, " +
                    "parse+match+present ${builtAt - loadedAt}ms, favicons ${faviconStore.decodeCount})"
            )
            callback.onSuccess(response.build())
        } catch (e: Exception) {
            GF.println("Autofill: failed - ${e.javaClass.simpleName}: ${e.message}")
            callback.onFailure(getString(AppR.string.autofill_failed))
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    /** The autofillable fields found in one assist structure. */
    private class ParsedFields(
        val usernameIds: Set<AutofillId>,
        val passwordIds: Set<AutofillId>,
        val unknownIds: Set<AutofillId>,
        val webDomain: String?,
        /** Kept so the inspector can show the verdict the service actually reached, rather than a second guess at it. */
        val verdicts: Map<Int, AutofillFieldType> = emptyMap(),
        val autofillIds: Map<Int, AutofillId> = emptyMap(),
        /** The field reporting focus in the structure, which is not always the one the request names. */
        val focusedId: AutofillId? = null,
    ) {
        companion object {
            /**
             * Collects every candidate, then lets [AutofillFormAnalyzer] decide.
             *
             * Two passes rather than one: which field is the username depends on where the password is, so nothing can
             * be decided until the whole tree has been seen. `ViewNode.getLeft` is relative to the parent, so the walk
             * carries the running offset and each candidate ends up with an absolute rectangle - without that, two
             * fields under different parents cannot be compared, which is every field inside a browser.
             */
            fun from(structure: AssistStructure, words: AutofillHintWords): ParsedFields {
                val candidates = mutableListOf<AutofillFormAnalyzer.Candidate>()
                val idsByCandidate = mutableMapOf<Int, AutofillId>()
                var webDomain: String? = null
                var next = 0

                fun traverse(node: ViewNode?, originX: Int, originY: Int) {
                    if (node == null) return
                    val id = next++
                    AutofillFieldAnalyzer.candidate(node, id, words, originX, originY)?.let { candidate ->
                        candidates += candidate
                        node.autofillId?.let { idsByCandidate[id] = it }
                    }
                    node.webDomain?.takeIf(String::isNotBlank)?.let { webDomain = it }
                    val childX = originX + node.left
                    val childY = originY + node.top
                    repeat(node.childCount) { traverse(node.getChildAt(it), childX, childY) }
                }

                repeat(structure.windowNodeCount) { traverse(structure.getWindowNodeAt(it).rootViewNode, 0, 0) }

                val verdicts = AutofillFormAnalyzer.resolve(candidates)
                val usernameIds = linkedSetOf<AutofillId>()
                val passwordIds = linkedSetOf<AutofillId>()
                val unknownIds = linkedSetOf<AutofillId>()
                candidates.forEach { candidate ->
                    val autofillId = idsByCandidate[candidate.id] ?: return@forEach
                    when (verdicts[candidate.id]) {
                        AutofillFieldType.USERNAME -> usernameIds += autofillId
                        AutofillFieldType.PASSWORD -> passwordIds += autofillId
                        AutofillFieldType.UNKNOWN -> unknownIds += autofillId
                        else -> Unit
                    }
                }
                // Which field the user is standing in, taken from the structure rather than from the request.
                // FillContext.getFocusedId is null often enough to matter - Edge is one - and when it is, a row that
                // targets the focused field has nothing to target and the platform silently drops it.
                val focusedId = candidates.firstOrNull { it.focused }?.let { idsByCandidate[it.id] }
                return ParsedFields(usernameIds, passwordIds, unknownIds, webDomain, verdicts, idsByCandidate, focusedId)
            }
        }
    }

    private fun applicationName(structure: AssistStructure): String =
        runCatching {
                val info =
                    packageManager.getApplicationInfo(
                        structure.activityComponent.packageName,
                        PackageManager.GET_META_DATA,
                    )
                packageManager.getApplicationLabel(info).toString()
            }
            .getOrDefault(structure.activityComponent.packageName)

    /** Which corner badge a row wears, and so which of the three choices it is. */
    private enum class Badge(val drawable: Int) {
        USERNAME(AppR.drawable.autofill_presentation_person_24),
        PASSWORD(AppR.drawable.autofill_presentation_key_24),
    }

    private fun createPresentation(
        title: String,
        subtitle: String,
        password: Password? = null,
        badge: Badge? = null,
        @DrawableRes icon: Int? = null,
    ): RemoteViews =
        RemoteViews(packageName, AppR.layout.autofill_dataset_presentation).apply {
            setTextViewText(AppR.id.autofill_presentation_title, title)
            setTextViewText(AppR.id.autofill_presentation_subtitle, subtitle)

            val favicon = password?.id?.let(faviconStore::peek)
            // The key badge carries the entry's security rating, in the colours the password list already uses, so the
            // meaning carries over. The person badge has no rating to show and takes the row's own text colour.
            val badgeColor =
                if (badge == Badge.PASSWORD) {
                    ContextCompat.getColor(
                        this@MyAutofillService,
                        PasswordStatus.from(password?.status ?: -1).colorResId,
                    )
                } else {
                    ContextCompat.getColor(this@MyAutofillService, AppR.color.autofill_presentation_text)
                }

            // The ImageView is untinted and the placeholder drawable tints itself, so a bitmap simply replaces it.
            // Clearing a tint here would need RemoteViews#setColorStateList, which is API 31.
            val composed =
                badge?.let { AutofillPresentationIcon.badged(this@MyAutofillService, favicon, it.drawable, badgeColor) }
            when {
                composed != null -> setImageViewBitmap(AppR.id.autofill_presentation_icon, composed)
                // No favicon to badge: a full size person or key says more than a blank square wearing a small one.
                badge != null -> setImageViewResource(AppR.id.autofill_presentation_icon, badge.drawable)
                favicon != null -> setImageViewBitmap(AppR.id.autofill_presentation_icon, favicon)
                icon != null -> setImageViewResource(AppR.id.autofill_presentation_icon, icon)
            }
        }

    private fun createSearchDataset(
        targetId: AutofillId,
        fields: ParsedFields,
        inlineRequest: InlineSuggestionsRequest?,
        inlineIndex: Int,
        initialQuery: String,
    ) =
        AutofillDatasetFactory.authenticationDataset(
            listOf(targetId),
            createPresentation(
                getString(AppR.string.search_all_passwords),
                getString(AppR.string.app_name),
                icon = AppR.drawable.autofill_presentation_search_24,
            ),
            createInlinePresentation(
                getString(AppR.string.search_all_passwords),
                getString(AppR.string.app_name),
                inlineRequest,
                inlineIndex,
            ),
            PendingIntent.getActivity(
                    this,
                    targetId.hashCode(),
                    Intent(this, AutofillPickerActivity::class.java)
                        .putParcelableArrayListExtra(
                            AutofillPickerActivity.EXTRA_USERNAME_IDS,
                            ArrayList(fields.usernameIds),
                        )
                        .putParcelableArrayListExtra(
                            AutofillPickerActivity.EXTRA_PASSWORD_IDS,
                            ArrayList(fields.passwordIds),
                        )
                        .putExtra(AutofillPickerActivity.EXTRA_FOCUSED_ID, targetId)
                        .putExtra(AutofillPickerActivity.EXTRA_INITIAL_QUERY, initialQuery),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                .intentSender,
        )

    /**
     * Builds the keyboard suggestion chip.
     *
     * `InlineSuggestionUi` hands back an `InlineSuggestionUi.Content`, but the `Slice` inside it — the only thing
     * `InlinePresentation` accepts — is annotated as library-internal. There is no public accessor; the platform's own
     * autofill sample reads it the same way.
     */
    @SuppressLint("RestrictedApi")
    private fun createInlinePresentation(
        title: String,
        subtitle: String,
        request: InlineSuggestionsRequest?,
        index: Int,
    ): InlinePresentation? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || request == null) return null
        if (index >= request.maxSuggestionCount) return null
        val spec =
            request.inlinePresentationSpecs.getOrNull(index)
                ?: request.inlinePresentationSpecs.lastOrNull()
                ?: return null
        if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null

        val attribution =
            PendingIntent.getActivity(
                this,
                1,
                Intent(this, OverviewActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val slice =
            InlineSuggestionUi.newContentBuilder(attribution)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setStartIcon(Icon.createWithResource(this, AppR.drawable.icon_vpn_key_24))
                .setContentDescription("$title, $subtitle")
                .build()
                .slice
        return InlinePresentation(slice, spec, false)
    }

    private companion object {
        /**
         * Entries offered, not rows: each one now produces up to three, so ten entries would be thirty rows in a
         * dropdown over someone's keyboard. Anyone with more matches than this wants the search row anyway.
         */
        const val MAX_CREDENTIAL_DATASETS = 5

        /** Chrome's release channels. Other browsers delegate without an extra opt-in and need no walkthrough. */
        val CHROME_PACKAGES = setOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary")
    }
}

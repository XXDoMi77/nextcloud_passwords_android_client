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
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.R as AppR
import com.dominikdomotor.nextcloudpasswords.activities.OverviewActivity
import com.dominikdomotor.nextcloudpasswords.autofill.debug.AutofillCapture
import com.dominikdomotor.nextcloudpasswords.autofill.debug.AutofillCaptureStore
import com.dominikdomotor.nextcloudpasswords.managers.FaviconStore
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
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
            storageManager.reloadFromStorage()

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
                fillContext.focusedId?.takeIf { settings.autofillManualFallback && it in fields.unknownIds }
            val fillableIds = (fields.usernameIds + fields.passwordIds + listOfNotNull(fallbackId)).toList()

            // Debug builds keep the structure so the inspector can show why a field was or was not recognised.
            // `enabled` is a compile time constant, so this and everything it reaches is gone from a release build.
            if (AutofillCaptureStore.enabled) {
                val words = AutofillHintWords(settings.autofillUsernameWords, settings.autofillPasswordWords)
                AutofillCaptureStore.write(
                    this,
                    AutofillCapture.from(structure, requestingPackage, fields.webDomain) { node ->
                        AutofillFieldAnalyzer.classify(node, words).name
                    },
                )
            }
            GF.println(
                "Autofill: $requestingPackage domain=${fields.webDomain} " +
                    "username=${fields.usernameIds.size} password=${fields.passwordIds.size} " +
                    "unknown=${fields.unknownIds.size} fallback=${fallbackId != null} " +
                    "manualFallback=${settings.autofillManualFallback}"
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
            matches.forEachIndexed { index, password ->
                response.addDataset(
                    AutofillDatasetFactory.credentialDataset(
                        password.username,
                        password.password,
                        fields.usernameIds,
                        fields.passwordIds,
                        createPresentation(password.label, password.username, password.id),
                        createInlinePresentation(password.label, password.username, inlineRequest, index),
                        "credential-${password.id.ifBlank { password.label.hashCode().toString() }}",
                    )
                )
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
            GF.println("Autofill: offering ${matches.size} credential(s) + search on ${fillableIds.size} field(s)")
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
    ) {
        companion object {
            fun from(structure: AssistStructure, words: AutofillHintWords): ParsedFields {
                val usernameIds = linkedSetOf<AutofillId>()
                val passwordIds = linkedSetOf<AutofillId>()
                val unknownIds = linkedSetOf<AutofillId>()
                var webDomain: String? = null

                fun traverse(node: ViewNode?) {
                    if (node == null) return
                    node.autofillId?.let { id ->
                        when (AutofillFieldAnalyzer.classify(node, words)) {
                            AutofillFieldType.USERNAME -> usernameIds += id
                            AutofillFieldType.PASSWORD -> passwordIds += id
                            AutofillFieldType.UNKNOWN -> unknownIds += id
                            AutofillFieldType.IGNORE -> Unit
                        }
                    }
                    node.webDomain?.takeIf(String::isNotBlank)?.let { webDomain = it }
                    repeat(node.childCount) { traverse(node.getChildAt(it)) }
                }

                repeat(structure.windowNodeCount) { traverse(structure.getWindowNodeAt(it).rootViewNode) }
                return ParsedFields(usernameIds, passwordIds, unknownIds, webDomain)
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

    private fun createPresentation(title: String, subtitle: String, passwordId: String? = null): RemoteViews =
        RemoteViews(packageName, AppR.layout.autofill_dataset_presentation).apply {
            setTextViewText(AppR.id.autofill_presentation_title, title)
            setTextViewText(AppR.id.autofill_presentation_subtitle, subtitle)
            // The ImageView is untinted and the placeholder drawable tints itself, so the bitmap
            // simply replaces it. Clearing a tint here would need RemoteViews#setColorStateList,
            // which is API 31.
            passwordId?.let(faviconStore::peek)?.let { setImageViewBitmap(AppR.id.autofill_presentation_icon, it) }
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
            createPresentation(getString(AppR.string.search_all_passwords), getString(AppR.string.app_name)),
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
        const val MAX_CREDENTIAL_DATASETS = 10

        /** Chrome's release channels. Other browsers delegate without an extra opt-in and need no walkthrough. */
        val CHROME_PACKAGES = setOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary")
    }
}

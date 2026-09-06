package com.dominikdomotor.nextcloudpasswords.autofill

import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews

internal object AutofillDatasetFactory {
    fun credentialDataset(
        username: String,
        password: String,
        usernameIds: Collection<AutofillId>,
        passwordIds: Collection<AutofillId>,
        menuPresentation: RemoteViews,
        inlinePresentation: InlinePresentation?,
        datasetId: String? = null,
    ): Dataset {
        val builder = Dataset.Builder()
        datasetId?.let(builder::setId)
        usernameIds.forEach {
            setField(builder, it, AutofillValue.forText(username), menuPresentation, inlinePresentation)
        }
        passwordIds.forEach {
            setField(builder, it, AutofillValue.forText(password), menuPresentation, inlinePresentation)
        }
        return builder.build()
    }

    fun authenticationDataset(
        ids: Collection<AutofillId>,
        menuPresentation: RemoteViews,
        inlinePresentation: InlinePresentation?,
        authentication: android.content.IntentSender,
    ): Dataset {
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Dataset.Builder(presentations(menuPresentation, inlinePresentation))
            } else {
                @Suppress("DEPRECATION")
                Dataset.Builder(menuPresentation).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                        @Suppress("DEPRECATION") setInlinePresentation(inlinePresentation)
                    }
                }
            }
        ids.forEach { id ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.setField(
                    id,
                    Field.Builder().setPresentations(presentations(menuPresentation, inlinePresentation)).build(),
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                @Suppress("DEPRECATION") builder.setValue(id, null, menuPresentation, inlinePresentation)
            } else {
                @Suppress("DEPRECATION") builder.setValue(id, null, menuPresentation)
            }
        }
        return builder.setAuthentication(authentication).build()
    }

    fun singleFieldDataset(id: AutofillId, value: String, menuPresentation: RemoteViews): Dataset =
        Dataset.Builder().apply { setField(this, id, AutofillValue.forText(value), menuPresentation, null) }.build()

    private fun setField(
        builder: Dataset.Builder,
        id: AutofillId,
        value: AutofillValue,
        menuPresentation: RemoteViews,
        inlinePresentation: InlinePresentation?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setField(
                id,
                Field.Builder()
                    .setValue(value)
                    .setPresentations(presentations(menuPresentation, inlinePresentation))
                    .build(),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
            @Suppress("DEPRECATION") builder.setValue(id, value, menuPresentation, inlinePresentation)
        } else {
            @Suppress("DEPRECATION") builder.setValue(id, value, menuPresentation)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun presentations(menuPresentation: RemoteViews, inlinePresentation: InlinePresentation?): Presentations =
        Presentations.Builder()
            .setMenuPresentation(menuPresentation)
            .apply { inlinePresentation?.let(::setInlinePresentation) }
            .build()
}

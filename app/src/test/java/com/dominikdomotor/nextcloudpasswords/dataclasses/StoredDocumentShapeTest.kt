package com.dominikdomotor.nextcloudpasswords.dataclasses

import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.SharesItem
import com.google.gson.annotations.SerializedName
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A tripwire on the shape of the stored document.
 *
 * `StorageManager` decides whether to keep an existing document by comparing one integer -
 * `Data.schemaVersion` against `SCHEMA_VERSION` - and nothing anywhere inspects the classes themselves. That
 * works as long as the number is raised whenever the document stops being readable under the old rules, and
 * the failure mode when it is forgotten is silent: Gson fills what it cannot find with defaults, so a renamed
 * key becomes an empty value rather than an error.
 *
 * So this test does the inspecting. It records every serialised key and its type across the whole document
 * and fails when they change. It is not claiming any particular shape is correct - only that a change was
 * deliberate. When it fails, ask whether a document written by the released version can still be read:
 *
 *  - A key added with a default, or a key removed entirely: still readable. Update the snapshot below, done.
 *  - A key renamed, retyped, or one whose absence would be wrong: not readable. Raise `SCHEMA_VERSION` in
 *    `StorageManager` as well, so the old document is discarded and the user is asked to sign in again.
 */
class StoredDocumentShapeTest {
    @Test
    fun theShapeOfTheStoredDocumentHasNotChangedByAccident() {
        assertEquals(EXPECTED_SHAPE, currentShape())
    }

    /** Every type reachable from the document, so a rename deep in the tree is caught too. */
    private fun currentShape(): String =
        listOf(Data::class, Settings::class, Password::class, Folder::class, SharesItem::class)
            .joinToString("\n\n") { type -> "${type.simpleName}\n${shapeOf(type)}" }

    /**
     * Every serialised key and its declared type, sorted, so field order is not what this is about.
     *
     * The type comes from the Kotlin property rather than the Java field, which means the element type of a
     * collection is part of the snapshot: `List<Password>` narrowing to `List<String>` is precisely the kind
     * of change that leaves Gson reading an old document into the wrong thing, and erasure would hide it.
     *
     * The name still comes off the field, because that is where `@SerializedName` lands - Kotlin applies an
     * untargeted annotation to the first applicable target, and Gson's only applies to fields.
     */
    private fun shapeOf(type: KClass<*>): String =
        type.memberProperties
            .mapNotNull { property ->
                val field = property.javaField ?: return@mapNotNull null
                val key = field.getAnnotation(SerializedName::class.java)?.value ?: property.name
                "  $key: ${property.returnType}"
            }
            .sorted()
            .joinToString("\n")

    private companion object {
        val EXPECTED_SHAPE =
            """
            Data
              folders: kotlin.collections.List<com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder>
              passwords: kotlin.collections.List<com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password>
              schemaVersion: kotlin.Int
              settings: com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
              shares: kotlin.collections.List<com.dominikdomotor.nextcloudpasswords.dataclasses.shares.SharesItem>

            Settings
              accentColorOverride: kotlin.String
              allowScreenshots: kotlin.Boolean
              animateSearchResults: kotlin.Boolean
              autofillBlockedApps: kotlin.collections.List<kotlin.String>
              autofillManualFallback: kotlin.Boolean
              autofillPasswordWords: kotlin.collections.List<kotlin.String>
              autofillUsernameWords: kotlin.collections.List<kotlin.String>
              basicAuth: kotlin.String
              clearClipboard: kotlin.Boolean
              clipboardClearSeconds: kotlin.Int
              e2ePassphrase: kotlin.String
              excludeSimilarCharacters: kotlin.Boolean
              expandBottomSheet: kotlin.Boolean
              includeSymbols: kotlin.Int
              includedSymbolCharacters: kotlin.String
              inlineAutofillSuggestions: kotlin.Boolean
              loggedIn: kotlin.Boolean
              loginInProgress: kotlin.Boolean
              passwordLength: kotlin.Int
              pendingLoginServer: kotlin.String
              server: kotlin.String
              serverThemeColor: kotlin.String
              similarCharacters: kotlin.String
              themeMode: com.dominikdomotor.nextcloudpasswords.dataclasses.ThemeMode
              themeSeedSource: com.dominikdomotor.nextcloudpasswords.dataclasses.ThemeSeedSource
              tintedText: kotlin.Boolean
              token: kotlin.String
              trustedCertificateHost: kotlin.String
              trustedCertificateSha256: kotlin.String
              username: kotlin.String

            Password
              client: kotlin.String
              created: kotlin.Int
              cseKey: kotlin.String
              cseType: kotlin.String
              customFields: kotlin.String
              editable: kotlin.Boolean
              edited: kotlin.Int
              favorite: kotlin.Boolean
              folder: kotlin.String
              hash: kotlin.String
              hidden: kotlin.Boolean
              id: kotlin.String
              label: kotlin.String
              notes: kotlin.String
              password: kotlin.String
              revision: kotlin.String
              share: kotlin.String?
              shared: kotlin.Boolean
              sseType: kotlin.String
              status: kotlin.Int
              statusCode: kotlin.String
              trashed: kotlin.Boolean
              updated: kotlin.Int
              url: kotlin.String
              username: kotlin.String

            Folder
              cseKey: kotlin.String
              cseType: kotlin.String
              id: kotlin.String
              label: kotlin.String
              parent: kotlin.String
              revision: kotlin.String
              trashed: kotlin.Boolean

            SharesItem
              client: kotlin.String
              created: kotlin.Int
              editable: kotlin.Boolean
              expires: kotlin.Any?
              id: kotlin.String
              owner: com.dominikdomotor.nextcloudpasswords.dataclasses.shares.Owner
              password: kotlin.String
              receiver: com.dominikdomotor.nextcloudpasswords.dataclasses.shares.Receiver
              shareable: kotlin.Boolean
              updatePending: kotlin.Boolean
              updated: kotlin.Int
            """
                .trimIndent()
                .trim()
    }
}

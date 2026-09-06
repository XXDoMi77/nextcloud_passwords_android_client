package com.dominikdomotor.nextcloudpasswords.autofill.debug

import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.graphics.Rect
import android.os.Build
import android.text.InputType
import android.view.View
import org.json.JSONArray
import org.json.JSONObject

/**
 * A record of one fill request, as the service saw it.
 *
 * The assist structure a fill request carries is a tree of the screen's views, and the reason a field is or is not
 * recognised lives in properties that are invisible from outside: a resource id, an HTML `autocomplete` attribute, an
 * input type. Nothing in the finished UI shows them, and logging the tree fills the terminal with thousands of lines
 * for one screen. So it is written to a file instead, and the inspector renders it.
 *
 * Debug builds only. [AutofillCaptureStore] is what decides that; this class just describes the shape.
 */
data class AutofillCapture(
    val id: String,
    val takenAt: Long,
    val packageName: String,
    val webDomain: String?,
    val root: CapturedNode,
) {
    /** Every node in the tree that could be filled, which is what a reader is normally looking for. */
    val fields: List<CapturedNode>
        get() = root.flatten().filter { it.autofillId != null }

    fun toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("takenAt", takenAt)
            put("packageName", packageName)
            put("webDomain", webDomain ?: JSONObject.NULL)
            put("root", root.toJson())
        }

    companion object {
        fun fromJson(json: JSONObject): AutofillCapture =
            AutofillCapture(
                id = json.getString("id"),
                takenAt = json.getLong("takenAt"),
                packageName = json.getString("packageName"),
                webDomain = json.optString("webDomain").takeIf { it.isNotBlank() && it != "null" },
                root = CapturedNode.fromJson(json.getJSONObject("root")),
            )

        /**
         * Walks the structure into a tree of plain data.
         *
         * Every window is folded under one synthetic root, because a request can carry several and a reader wants one
         * tree rather than a list of them.
         *
         * [verdictAt] is looked up by traversal index rather than recomputed per node: the real decision is made over
         * the whole form, so a node cannot be re-judged on its own without giving a different answer to the one the
         * service actually used - which would make the inspector lie in exactly the situation it exists for. This walk
         * must therefore count nodes in the same order the service does, which is pre-order over the same windows.
         */
        fun from(
            structure: AssistStructure,
            packageName: String,
            webDomain: String?,
            verdictAt: (Int) -> String?,
        ): AutofillCapture {
            var next = 0
            val windows =
                (0 until structure.windowNodeCount).mapNotNull { index ->
                    structure.getWindowNodeAt(index).rootViewNode?.let {
                        CapturedNode.from(it, verdictAt) { next++ }
                    }
                }
            val root =
                if (windows.size == 1) windows.first()
                else CapturedNode(className = "windows", children = windows)
            return AutofillCapture(
                id = System.currentTimeMillis().toString(36),
                takenAt = System.currentTimeMillis(),
                packageName = packageName,
                webDomain = webDomain,
                root = root,
            )
        }
    }
}

/** One view, flattened to the properties that decide whether it can be filled. */
data class CapturedNode(
    val className: String? = null,
    val autofillId: String? = null,
    val idEntry: String? = null,
    val hint: String? = null,
    val hintIdEntry: String? = null,
    val textIdEntry: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    val autofillHints: List<String> = emptyList(),
    val autofillType: String? = null,
    val inputType: List<String> = emptyList(),
    val htmlTag: String? = null,
    val htmlAttributes: Map<String, String> = emptyMap(),
    val webDomain: String? = null,
    val bounds: List<Int> = emptyList(),
    val focused: Boolean = false,
    val verdict: String? = null,
    val children: List<CapturedNode> = emptyList(),
) {
    /** A one-line summary for a tree row: the class, and whatever names it best. */
    val label: String
        get() {
            val shortClass = className?.substringAfterLast('.') ?: htmlTag ?: "view"
            val name = idEntry ?: hint ?: htmlAttributes["id"] ?: htmlAttributes["name"] ?: contentDescription
            return if (name.isNullOrBlank()) shortClass else "$shortClass  ·  $name"
        }

    fun flatten(): List<CapturedNode> = listOf(this) + children.flatMap { it.flatten() }

    fun toJson(): JSONObject =
        JSONObject().apply {
            putOpt("className", className)
            putOpt("autofillId", autofillId)
            putOpt("idEntry", idEntry)
            putOpt("hint", hint)
            putOpt("hintIdEntry", hintIdEntry)
            putOpt("textIdEntry", textIdEntry)
            putOpt("contentDescription", contentDescription)
            putOpt("text", text)
            if (autofillHints.isNotEmpty()) put("autofillHints", JSONArray(autofillHints))
            putOpt("autofillType", autofillType)
            if (inputType.isNotEmpty()) put("inputType", JSONArray(inputType))
            putOpt("htmlTag", htmlTag)
            if (htmlAttributes.isNotEmpty()) put("htmlAttributes", JSONObject(htmlAttributes.toMap()))
            putOpt("webDomain", webDomain)
            if (bounds.isNotEmpty()) put("bounds", JSONArray(bounds))
            if (focused) put("focused", true)
            putOpt("verdict", verdict)
            if (children.isNotEmpty()) put("children", JSONArray(children.map { it.toJson() }))
        }

    companion object {
        fun fromJson(json: JSONObject): CapturedNode =
            CapturedNode(
                className = json.optStringOrNull("className"),
                autofillId = json.optStringOrNull("autofillId"),
                idEntry = json.optStringOrNull("idEntry"),
                hint = json.optStringOrNull("hint"),
                hintIdEntry = json.optStringOrNull("hintIdEntry"),
                textIdEntry = json.optStringOrNull("textIdEntry"),
                contentDescription = json.optStringOrNull("contentDescription"),
                text = json.optStringOrNull("text"),
                autofillHints = json.optJSONArray("autofillHints").toStringList(),
                autofillType = json.optStringOrNull("autofillType"),
                inputType = json.optJSONArray("inputType").toStringList(),
                htmlTag = json.optStringOrNull("htmlTag"),
                htmlAttributes = json.optJSONObject("htmlAttributes").toStringMap(),
                webDomain = json.optStringOrNull("webDomain"),
                bounds = json.optJSONArray("bounds").toIntList(),
                focused = json.optBoolean("focused", false),
                verdict = json.optStringOrNull("verdict"),
                children = json.optJSONArray("children").toObjectList().map { fromJson(it) },
            )

        fun from(node: ViewNode, verdictAt: (Int) -> String?, nextIndex: () -> Int): CapturedNode {
            val html = node.htmlInfo
            val index = nextIndex()
            return CapturedNode(
                className = node.className,
                autofillId = node.autofillId?.toString(),
                idEntry = node.idEntry,
                hint = node.hint,
                hintIdEntry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) node.hintIdEntry else null,
                textIdEntry = node.textIdEntry,
                contentDescription = node.contentDescription?.toString(),
                // Truncated: a field the user has typed into would otherwise put its contents in a file.
                text = node.text?.toString()?.take(TEXT_LIMIT),
                autofillHints = node.autofillHints?.toList().orEmpty(),
                autofillType = autofillTypeName(node.autofillType),
                inputType = inputTypeNames(node.inputType),
                htmlTag = html?.tag,
                htmlAttributes = html?.attributes?.associate { it.first to it.second }.orEmpty(),
                webDomain = node.webDomain,
                bounds = Rect(node.left, node.top, node.left + node.width, node.top + node.height).let {
                    listOf(it.left, it.top, it.right, it.bottom)
                },
                focused = node.isFocused,
                verdict = node.autofillId?.let { verdictAt(index) },
                children =
                    (0 until node.childCount).mapNotNull { child ->
                        node.getChildAt(child)?.let { from(it, verdictAt, nextIndex) }
                    },
            )
        }

        private const val TEXT_LIMIT = 40

        private fun autofillTypeName(type: Int): String =
            when (type) {
                View.AUTOFILL_TYPE_NONE -> "NONE"
                View.AUTOFILL_TYPE_TEXT -> "TEXT"
                View.AUTOFILL_TYPE_TOGGLE -> "TOGGLE"
                View.AUTOFILL_TYPE_LIST -> "LIST"
                View.AUTOFILL_TYPE_DATE -> "DATE"
                else -> type.toString()
            }

        /** The class and variation bits spelled out, since the raw integer says nothing to a reader. */
        private fun inputTypeNames(inputType: Int): List<String> {
            if (inputType == 0) return emptyList()
            val classes =
                mapOf(
                    InputType.TYPE_CLASS_TEXT to "TEXT",
                    InputType.TYPE_CLASS_NUMBER to "NUMBER",
                    InputType.TYPE_CLASS_PHONE to "PHONE",
                    InputType.TYPE_CLASS_DATETIME to "DATETIME",
                )
            val variations =
                mapOf(
                    InputType.TYPE_TEXT_VARIATION_PASSWORD to "PASSWORD",
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD to "VISIBLE_PASSWORD",
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD to "WEB_PASSWORD",
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS to "EMAIL",
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS to "WEB_EMAIL",
                    InputType.TYPE_TEXT_VARIATION_URI to "URI",
                    InputType.TYPE_TEXT_VARIATION_PERSON_NAME to "PERSON_NAME",
                )
            return buildList {
                classes[inputType and InputType.TYPE_MASK_CLASS]?.let(::add)
                variations[inputType and InputType.TYPE_MASK_VARIATION]?.let(::add)
                if (isEmpty()) add("0x${inputType.toString(16)}")
            }
        }
    }
}

private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name).ifBlank { null }

private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }

private fun JSONArray?.toIntList(): List<Int> = if (this == null) emptyList() else (0 until length()).map { getInt(it) }

private fun JSONArray?.toObjectList(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

private fun JSONObject?.toStringMap(): Map<String, String> =
    if (this == null) emptyMap() else keys().asSequence().associateWith { getString(it) }

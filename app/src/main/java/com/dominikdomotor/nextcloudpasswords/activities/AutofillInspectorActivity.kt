package com.dominikdomotor.nextcloudpasswords.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.autofill.debug.AutofillCapture
import com.dominikdomotor.nextcloudpasswords.autofill.debug.AutofillCaptureStore
import com.dominikdomotor.nextcloudpasswords.autofill.debug.CapturedNode
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog
import com.dominikdomotor.nextcloudpasswords.ui.padBottomForSystemBars
import com.dominikdomotor.nextcloudpasswords.ui.padTopForStatusBar
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * What the autofill service actually saw, for a person to read.
 *
 * A fill request carries a tree of the screen's views, and whether a field is recognised is decided by properties that
 * are invisible from the outside - a resource id, an HTML `autocomplete` attribute, an input type. When autofill does
 * not offer anything there is no error to look at, so this screen shows the tree, every property on every node, and
 * the verdict the analyser reached for each one.
 *
 * Debug builds only; [AutofillCaptureStore.enabled] is a compile time constant and R8 drops the rest.
 */
@AndroidEntryPoint
class AutofillInspectorActivity : BaseActivity() {
    private lateinit var list: RecyclerView
    private lateinit var title: TextView
    private lateinit var empty: TextView
    private lateinit var share: ImageButton

    private var captures: List<AutofillCapture> = emptyList()
    private var opened: AutofillCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autofill_inspector)

        title = findViewById(R.id.inspectorTitle)
        empty = findViewById(R.id.inspectorEmpty)
        share = findViewById(R.id.inspectorShare)
        list = findViewById<RecyclerView>(R.id.inspectorList).apply { layoutManager = LinearLayoutManager(context) }
        findViewById<View>(R.id.inspectorHeader).padTopForStatusBar()
        list.padBottomForSystemBars()

        findViewById<ImageButton>(R.id.inspectorClear).setOnClickListener {
            AppDialog(this)
                .message(R.string.autofill_inspector_clear_confirm)
                .button(R.string.cancel)
                .button(R.string.yes, destructive = true) {
                    AutofillCaptureStore.clear(this)
                    showCaptureList()
                }
                .showCompact()
        }
        share.setOnClickListener { shareOpenedCapture() }

        // Back from a tree returns to the list rather than leaving the screen.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (opened != null) showCaptureList() else finish()
                }
            },
        )

        showCaptureList()
    }

    override fun onResume() {
        super.onResume()
        if (opened == null) showCaptureList()
    }

    private fun showCaptureList() {
        opened = null
        captures = AutofillCaptureStore.readAll(this)
        title.setText(R.string.autofill_inspector)
        share.visibility = View.GONE
        empty.visibility = if (captures.isEmpty()) View.VISIBLE else View.GONE
        list.adapter = CaptureAdapter(captures) { showTree(it) }
    }

    private fun showTree(capture: AutofillCapture) {
        opened = capture
        title.text = capture.webDomain ?: capture.packageName
        share.visibility = View.VISIBLE
        empty.visibility = View.GONE
        list.adapter = NodeAdapter(capture.root) { showProperties(it) }
    }

    /** Every property, as text, because the interesting one differs every time. */
    private fun showProperties(node: CapturedNode) {
        val lines = buildList {
            fun add(name: String, value: Any?) {
                val text = value?.toString().orEmpty()
                if (text.isNotBlank() && text != "[]" && text != "{}") add("$name: $text")
            }
            add("class", node.className)
            add("verdict", node.verdict)
            add("autofillId", node.autofillId)
            add("idEntry", node.idEntry)
            add("hint", node.hint)
            add("hintIdEntry", node.hintIdEntry)
            add("textIdEntry", node.textIdEntry)
            add("contentDescription", node.contentDescription)
            add("text", node.text)
            add("autofillHints", node.autofillHints)
            add("autofillType", node.autofillType)
            add("inputType", node.inputType)
            add("htmlTag", node.htmlTag)
            node.htmlAttributes.forEach { (key, value) -> add("html:$key", value) }
            add("webDomain", node.webDomain)
            add("bounds", node.bounds)
            if (node.focused) add("focused: true")
        }

        AppDialog(this)
            .title(R.string.autofill_inspector_field)
            .message(lines.joinToString("\n").ifBlank { getString(R.string.autofill_inspector_no_properties) })
            .button(R.string.close)
            .show()
    }

    /**
     * Hands the capture to another app as a file.
     *
     * A FileProvider URI rather than the text itself: a tree of any size overflows what an intent extra will carry,
     * and a `.json` file is what a reader wants anyway.
     */
    private fun shareOpenedCapture() {
        val capture = opened ?: return
        runCatching {
                val outbox = File(cacheDir, "shared-captures").apply { mkdirs() }
                val file = File(outbox, "autofill-${capture.packageName}-${capture.id}.json")
                file.writeText(capture.toJson().toString(2))
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        getString(R.string.autofill_inspector_share),
                    )
                )
            }
            .onFailure {
                AppDialog(this).message(it.message ?: it.javaClass.simpleName).button(R.string.close).showCompact()
            }
    }

    private class CaptureAdapter(
        private val items: List<AutofillCapture>,
        private val onClick: (AutofillCapture) -> Unit,
    ) : RecyclerView.Adapter<CaptureAdapter.Holder>() {
        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.captureTitle)
            val subtitle: TextView = view.findViewById(R.id.captureSubtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_autofill_capture, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val capture = items[position]
            val fields = capture.fields
            holder.title.text = capture.webDomain ?: capture.packageName
            holder.subtitle.text =
                buildString {
                    append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(capture.takenAt)))
                    append("  ·  ")
                    append(holder.itemView.context.getString(R.string.autofill_inspector_field_count, fields.size))
                    val recognised = fields.count { it.verdict == "USERNAME" || it.verdict == "PASSWORD" }
                    if (recognised > 0) append("  ·  $recognised recognised")
                }
            holder.itemView.setOnClickListener { onClick(capture) }
        }
    }

    /**
     * The tree, flattened.
     *
     * A RecyclerView wants a list, so the visible rows are recomputed from the expansion state whenever it changes.
     * Nodes that lead to a fillable field start expanded: opening a capture and immediately seeing the fields is the
     * common case, and hunting down through half a dozen layout wrappers to reach them is not.
     */
    private class NodeAdapter(root: CapturedNode, private val onDetails: (CapturedNode) -> Unit) :
        RecyclerView.Adapter<NodeAdapter.Holder>() {
        private data class Row(val node: CapturedNode, val depth: Int)

        private val expanded = hashSetOf<CapturedNode>()
        private var rows: List<Row> = emptyList()

        init {
            expandTowardsFields(root)
            rebuild(root)
        }

        private val root = root

        private fun expandTowardsFields(node: CapturedNode): Boolean {
            val leadsToField = node.children.map { expandTowardsFields(it) }.any { it } || node.autofillId != null
            if (leadsToField && node.children.isNotEmpty()) expanded += node
            return leadsToField
        }

        private fun rebuild(root: CapturedNode) {
            rows = buildList {
                fun walk(node: CapturedNode, depth: Int) {
                    add(Row(node, depth))
                    if (node in expanded) node.children.forEach { walk(it, depth + 1) }
                }
                walk(root, 0)
            }
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val row: View = view.findViewById(R.id.nodeRow)
            val caret: TextView = view.findViewById(R.id.nodeCaret)
            val label: TextView = view.findViewById(R.id.nodeLabel)
            val subtitle: TextView = view.findViewById(R.id.nodeSubtitle)
            val verdict: TextView = view.findViewById(R.id.nodeVerdict)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_autofill_node, parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val (node, depth) = rows[position]
            val context = holder.itemView.context

            holder.row.setPadding(depth * INDENT_DP * context.resources.displayMetrics.density.toInt(), 0, 0, 0)
            holder.caret.text = if (node.children.isEmpty()) "" else if (node in expanded) "▾" else "▸"
            holder.label.text = node.label

            val summary =
                listOfNotNull(
                        node.autofillHints.takeIf { it.isNotEmpty() }?.joinToString(","),
                        node.inputType.takeIf { it.isNotEmpty() }?.joinToString(","),
                        node.htmlAttributes["autocomplete"]?.let { "autocomplete=$it" },
                        node.htmlAttributes["type"]?.let { "type=$it" },
                    )
                    .joinToString("  ")
            holder.subtitle.text = summary
            holder.subtitle.visibility = if (summary.isBlank()) View.GONE else View.VISIBLE

            holder.verdict.text = node.verdict.orEmpty()
            holder.verdict.visibility = if (node.verdict == null) View.GONE else View.VISIBLE
            holder.verdict.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    context,
                    when (node.verdict) {
                        "USERNAME",
                        "PASSWORD" -> R.color.password_status_secure
                        "UNKNOWN" -> R.color.password_status_warning
                        else -> R.color.password_status_insecure
                    },
                )
            )

            holder.itemView.setOnClickListener {
                if (node.children.isEmpty()) {
                    onDetails(node)
                } else {
                    if (node in expanded) expanded -= node else expanded += node
                    rebuild(root)
                    notifyDataSetChanged()
                }
            }
            holder.itemView.setOnLongClickListener {
                onDetails(node)
                true
            }
        }

        private companion object {
            const val INDENT_DP = 14
        }
    }
}

package com.tds.binarystars.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tds.binarystars.R
import com.tds.binarystars.api.NoteType
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin

class ContentEditorActivity : AppCompatActivity() {
    private lateinit var editorContent: EditText
    private lateinit var tvEditorTitle: TextView
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private lateinit var markdownTools: HorizontalScrollView
    private lateinit var previewLabel: TextView
    private lateinit var markdownPreview: TextView

    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button

    private lateinit var btnBold: Button
    private lateinit var btnItalic: Button
    private lateinit var btnUnderline: Button
    private lateinit var btnStrike: Button
    private lateinit var btnH1: Button
    private lateinit var btnH2: Button
    private lateinit var btnH3: Button
    private lateinit var btnH4: Button
    private lateinit var btnH5: Button
    private lateinit var btnH6: Button
    private lateinit var btnList: Button
    private lateinit var btnNumberedList: Button
    private lateinit var btnTaskList: Button
    private lateinit var btnTable: Button
    private lateinit var btnCode: Button
    private lateinit var btnInlineCode: Button
    private lateinit var btnQuote: Button
    private lateinit var btnLink: Button
    private lateinit var btnHorizontalRule: Button

    private var contentType: NoteType = NoteType.Plaintext
    private var history: MutableList<String> = mutableListOf()
    private var historyIndex = -1
    private var isHistoryUpdate = false
    private var markwon: Markwon? = null

    /**
     * Initializes the editor UI and formatting controls.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_content_editor)

        contentType = NoteType.values()[intent.getIntExtra(EXTRA_CONTENT_TYPE, 0)]

        tvEditorTitle = findViewById(R.id.tvEditorTitle)
        editorContent = findViewById(R.id.etEditorContent)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        markdownTools = findViewById(R.id.layoutMarkdownTools)
        previewLabel = findViewById(R.id.tvPreviewLabel)
        markdownPreview = findViewById(R.id.tvMarkdownPreview)

        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)

        btnBold = findViewById(R.id.btnBold)
        btnItalic = findViewById(R.id.btnItalic)
        btnUnderline = findViewById(R.id.btnUnderline)
        btnStrike = findViewById(R.id.btnStrike)
        btnH1 = findViewById(R.id.btnH1)
        btnH2 = findViewById(R.id.btnH2)
        btnH3 = findViewById(R.id.btnH3)
        btnH4 = findViewById(R.id.btnH4)
        btnH5 = findViewById(R.id.btnH5)
        btnH6 = findViewById(R.id.btnH6)
        btnList = findViewById(R.id.btnList)
        btnNumberedList = findViewById(R.id.btnNumberedList)
        btnTaskList = findViewById(R.id.btnTaskList)
        btnTable = findViewById(R.id.btnTable)
        btnCode = findViewById(R.id.btnCode)
        btnInlineCode = findViewById(R.id.btnInlineCode)
        btnQuote = findViewById(R.id.btnQuote)
        btnLink = findViewById(R.id.btnLink)
        btnHorizontalRule = findViewById(R.id.btnHorizontalRule)

        val initialContent = intent.getStringExtra(EXTRA_CONTENT) ?: ""
        editorContent.setText(initialContent)
        editorContent.setSelection(editorContent.text.length)

        setupEditorUi()
        setupHistory(initialContent)
        setupListeners()
    }

    /**
     * Configures the editor layout based on content type.
     */
    private fun setupEditorUi() {
        if (contentType == NoteType.Markdown) {
            tvEditorTitle.text = "Markdown Editor"
            markdownTools.visibility = View.VISIBLE
            previewLabel.visibility = View.VISIBLE
            markdownPreview.visibility = View.VISIBLE
            markwon = Markwon.builder(this)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(this))
                .usePlugin(TaskListPlugin.create(this))
                .usePlugin(HtmlPlugin.create())
                .build()
            renderMarkdownPreview(editorContent.text.toString())
            setMarkdownButtonsVisible(true)
        } else {
            tvEditorTitle.text = "Plaintext Editor"
            markdownTools.visibility = View.VISIBLE
            previewLabel.visibility = View.GONE
            markdownPreview.visibility = View.GONE
            setMarkdownButtonsVisible(false)
        }
    }

    /**
     * Seeds the undo/redo history with initial content.
     */
    private fun setupHistory(initialContent: String) {
        history.clear()
        history.add(initialContent)
        historyIndex = 0
    }

    /**
     * Registers click listeners and text change handlers.
     */
    private fun setupListeners() {
        btnSave.setOnClickListener { finishWithResult() }
        btnCancel.setOnClickListener { finish() }

        btnUndo.setOnClickListener { undo() }
        btnRedo.setOnClickListener { redo() }

        btnBold.setOnClickListener { applySurrounding("**", "**") }
        btnItalic.setOnClickListener { applySurrounding("*", "*") }
        btnUnderline.setOnClickListener { applySurrounding("<u>", "</u>") }
        btnStrike.setOnClickListener { applySurrounding("~~", "~~") }
        btnH1.setOnClickListener { insertHeading(1) }
        btnH2.setOnClickListener { insertHeading(2) }
        btnH3.setOnClickListener { insertHeading(3) }
        btnH4.setOnClickListener { insertHeading(4) }
        btnH5.setOnClickListener { insertHeading(5) }
        btnH6.setOnClickListener { insertHeading(6) }
        btnList.setOnClickListener { insertAtLineStart("- ") }
        btnNumberedList.setOnClickListener { insertAtLineStart("1. ") }
        btnTaskList.setOnClickListener { insertAtLineStart("- [ ] ") }
        btnTable.setOnClickListener { insertTable() }
        btnCode.setOnClickListener { insertCodeBlock() }
        btnInlineCode.setOnClickListener { applySurrounding("`", "`") }
        btnQuote.setOnClickListener { insertAtLineStart("> ") }
        btnLink.setOnClickListener { insertLink() }
        btnHorizontalRule.setOnClickListener { insertHorizontalRule() }

        editorContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // no-op
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // no-op
            }

            override fun afterTextChanged(s: Editable?) {
                if (isHistoryUpdate) return
                val text = s?.toString() ?: ""
                pushHistory(text)

                if (contentType == NoteType.Markdown) {
                    renderMarkdownPreview(text)
                }
            }
        })
    }

    /**
     * Renders markdown preview text.
     */
    private fun renderMarkdownPreview(markdown: String) {
        val renderer = markwon ?: return
        renderer.setMarkdown(markdownPreview, markdown)
    }

    /**
     * Adds a new entry to the history stack.
     */
    private fun pushHistory(text: String) {
        if (historyIndex < history.size - 1) {
            history = history.subList(0, historyIndex + 1).toMutableList()
        }
        history.add(text)
    }

    /**
     * Reverts to the previous history entry.
     */
    private fun undo() {
        if (historyIndex <= 0) return
        historyIndex -= 1
        applyHistoryValue(history[historyIndex])
    }

    /**
     * Advances to the next history entry.
     */
    private fun redo() {
        if (historyIndex >= history.size - 1) return
        historyIndex += 1
        applyHistoryValue(history[historyIndex])
    }

    /**
     * Applies a historical content value to the editor.
     */
    private fun applyHistoryValue(value: String) {
        isHistoryUpdate = true
        editorContent.setText(value)
        editorContent.setSelection(value.length)
        isHistoryUpdate = false
        if (contentType == NoteType.Markdown) {
            renderMarkdownPreview(value)
        }
    }

    /**
     * Wraps the current selection with the provided prefix and suffix.
     */
    private fun applySurrounding(prefix: String, suffix: String) {
        val start = editorContent.selectionStart
        val end = editorContent.selectionEnd
        if (start < 0 || end < 0) return
        val original = editorContent.text.toString()
        val selected = original.substring(start, end)
        val updated = original.replaceRange(start, end, "$prefix$selected$suffix")
        editorContent.setText(updated)
        editorContent.setSelection(start + prefix.length, start + prefix.length + selected.length)
    }

    /**
     * Inserts a prefix at the start of the current line.
     */
    private fun insertAtLineStart(prefix: String) {
        val start = editorContent.selectionStart
        if (start < 0) return
        val text = editorContent.text.toString()
        val lineStart = text.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
        val updated = text.replaceRange(lineStart, lineStart, prefix)
        editorContent.setText(updated)
        editorContent.setSelection(start + prefix.length)
    }

    /**
     * Inserts a markdown heading prefix.
     */
    private fun insertHeading(level: Int) {
        val safeLevel = level.coerceIn(1, 6)
        val prefix = "#".repeat(safeLevel) + " "
        insertAtLineStart(prefix)
    }

    /**
     * Inserts a markdown table template.
     */
    private fun insertTable() {
        val template = "| Column 1 | Column 2 |\n| --- | --- |\n| Value 1 | Value 2 |\n"
        insertTextAtCursor(template)
    }

    /**
     * Inserts a fenced code block template.
     */
    private fun insertCodeBlock() {
        val template = "```\ncode\n```\n"
        insertTextAtCursor(template)
    }

    /**
     * Inserts a markdown link template.
     */
    private fun insertLink() {
        val template = "[text](url)"
        insertTextAtCursor(template)
    }

    /**
     * Inserts a horizontal rule.
     */
    private fun insertHorizontalRule() {
        val template = "\n---\n"
        insertTextAtCursor(template)
    }

    /**
     * Toggles formatting button visibility for markdown mode.
     */
    private fun setMarkdownButtonsVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        btnBold.visibility = visibility
        btnItalic.visibility = visibility
        btnUnderline.visibility = visibility
        btnStrike.visibility = visibility
        btnH1.visibility = visibility
        btnH2.visibility = visibility
        btnH3.visibility = visibility
        btnH4.visibility = visibility
        btnH5.visibility = visibility
        btnH6.visibility = visibility
        btnList.visibility = visibility
        btnNumberedList.visibility = visibility
        btnTaskList.visibility = visibility
        btnTable.visibility = visibility
        btnCode.visibility = visibility
        btnInlineCode.visibility = visibility
        btnQuote.visibility = visibility
        btnLink.visibility = visibility
        btnHorizontalRule.visibility = visibility
    }

    /**
     * Inserts text at the current cursor position.
     */
    private fun insertTextAtCursor(textToInsert: String) {
        val start = editorContent.selectionStart
        if (start < 0) return
        val text = editorContent.text.toString()
        val updated = text.replaceRange(start, start, textToInsert)
        editorContent.setText(updated)
        editorContent.setSelection(start + textToInsert.length)
    }

    /**
     * Returns edited content back to the caller.
     */
    private fun finishWithResult() {
        val result = Intent().apply {
            putExtra(EXTRA_CONTENT, editorContent.text.toString())
        }
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        const val EXTRA_CONTENT_TYPE = "extra_content_type"
        const val EXTRA_CONTENT = "extra_content"
        private const val MAX_HISTORY = 50

        /**
         * Builds an intent to open the editor.
         */
        fun newIntent(context: Context, contentType: NoteType, initialContent: String): Intent {
            return Intent(context, ContentEditorActivity::class.java).apply {
                putExtra(EXTRA_CONTENT_TYPE, contentType.ordinal)
                putExtra(EXTRA_CONTENT, initialContent)
            }
        }
    }
}

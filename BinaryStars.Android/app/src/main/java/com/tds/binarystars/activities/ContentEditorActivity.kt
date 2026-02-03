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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tds.binarystars.R
import com.tds.binarystars.api.NoteType
import io.noties.markwon.Markwon

class ContentEditorActivity : AppCompatActivity() {
    private lateinit var editorContent: EditText
    private lateinit var tvEditorTitle: TextView
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private lateinit var plaintextTools: LinearLayout
    private lateinit var markdownTools: HorizontalScrollView
    private lateinit var previewLabel: TextView
    private lateinit var markdownPreview: TextView

    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button

    private lateinit var btnBold: Button
    private lateinit var btnItalic: Button
    private lateinit var btnH1: Button
    private lateinit var btnH2: Button
    private lateinit var btnList: Button
    private lateinit var btnTable: Button
    private lateinit var btnCode: Button
    private lateinit var btnQuote: Button
    private lateinit var btnLink: Button

    private var contentType: NoteType = NoteType.Plaintext
    private var history: MutableList<String> = mutableListOf()
    private var historyIndex = -1
    private var isHistoryUpdate = false
    private var markwon: Markwon? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_content_editor)

        contentType = NoteType.values()[intent.getIntExtra(EXTRA_CONTENT_TYPE, 0)]

        tvEditorTitle = findViewById(R.id.tvEditorTitle)
        editorContent = findViewById(R.id.etEditorContent)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        plaintextTools = findViewById(R.id.layoutPlaintextTools)
        markdownTools = findViewById(R.id.layoutMarkdownTools)
        previewLabel = findViewById(R.id.tvPreviewLabel)
        markdownPreview = findViewById(R.id.tvMarkdownPreview)

        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)

        btnBold = findViewById(R.id.btnBold)
        btnItalic = findViewById(R.id.btnItalic)
        btnH1 = findViewById(R.id.btnH1)
        btnH2 = findViewById(R.id.btnH2)
        btnList = findViewById(R.id.btnList)
        btnTable = findViewById(R.id.btnTable)
        btnCode = findViewById(R.id.btnCode)
        btnQuote = findViewById(R.id.btnQuote)
        btnLink = findViewById(R.id.btnLink)

        val initialContent = intent.getStringExtra(EXTRA_CONTENT) ?: ""
        editorContent.setText(initialContent)
        editorContent.setSelection(editorContent.text.length)

        setupEditorUi()
        setupHistory(initialContent)
        setupListeners()
    }

    private fun setupEditorUi() {
        if (contentType == NoteType.Markdown) {
            tvEditorTitle.text = "Markdown Editor"
            plaintextTools.visibility = View.VISIBLE
            markdownTools.visibility = View.VISIBLE
            previewLabel.visibility = View.VISIBLE
            markdownPreview.visibility = View.VISIBLE
            markwon = Markwon.create(this)
            renderMarkdownPreview(editorContent.text.toString())
        } else {
            tvEditorTitle.text = "Plaintext Editor"
            plaintextTools.visibility = View.VISIBLE
            markdownTools.visibility = View.GONE
            previewLabel.visibility = View.GONE
            markdownPreview.visibility = View.GONE
        }
    }

    private fun setupHistory(initialContent: String) {
        history.clear()
        history.add(initialContent)
        historyIndex = 0
    }

    private fun setupListeners() {
        btnSave.setOnClickListener { finishWithResult() }
        btnCancel.setOnClickListener { finish() }

        btnUndo.setOnClickListener { undo() }
        btnRedo.setOnClickListener { redo() }

        btnBold.setOnClickListener { applySurrounding("**", "**") }
        btnItalic.setOnClickListener { applySurrounding("*", "*") }
        btnH1.setOnClickListener { insertAtLineStart("# ") }
        btnH2.setOnClickListener { insertAtLineStart("## ") }
        btnList.setOnClickListener { insertAtLineStart("- ") }
        btnTable.setOnClickListener { insertTable() }
        btnCode.setOnClickListener { insertCodeBlock() }
        btnQuote.setOnClickListener { insertAtLineStart("> ") }
        btnLink.setOnClickListener { insertLink() }

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

    private fun renderMarkdownPreview(markdown: String) {
        val renderer = markwon ?: return
        renderer.setMarkdown(markdownPreview, markdown)
    }

    private fun pushHistory(text: String) {
        if (historyIndex < history.size - 1) {
            history = history.subList(0, historyIndex + 1).toMutableList()
        }
        history.add(text)
        if (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
        historyIndex = history.size - 1
    }

    private fun undo() {
        if (historyIndex <= 0) return
        historyIndex -= 1
        applyHistoryValue(history[historyIndex])
    }

    private fun redo() {
        if (historyIndex >= history.size - 1) return
        historyIndex += 1
        applyHistoryValue(history[historyIndex])
    }

    private fun applyHistoryValue(value: String) {
        isHistoryUpdate = true
        editorContent.setText(value)
        editorContent.setSelection(value.length)
        isHistoryUpdate = false
        if (contentType == NoteType.Markdown) {
            renderMarkdownPreview(value)
        }
    }

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

    private fun insertAtLineStart(prefix: String) {
        val start = editorContent.selectionStart
        if (start < 0) return
        val text = editorContent.text.toString()
        val lineStart = text.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
        val updated = text.replaceRange(lineStart, lineStart, prefix)
        editorContent.setText(updated)
        editorContent.setSelection(start + prefix.length)
    }

    private fun insertTable() {
        val template = "| Column 1 | Column 2 |\n| --- | --- |\n| Value 1 | Value 2 |\n"
        insertTextAtCursor(template)
    }

    private fun insertCodeBlock() {
        val template = "```\ncode\n```\n"
        insertTextAtCursor(template)
    }

    private fun insertLink() {
        val template = "[text](url)"
        insertTextAtCursor(template)
    }

    private fun insertTextAtCursor(textToInsert: String) {
        val start = editorContent.selectionStart
        if (start < 0) return
        val text = editorContent.text.toString()
        val updated = text.replaceRange(start, start, textToInsert)
        editorContent.setText(updated)
        editorContent.setSelection(start + textToInsert.length)
    }

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

        fun newIntent(context: Context, contentType: NoteType, initialContent: String): Intent {
            return Intent(context, ContentEditorActivity::class.java).apply {
                putExtra(EXTRA_CONTENT_TYPE, contentType.ordinal)
                putExtra(EXTRA_CONTENT, initialContent)
            }
        }
    }
}

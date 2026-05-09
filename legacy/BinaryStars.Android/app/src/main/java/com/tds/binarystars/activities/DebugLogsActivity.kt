package com.tds.binarystars.activities

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tds.binarystars.R
import com.tds.binarystars.util.NativeLogging

class DebugLogsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_logs)

        val tvLogs = findViewById<TextView>(R.id.tvLogs)
        val btnClear = findViewById<Button>(R.id.btnClearLogs)

        fun refreshLogs() {
            tvLogs.text = NativeLogging.getLogContent()
        }

        btnClear.setOnClickListener {
            NativeLogging.clear()
            refreshLogs()
        }

        refreshLogs()
    }
}

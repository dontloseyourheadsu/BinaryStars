package com.tds.binarystars.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object NativeLogging {
    private const val LOG_FILE_NAME = "binarystars_native.log"
    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        d("BinaryStarsLog", "Logger initialized. Path: ${logFile?.absolutePath}")
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        writeToFile("DEBUG", tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        writeToFile("INFO", tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        writeToFile("WARN", tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        Log.e(tag, msg, tr)
        writeToFile("ERROR", tag, "$msg ${tr?.stackTraceToString() ?: ""}")
    }

    private fun writeToFile(level: String, tag: String, msg: String) {
        val file = logFile ?: return
        try {
            val timestamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "$timestamp $level/$tag: $msg\n"
            FileOutputStream(file, true).use { 
                it.write(line.toByteArray())
            }
        } catch (e: Exception) {
            // Can't log the logger failure easily
        }
    }

    fun getLogContent(): String {
        val file = logFile ?: return "Log not initialized"
        if (!file.exists()) return "Log file empty"
        return try {
            file.readText()
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    fun clear() {
        logFile?.delete()
    }
}

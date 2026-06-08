package com.tds.binarystars.data.repository

object CommandParser {
    fun isDeviceInfoCommand(content: String): Boolean {
        val trimmed = content.trim()
        val tokens = trimmed.split(Regex("\\s+"))
        return tokens.isNotEmpty() && tokens[0] == "!device-info"
    }

    fun isSelfCommand(content: String): Boolean {
        val trimmed = content.trim()
        val tokens = trimmed.split(Regex("\\s+"))
        return tokens.isNotEmpty() && tokens[0] == "!device-info" && tokens.contains("--self")
    }
}

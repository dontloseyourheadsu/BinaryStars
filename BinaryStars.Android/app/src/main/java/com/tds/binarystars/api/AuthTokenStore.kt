package com.tds.binarystars.api

import android.content.Context
import android.content.SharedPreferences

object AuthTokenStore {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_EXPIRES_AT_EPOCH = "expires_at_epoch"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setToken(token: String, expiresInSeconds: Int) {
        val expiresAt = (System.currentTimeMillis() / 1000L) + expiresInSeconds
        prefs?.edit()
            ?.putString(KEY_ACCESS_TOKEN, token)
            ?.putLong(KEY_EXPIRES_AT_EPOCH, expiresAt)
            ?.apply()
    }

    fun getToken(): String? {
        val token = prefs?.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs?.getLong(KEY_EXPIRES_AT_EPOCH, 0L) ?: 0L
        if (token.isNullOrBlank()) return null
        val now = System.currentTimeMillis() / 1000L
        return if (expiresAt > now) token else null
    }

    fun clear() {
        prefs?.edit()
            ?.remove(KEY_ACCESS_TOKEN)
            ?.remove(KEY_EXPIRES_AT_EPOCH)
            ?.apply()
    }
}

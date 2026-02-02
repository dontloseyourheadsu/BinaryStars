package com.tds.binarystars.api

import android.content.Context
import android.content.SharedPreferences

object AuthTokenStore {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setToken(token: String) {
        prefs?.edit()?.putString(KEY_ACCESS_TOKEN, token)?.apply()
    }

    fun getToken(): String? {
        return prefs?.getString(KEY_ACCESS_TOKEN, null)
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_ACCESS_TOKEN)?.apply()
    }
}

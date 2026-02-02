package com.tds.binarystars

import android.app.Application
import com.tds.binarystars.api.AuthTokenStore

class BinaryStarsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthTokenStore.init(this)
    }
}

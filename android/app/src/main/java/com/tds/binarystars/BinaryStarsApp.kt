package com.tds.binarystars

import android.app.Application
import com.tds.binarystars.data.repository.BluetoothRepositoryImpl

class BinaryStarsApp : Application() {

    companion object {
        lateinit var repository: BluetoothRepositoryImpl
            private set
    }

    override fun onCreate() {
        super.onCreate()
        repository = BluetoothRepositoryImpl(this)
    }
}

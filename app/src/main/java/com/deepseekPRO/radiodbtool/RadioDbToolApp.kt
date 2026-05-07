package com.deepseekPRO.radiodbtool

import android.app.Application
import com.deepseekPRO.radiodbtool.data.local.RadioDatabase

class RadioDbToolApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RadioDatabase.getInstance(this)
    }
}
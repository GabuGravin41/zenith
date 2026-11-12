package com.zenith

import android.app.Application
import com.zenith.worker.WorkManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ZenithApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Schedule background tasks synchronously but with robust error handling
        try {
            WorkManagerHelper.schedulePeriodicScan(this)
        } catch (t: Throwable) {
            // Log and prevent crash
            t.printStackTrace()
        }
    }
}

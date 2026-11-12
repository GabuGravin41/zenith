package com.zenith

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.zenith.worker.WorkManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ZenithApp : Application(), ImageLoaderFactory {
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

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}

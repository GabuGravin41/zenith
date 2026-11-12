package com.zenith.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkManagerHelper {
    private const val SMS_SCAN_WORK_NAME = "periodic_sms_scan"

    fun schedulePeriodicScan(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val scanRequest = PeriodicWorkRequestBuilder<SmsScanWorker>(
            1, TimeUnit.HOURS // Run every hour
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SMS_SCAN_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
            scanRequest
        )
    }
}

package com.zenith.worker

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zenith.util.NotificationHelper

/**
 * Fires at the scheduled time to remind the user to publish their post,
 * and opens the share sheet so they can post with one tap.
 */
class ScheduledPostWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val platform = inputData.getString("platform") ?: "Social Media"
            val text = inputData.getString("text") ?: ""

            // Show a notification with the draft text
            NotificationHelper.showScheduledPostReminder(context, platform, text)

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}

package com.zenith.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zenith.R

object NotificationHelper {
    private const val CHANNEL_ID = "zenith_alerts"
    private const val CHANNEL_NAME = "Zenith Alerts"
    private const val SOCIAL_CHANNEL_ID = "zenith_social"
    private const val SOCIAL_CHANNEL_NAME = "Social Post Reminders"

    fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Using default icon for now
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    fun showFinancialAlert(context: Context, amount: Double, type: String) {
        val title = if (type == "CREDIT") "Money Received! 💰" else "New Expense Detected"
        val message = "Ksh ${String.format("%,.0f", amount)} was $type-ed to your account."
        showNotification(context, title, message)
    }

    fun showMediaCleanupTip(context: Context, sizeMb: Int) {
        showNotification(
            context,
            "Storage Alert! 📁",
            "You can free up $sizeMb MB by clearing WhatsApp junk media."
        )
    }

    fun showScheduledPostReminder(context: Context, platform: String, text: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(SOCIAL_CHANNEL_ID, SOCIAL_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        val preview = if (text.length > 60) text.take(60) + "…" else text
        val notification = NotificationCompat.Builder(context, SOCIAL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time to post on $platform! 🚀")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showScanSummary(context: Context, newCount: Int) {
        if (newCount == 0) return
        showNotification(context, "Zenith Scan Complete", "Found $newCount new M-Pesa transactions.")
    }
}

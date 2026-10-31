package com.tom7.openremote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object OpenRemoteNotificationManager {
    private const val CHANNEL_ID_SUCCESS = "session_success"
    private const val CHANNEL_ID_FAILURE = "session_failure"
    private const val CHANNEL_ID_INTERRUPTED = "session_interrupted"

    private const val NOTIFICATION_GROUP = "com.tom7.openremote.SESSION_NOTIFICATIONS"

    fun createNotificationChannels(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return // Notification channels not available before API 26
        }

        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
        if (notificationManager == null) return

        // Success channel - pleasant notification sound
        val successChannel = android.app.NotificationChannel(
            CHANNEL_ID_SUCCESS,
            "Session Completed",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when sessions complete successfully"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 100, 200)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        // Failure channel - more urgent sound
        val failureChannel = android.app.NotificationChannel(
            CHANNEL_ID_FAILURE,
            "Session Failed",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when sessions fail"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 100, 300)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        // Interrupted channel - different sound
        val interruptedChannel = android.app.NotificationChannel(
            CHANNEL_ID_INTERRUPTED,
            "Session Interrupted",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when sessions are interrupted"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 150)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        notificationManager.createNotificationChannels(
            listOf(successChannel, failureChannel, interruptedChannel)
        )
    }

    fun notifySessionCompleted(context: Context, sessionTitle: String, outcome: String?) {
        try {
            val channelId = when (outcome) {
                "failed" -> CHANNEL_ID_FAILURE
                "interrupted" -> CHANNEL_ID_INTERRUPTED
                else -> CHANNEL_ID_SUCCESS
            }

            val title = when (outcome) {
                "failed" -> "Session Failed"
                "interrupted" -> "Session Interrupted"
                else -> "Session Completed"
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(sessionTitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(sessionTitle))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP)
                .build()

            val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            notificationManager?.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            android.util.Log.e("OpenRemoteNotificationManager", "Failed to show notification", e)
        }
    }
}

package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.service.CallRecordingService

/**
 * After a reboot, a foreground service cannot restart itself from the
 * background — so instead of silently staying off, we post a tap-to-resume
 * notification when monitoring was enabled. The tap is a user action, which
 * is one of Android's allowed triggers for starting the microphone-type
 * foreground service.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val CHANNEL_ID = "callpulse_recording_channel"
        private const val RESUME_NOTIFICATION_ID = 9022
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("callpulse_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("monitoring_enabled", false)) {
            Log.d(TAG, "Monitoring was off — nothing to resume after boot.")
            return
        }

        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "CallPulse AI Audio Recording",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { setShowBadge(false) }
                )
            }

            val resumeIntent = Intent(context, CallRecordingService::class.java).apply {
                action = CallRecordingService.ACTION_START_MONITORING
            }
            val resumePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context, 3, resumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    context, 3, resumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("CallPulse AI: tap to resume call monitoring")
                .setContentText("Monitoring was on before the reboot.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(resumePendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            manager.notify(RESUME_NOTIFICATION_ID, notification)
            Log.i(TAG, "Posted tap-to-resume monitoring notification after boot.")
        } catch (e: Exception) {
            Log.e(TAG, "Boot resume notification failed: ${e.message}")
        }
    }
}

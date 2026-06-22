package com.yourcompany.salestrack.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yourcompany.salestrack.LoginActivity
import com.yourcompany.salestrack.supabase.TripsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    private val tripsRepository = TripsRepository()
    private val CHANNEL_ID = "SalesTrackReminders"

    override fun onReceive(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra("request_code", 0)
        val userId = intent.getStringExtra("user_id") ?: return

        createNotificationChannel(context)

        // Perform checks in a Coroutine
        CoroutineScope(Dispatchers.IO).launch {
            val trips = tripsRepository.getTodayTrips(userId)
            
            if (requestCode == 1001) {
                // Morning check (8:30 AM)
                if (trips.isEmpty()) {
                    showNotification(
                        context,
                        "Start Odometer Log Needed",
                        "Don't forget to log your start KM before heading out!",
                        101
                    )
                }
            } else if (requestCode == 1002) {
                // Evening check (6:00 PM)
                if (trips.isNotEmpty() && trips.last().type == "out") {
                    showNotification(
                        context,
                        "Open Trip Reminder",
                        "You have an open trip — please log your return KM.",
                        102
                    )
                }
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SalesTrack Alerts"
            val descriptionText = "Reminders to log odometer readings daily"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, title: String, message: String, notificationId: Int) {
        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            // In Android 13+, check permission before posting
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(notificationId, builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.a8kernrh33.buzzer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class BuzzerMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val name = message.data["name"]?.take(40)?.ifBlank { "Someone" } ?: "Someone"
        val command = message.data["command"]?.take(180)?.trim().orEmpty()
        showAlarmNotification(name, command)
    }

    override fun onNewToken(token: String) {
        getSharedPreferences("buzzer", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
    }

    private fun showAlarmNotification(name: String, command: String) {
        val channelId = "buzzer_alarm"
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(channelId, "Buzzer alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Loud alarms when someone presses your buzzer"
                setSound(alarmSound, attributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 800)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("name", name)
            putExtra("command", command)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val content = if (command.isNotBlank()) command else "$name needs your attention"
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("SUMMONED BY $name")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        manager.notify(9001, notification)
    }
}

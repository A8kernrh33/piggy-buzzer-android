package com.a8kernrh33.buzzer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class BuzzerMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val command = data["command"]?.trim()?.lowercase() ?: "summon"

        when (command) {
            "stop_alarm" -> {
                sendLocalCommand("stop_alarm")
                return
            }
            "vibrate" -> {
                sendLocalCommand("vibrate", data)
                return
            }
            "stop_vibration" -> {
                sendLocalCommand("stop_vibration")
                return
            }
            "notification" -> {
                showCustomNotification(
                    data["title"]?.take(80) ?: "BUZZER 2.0",
                    data["message"]?.take(500) ?: "You have a new summon."
                )
                return
            }
            "wake" -> {
                sendLocalCommand("wake")
                return
            }
        }

        val name = data["name"]?.take(40)?.ifBlank { "Someone" } ?: "Someone"
        val text = data["message"]?.take(300)?.trim()
            ?: data["command_text"]?.take(300)?.trim().orEmpty()
        val duration = data["duration"]?.toLongOrNull()?.coerceIn(1, 300) ?: 60L
        val volume = data["volume"]?.toIntOrNull()?.coerceIn(0, 100) ?: 100
        val pattern = data["vibration_pattern"]?.let { parsePattern(it) }

        showAlarmNotification(name, text, duration, volume, pattern)
    }

    override fun onNewToken(token: String) {
        getSharedPreferences("buzzer", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
    }

    private fun showAlarmNotification(
        name: String,
        message: String,
        duration: Long,
        volume: Int,
        pattern: LongArray?
    ) {
        val channelId = "buzzer_alarm"
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(channelId, "Buzzer alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarms sent by authorized Buzzer controls"
                setSound(alarmSound, attributes)
                enableVibration(true)
                vibrationPattern = pattern ?: longArrayOf(0, 500, 300, 500, 300, 800)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("name", name)
            putExtra("message", message)
            putExtra("duration", duration)
            putExtra("volume", volume)
            putExtra("vibration_pattern", pattern)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val content = if (message.isNotBlank()) message else "$name needs your attention"
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

    private fun showCustomNotification(title: String, message: String) {
        val channelId = "buzzer_control"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Buzzer controls", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(9002, notification)
    }

    private fun sendLocalCommand(command: String, data: Map<String, String> = emptyMap()) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.a8kernrh33.buzzer.REMOTE_COMMAND"
            putExtra("command", command)
            data.forEach { (key, value) -> putExtra(key, value) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun parsePattern(raw: String): LongArray? {
        return raw.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .take(20)
            .map { it.coerceIn(0, 10000) }
            .toLongArray()
            .takeIf { it.isNotEmpty() }
    }
}

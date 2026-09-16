package com.a8kernrh33.buzzer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class BuzzerMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val command = data["command"]?.trim()?.lowercase() ?: "summon"

        when (command) {
            "stop_alarm" -> stopAlarm()
            "vibrate" -> vibrate(data["vibration_pattern"])
            "stop_vibration" -> getVibrator().cancel()
            "notification" -> showCustomNotification(
                data["title"]?.take(80) ?: "BUZZER 2.0",
                data["message"]?.take(500) ?: "You have a new summon."
            )
            "wake" -> wakeScreen()
            else -> {
                val name = data["name"]?.take(40)?.ifBlank { "Someone" } ?: "Someone"
                val text = data["message"]?.take(300)?.trim().orEmpty()
                val duration = data["duration"]?.toLongOrNull()?.coerceIn(1, 300) ?: 60L
                val volume = data["volume"]?.toIntOrNull()?.coerceIn(0, 100) ?: 100
                val pattern = data["vibration_pattern"]?.let { parsePattern(it) }
                showAlarmNotification(name, text, duration, volume, pattern)
            }
        }
    }

    override fun onNewToken(token: String) {
        getSharedPreferences("buzzer", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
    }

    private fun getVibrator(): Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun vibrate(raw: String?) {
        val pattern = raw?.let { parsePattern(it) } ?: longArrayOf(0, 600, 250, 600, 250, 1000)
        val vibrator = getVibrator()
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() {
        getVibrator().cancel()
        getSystemService(NotificationManager::class.java).cancel(9001)
        sendBroadcast(Intent("com.a8kernrh33.buzzer.STOP_ALARM"))
    }

    private fun wakeScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "PiggyBuzzer:RemoteWake"
        )
        wakeLock.acquire(5000L)
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
            this, System.currentTimeMillis().toInt(), intent,
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
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Buzzer controls", NotificationManager.IMPORTANCE_HIGH)
            )
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

    private fun parsePattern(raw: String): LongArray? {
        return raw.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .take(20)
            .map { it.coerceIn(0, 10000) }
            .toLongArray()
            .takeIf { it.isNotEmpty() }
    }
}

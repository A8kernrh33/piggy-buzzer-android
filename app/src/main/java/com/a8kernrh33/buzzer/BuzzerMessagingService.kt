package com.a8kernrh33.buzzer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
            "status" -> Unit
            "stop_alarm" -> stopAlarm()
            "vibrate" -> vibrate(data["vibration_pattern"])
            "stop_vibration" -> getVibrator().cancel()
            "notification" -> showCustomNotification(data["title"]?.take(80) ?: "BUZZER 2.0", data["message"]?.take(500) ?: "You have a new summon.")
            "wake" -> wakeScreen()
            "volume" -> setVolume(data["stream"], data["level"])
            "volume_up" -> adjustVolume(data["stream"], AudioManager.ADJUST_RAISE)
            "volume_down" -> adjustVolume(data["stream"], AudioManager.ADJUST_LOWER)
            "mute" -> setMute(data["stream"], true)
            "unmute" -> setMute(data["stream"], false)
            "media_play_pause" -> mediaKey(KeyEventCodes.PLAY_PAUSE)
            "media_next" -> mediaKey(KeyEventCodes.NEXT)
            "media_previous" -> mediaKey(KeyEventCodes.PREVIOUS)
            "media_stop" -> mediaKey(KeyEventCodes.STOP)
            "open_app" -> openApprovedApp(data["package"])
            "settings" -> openAllowedSettings(data["target"])
            "device_lock" -> lockDevice()
            else -> {
                val name = data["name"]?.take(40)?.ifBlank { "Someone" } ?: "Someone"
                val text = data["message"]?.take(300)?.trim().orEmpty()
                val duration = data["duration"]?.toLongOrNull()?.coerceIn(1, 300) ?: 60L
                val volume = data["volume"]?.toIntOrNull()?.coerceIn(0, 100) ?: 100
                val pattern = data["vibration_pattern"]?.let { parsePattern(it) }
                showAlarmNotification(name, text, duration, volume, pattern)
            }
        }
        val token = getSharedPreferences("buzzer", MODE_PRIVATE).getString("fcm_token", "") ?: ""
        DeviceStatusReporter.reportAsync(this, token)
    }

    override fun onNewToken(token: String) {
        getSharedPreferences("buzzer", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
        DeviceStatusReporter.reportAsync(this, token)
    }

    private fun getVibrator(): Vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator else {
        @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun vibrate(raw: String?) {
        val pattern = raw?.let { parsePattern(it) } ?: longArrayOf(0, 600, 250, 600, 250, 1000)
        val vibrator = getVibrator()
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)) else {
            @Suppress("DEPRECATION") vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() { getVibrator().cancel(); getSystemService(NotificationManager::class.java).cancel(9001); sendBroadcast(Intent("com.a8kernrh33.buzzer.STOP_ALARM")) }

    private fun wakeScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION") val wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "PiggyBuzzer:RemoteWake")
        wakeLock.acquire(5000L)
    }

    private fun setVolume(streamRaw: String?, levelRaw: String?) {
        val audio = getSystemService(AudioManager::class.java)
        val stream = streamFromName(streamRaw)
        val max = audio.getStreamMaxVolume(stream)
        val level = levelRaw?.toIntOrNull()?.coerceIn(0, max) ?: max
        audio.setStreamVolume(stream, level, 0)
    }

    private fun adjustVolume(streamRaw: String?, direction: Int) { getSystemService(AudioManager::class.java).adjustStreamVolume(streamFromName(streamRaw), direction, 0) }

    private fun setMute(streamRaw: String?, mute: Boolean) {
        val audio = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= 23) audio.adjustStreamVolume(streamFromName(streamRaw), if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
    }

    private fun streamFromName(raw: String?): Int = when (raw?.lowercase()) {
        "media" -> AudioManager.STREAM_MUSIC
        "ring" -> AudioManager.STREAM_RING
        "alarm" -> AudioManager.STREAM_ALARM
        "notification" -> AudioManager.STREAM_NOTIFICATION
        "system" -> AudioManager.STREAM_SYSTEM
        "call" -> AudioManager.STREAM_VOICE_CALL
        else -> AudioManager.STREAM_MUSIC
    }

    private fun mediaKey(keyCode: Int) {
        val audio = getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    private fun openApprovedApp(packageName: String?) {
        val approved = setOf("com.google.android.youtube", "com.spotify.music", "com.google.android.apps.maps", "com.discord")
        val pkg = packageName?.trim() ?: return
        if (pkg !in approved) return
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }

    private fun openAllowedSettings(target: String?) {
        val action = when (target?.lowercase()) {
            "wifi" -> android.provider.Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
            "sound" -> android.provider.Settings.ACTION_SOUND_SETTINGS
            "display" -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
            "battery" -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
            "notifications" -> android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
            "accessibility" -> android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            "device_admin" -> android.provider.Settings.ACTION_SECURITY_SETTINGS
            else -> return
        }
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (target?.lowercase() == "notifications") intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(intent)
    }

    private fun lockDevice() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, BuzzerDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) dpm.lockNow()
    }

    private fun showAlarmNotification(name: String, message: String, duration: Long, volume: Int, pattern: LongArray?) {
        val channelId = "buzzer_alarm"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val channel = NotificationChannel(channelId, "Buzzer alarms", NotificationManager.IMPORTANCE_HIGH).apply { description = "Alarms sent by authorized Buzzer controls"; setSound(alarmSound, attributes); enableVibration(true); vibrationPattern = pattern ?: longArrayOf(0, 500, 300, 500, 300, 800); lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC }
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(this, AlarmActivity::class.java).apply { putExtra("name", name); putExtra("message", message); putExtra("duration", duration); putExtra("volume", volume); putExtra("vibration_pattern", pattern); flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingIntent = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val content = if (message.isNotBlank()) message else "$name needs your attention"
        val notification = NotificationCompat.Builder(this, channelId).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("SUMMONED BY $name").setContentText(content).setStyle(NotificationCompat.BigTextStyle().bigText(content)).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setAutoCancel(false).setOngoing(true).setFullScreenIntent(pendingIntent, true).build()
        manager.notify(9001, notification)
    }

    private fun showCustomNotification(title: String, message: String) {
        val channelId = "buzzer_control"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel(channelId, "Buzzer controls", NotificationManager.IMPORTANCE_HIGH))
        val notification = NotificationCompat.Builder(this, channelId).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        manager.notify(9002, notification)
    }

    private fun parsePattern(raw: String): LongArray? = raw.split(",").mapNotNull { it.trim().toLongOrNull() }.take(20).map { it.coerceIn(0, 10000) }.toLongArray().takeIf { it.isNotEmpty() }

    private object KeyEventCodes { const val PLAY_PAUSE = android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; const val NEXT = android.view.KeyEvent.KEYCODE_MEDIA_NEXT; const val PREVIOUS = android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS; const val STOP = android.view.KeyEvent.KEYCODE_MEDIA_STOP }
}

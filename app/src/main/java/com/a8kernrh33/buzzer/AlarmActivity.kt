package com.a8kernrh33.buzzer

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class AlarmActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.a8kernrh33.buzzer.STOP_ALARM") stopAlarm()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_alarm)

        val name = intent.getStringExtra("name") ?: "Someone"
        val message = intent.getStringExtra("message").orEmpty()
        findViewById<TextView>(R.id.nameView).text = name
        findViewById<TextView>(R.id.messageView).text =
            if (message.isNotBlank()) "\u201c$message\u201d" else "needs your attention"
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopAlarm() }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stopReceiver, IntentFilter("com.a8kernrh33.buzzer.STOP_ALARM"), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stopReceiver, IntentFilter("com.a8kernrh33.buzzer.STOP_ALARM"))
        }
        startAlarm()
    }

    private fun startAlarm() {
        val volumePercent = intent.getIntExtra("volume", 100).coerceIn(0, 100)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val requestedVolume = (maxVolume * volumePercent / 100f).toInt().coerceAtLeast(1)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, requestedVolume, 0)

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AlarmActivity, uri)
            isLooping = true
            prepare()
            start()
        }

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = intent.getLongArrayExtra("vibration_pattern")
            ?: longArrayOf(0, 600, 250, 600, 250, 1000)
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        val duration = intent.getLongExtra("duration", 60L).coerceIn(1, 300)
        window.decorView.postDelayed({
            if (!isFinishing) stopAlarm()
        }, duration * 1000L)
    }

    private fun stopAlarm() {
        player?.stop()
        player?.release()
        player = null
        vibrator?.cancel()
        getSystemService(NotificationManager::class.java).cancel(9001)
        if (!isFinishing) finishAndRemoveTask()
    }

    override fun onDestroy() {
        try { unregisterReceiver(stopReceiver) } catch (_: Exception) { }
        player?.release()
        vibrator?.cancel()
        super.onDestroy()
    }
}

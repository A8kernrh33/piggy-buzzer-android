package com.a8kernrh33.buzzer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity

class AlarmActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_alarm)

        val name = intent.getStringExtra("name") ?: "Someone"
        findViewById<TextView>(R.id.nameView).text = name
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopAlarm() }

        startAlarm()
    }

    private fun startAlarm() {
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

        vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(VibratorManager::class.java)
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 600, 250, 600, 250, 1000)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() {
        player?.stop()
        player?.release()
        player = null
        vibrator?.cancel()
        getSystemService(NotificationManager::class.java).cancel(9001)
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        player?.release()
        vibrator?.cancel()
        super.onDestroy()
    }
}

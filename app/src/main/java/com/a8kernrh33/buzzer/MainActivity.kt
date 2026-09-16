package com.a8kernrh33.buzzer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    private lateinit var tokenView: TextView
    private lateinit var batteryView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenView = findViewById(R.id.tokenView)
        batteryView = findViewById(R.id.batteryView)

        findViewById<Button>(R.id.copyButton).setOnClickListener { copyToken() }
        findViewById<Button>(R.id.testButton).setOnClickListener {
            startActivity(Intent(this, AlarmActivity::class.java).putExtra("name", "Test Buzz"))
        }

        updateBattery()
        loadToken()
    }

    override fun onResume() {
        super.onResume()
        updateBattery()
    }

    private fun updateBattery() {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) {
            batteryView.text = "🔋 Battery: unavailable"
            return
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val charging = plugged != 0
        val chargingText = if (charging) " · Charging" else ""
        batteryView.text = if (percent >= 0) {
            "🔋 Battery: $percent%$chargingText"
        } else {
            "🔋 Battery: unavailable"
        }
    }

    private fun loadToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                tokenView.text = task.result
            } else {
                tokenView.text = "Could not get token yet. Reopen the app to retry."
            }
        }
    }

    private fun copyToken() {
        val token = tokenView.text.toString()
        if (token.isBlank() || token.startsWith("Could not")) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FCM token", token))
    }
}

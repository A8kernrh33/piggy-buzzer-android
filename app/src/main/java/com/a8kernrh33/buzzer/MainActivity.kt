package com.a8kernrh33.buzzer

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    private lateinit var tokenView: TextView
    private lateinit var batteryView: TextView
    private lateinit var adminView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenView = findViewById(R.id.tokenView)
        batteryView = findViewById(R.id.batteryView)
        adminView = findViewById(R.id.adminView)

        findViewById<Button>(R.id.copyButton).setOnClickListener { copyToken() }
        findViewById<Button>(R.id.testButton).setOnClickListener {
            startActivity(Intent(this, AlarmActivity::class.java).putExtra("name", "Test Buzz"))
        }
        findViewById<Button>(R.id.deviceAdminButton).setOnClickListener { requestDeviceAdmin() }
        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.notificationSettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }

        updateBattery()
        updateCapabilityStatus()
        loadToken()
    }

    override fun onResume() {
        super.onResume()
        updateBattery()
        updateCapabilityStatus()
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
        val chargingText = if (plugged != 0) " · Charging" else ""
        batteryView.text = if (percent >= 0) "🔋 Battery: $percent%$chargingText" else "🔋 Battery: unavailable"
    }

    private fun updateCapabilityStatus() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, BuzzerDeviceAdminReceiver::class.java)
        val adminState = if (dpm.isAdminActive(admin)) "ACTIVE" else "NOT ENABLED"
        adminView.text = "🔐 Device admin: $adminState"
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@MainActivity, BuzzerDeviceAdminReceiver::class.java))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enables the optional Buzzer device-lock control. You choose whether to activate it.")
        }
        startActivity(intent)
    }

    private fun loadToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            tokenView.text = if (task.isSuccessful) task.result else "Could not get token yet. Reopen the app to retry."
        }
    }

    private fun copyToken() {
        val token = tokenView.text.toString()
        if (token.isBlank() || token.startsWith("Could not")) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FCM token", token))
    }
}

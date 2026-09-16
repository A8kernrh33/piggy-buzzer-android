package com.a8kernrh33.buzzer

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
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
    private lateinit var telemetryView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenView = findViewById(R.id.tokenView)
        batteryView = findViewById(R.id.batteryView)
        adminView = findViewById(R.id.adminView)
        telemetryView = findViewById(R.id.telemetryView)

        findViewById<Button>(R.id.setupButton).setOnClickListener { openQuickSetup() }
        findViewById<Button>(R.id.copyButton).setOnClickListener { copyToken() }
        findViewById<Button>(R.id.testButton).setOnClickListener {
            startActivity(Intent(this, AlarmActivity::class.java).putExtra("name", "Test Buzz"))
        }
        findViewById<Button>(R.id.vibrateTestButton).setOnClickListener { testVibration() }
        findViewById<Button>(R.id.deviceAdminButton).setOnClickListener { requestDeviceAdmin() }
        findViewById<Button>(R.id.accessibilityButton).setOnClickListener { openAccessibility() }
        findViewById<Button>(R.id.notificationSettingsButton).setOnClickListener { openNotificationSettings() }

        updateBattery()
        updateCapabilityStatus()
        updateSetupStatus()
        loadToken()
    }

    override fun onResume() {
        super.onResume()
        updateBattery()
        updateCapabilityStatus()
        updateSetupStatus()
        val token = getSharedPreferences("buzzer", MODE_PRIVATE).getString("fcm_token", "") ?: ""
        DeviceStatusReporter.reportAsync(this, token)
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

    private fun updateSetupStatus() {
        val notificationOk = if (android.os.Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, BuzzerDeviceAdminReceiver::class.java)
        val adminOk = dpm.isAdminActive(admin)
        val ok = notificationOk && adminOk
        telemetryView.text = if (ok) "✅ Quick setup: core permissions enabled" else "⚠️ Quick setup: some optional permissions/settings still need attention"
    }

    private fun openQuickSetup() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 100)
            return
        }
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, BuzzerDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            requestDeviceAdmin()
            return
        }
        openNotificationSettings()
    }

    private fun testVibration() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        val pattern = longArrayOf(0, 500, 200, 500, 200, 800)
        if (android.os.Build.VERSION.SDK_INT >= 26) vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
        else {
            @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
        }
        telemetryView.text = "📳 Local vibration test sent — if you feel nothing, check the phone's vibration settings."
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@MainActivity, BuzzerDeviceAdminReceiver::class.java))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enables the optional Buzzer device-lock control. You choose whether to activate it.")
        }
        startActivity(intent)
    }

    private fun openAccessibility() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun loadToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                tokenView.text = token
                getSharedPreferences("buzzer", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
                DeviceStatusReporter.reportAsync(this, token)
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

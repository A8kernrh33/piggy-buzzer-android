package com.a8kernrh33.buzzer

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DeviceStatusReporter {
    private const val SERVER_URL = "https://buzzer-h559.onrender.com"

    fun reportAsync(context: Context, deviceToken: String) {
        if (deviceToken.isBlank()) return
        Thread {
            try {
                val app = context.applicationContext
                val batteryIntent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val battery = if (level >= 0 && scale > 0) (level * 100) / scale else -1
                val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                val healthCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                    ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
                val health = when (healthCode) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
                    else -> "unknown"
                }
                val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
                val temperature = if (rawTemp != Int.MIN_VALUE) rawTemp / 10.0 else JSONObject.NULL
                val dpm = app.getSystemService(DevicePolicyManager::class.java)
                val admin = ComponentName(app, BuzzerDeviceAdminReceiver::class.java)
                val notifications = if (Build.VERSION.SDK_INT >= 24) {
                    app.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
                } else true
                val version = try {
                    val info = app.packageManager.getPackageInfo(app.packageName, 0)
                    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString() else info.versionCode.toString()
                } catch (_: Exception) { "unknown" }

                val status = JSONObject().apply {
                    put("battery", if (battery >= 0) battery else JSONObject.NULL)
                    put("charging", plugged != 0)
                    put("batteryHealth", health)
                    put("batteryTemperatureC", temperature)
                    put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL)
                    put("androidVersion", Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString())
                    put("appVersion", version)
                    put("deviceAdmin", dpm.isAdminActive(admin))
                    put("notificationsEnabled", notifications)
                    put("uptimeSeconds", SystemClock.elapsedRealtime() / 1000L)
                }

                val body = JSONObject().apply {
                    put("deviceToken", deviceToken)
                    put("status", status)
                }.toString()

                val connection = (URL("$SERVER_URL/api/device/status").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                connection.inputStream.close()
                connection.disconnect()
            } catch (_: Exception) {
                // Telemetry is best-effort; it must never interfere with alarms or commands.
            }
        }.start()
    }
}

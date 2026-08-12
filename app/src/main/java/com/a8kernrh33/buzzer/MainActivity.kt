package com.a8kernrh33.buzzer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    private lateinit var tokenView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenView = findViewById(R.id.tokenView)
        findViewById<Button>(R.id.copyButton).setOnClickListener { copyToken() }
        findViewById<Button>(R.id.testButton).setOnClickListener {
            startActivity(Intent(this, AlarmActivity::class.java).putExtra("name", "Test Buzz"))
        }

        requestNotificationPermission()
        loadToken()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun loadToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                tokenView.text = task.result
            } else {
                tokenView.text = "Could not get token yet. Tap refresh by reopening the app."
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

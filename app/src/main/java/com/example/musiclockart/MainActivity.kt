package com.example.musiclockart

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.musiclockart.databinding.ActivityMainBinding

/**
 * Setup screen. Walks the user through the three things the app needs:
 *   1. Notification access (to read MediaSession data) — the big one.
 *   2. Bluetooth permission (to show the connected device).
 *   3. Notification posting permission (Android 13+).
 * Plus a button to preview the lock overlay.
 */
class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnNotificationAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.btnPermissions.setOnClickListener {
            requestPerms.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            )
        }

        binding.btnPreview.setOnClickListener {
            startActivity(Intent(this, LockOverlayActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val notifAccess = isNotificationAccessGranted()
        val btGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        binding.statusNotification.text = getString(
            R.string.status_notification,
            if (notifAccess) "✓" else "✗"
        )
        binding.statusBluetooth.text = getString(
            R.string.status_bluetooth,
            if (btGranted) "✓" else "✗"
        )
        binding.btnPreview.isEnabled = notifAccess
    }

    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val component = ComponentName(this, MediaNotificationListener::class.java)
        return flat.split(":").any {
            ComponentName.unflattenFromString(it) == component
        }
    }
}

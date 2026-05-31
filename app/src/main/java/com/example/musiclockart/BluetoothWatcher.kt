package com.example.musiclockart

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Watches for the connected A2DP (audio) Bluetooth device and pushes its name
 * into NowPlaying so the overlay can show "Playing on <device>".
 */
class BluetoothWatcher(private val context: Context) {

    private var a2dp: BluetoothA2dp? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.A2DP) {
                a2dp = proxy as BluetoothA2dp
                refresh()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) a2dp = null
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> refresh()
            }
        }
    }

    fun start() {
        if (!hasPermission()) return
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter
        adapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_EXPORTED
        )
        refresh()
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        a2dp?.let {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.closeProfileProxy(BluetoothProfile.A2DP, it)
        }
    }

    @SuppressLint("MissingPermission")
    private fun refresh() {
        if (!hasPermission()) {
            NowPlaying.setBluetoothDevice(null)
            return
        }
        val connected: List<BluetoothDevice> = a2dp?.connectedDevices ?: emptyList()
        NowPlaying.setBluetoothDevice(connected.firstOrNull()?.name)
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
}

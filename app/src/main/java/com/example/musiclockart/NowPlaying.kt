package com.example.musiclockart

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared, app-wide state for whatever is currently playing.
 * The MediaNotificationListener writes to it; the overlay activity reads from it.
 */
data class TrackInfo(
    val title: String = "",
    val artist: String = "",
    val art: Bitmap? = null,
    val isPlaying: Boolean = false,
    val packageName: String = "",
    val clockColor: Int = android.graphics.Color.WHITE,
    val artLuminance: Float = 0.5f
)

object NowPlaying {
    private val _track = MutableStateFlow(TrackInfo())
    val track: StateFlow<TrackInfo> = _track.asStateFlow()

    private val _bluetoothDevice = MutableStateFlow<String?>(null)
    val bluetoothDevice: StateFlow<String?> = _bluetoothDevice.asStateFlow()

    fun update(info: TrackInfo) {
        _track.value = info
    }

    fun setBluetoothDevice(name: String?) {
        _bluetoothDevice.value = name
    }
}

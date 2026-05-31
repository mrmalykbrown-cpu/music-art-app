package com.example.musiclockart

import android.app.WallpaperManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The core data pipeline. As a NotificationListenerService it is allowed to call
 * MediaSessionManager.getActiveSessions(), which is how we read the current track
 * + album art from Spotify / Apple Music / YT Music without their SDKs.
 *
 * On every metadata or playback change we:
 *   1. update the NowPlaying state (drives the overlay UI), and
 *   2. push a blurred version of the art to the lock screen wallpaper.
 */
class MediaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sessionManager: MediaSessionManager? = null
    private var bluetoothWatcher: BluetoothWatcher? = null
    private var activeController: MediaController? = null
    private var lastArtHash: Int = 0

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindToTopController(controllers ?: emptyList())
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            handleUpdate()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            handleUpdate()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
        sessionManager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, MediaNotificationListener::class.java)
        try {
            sessionManager?.addOnActiveSessionsChangedListener(sessionsListener, component)
            bindToTopController(sessionManager?.getActiveSessions(component) ?: emptyList())
        } catch (e: SecurityException) {
            Log.e(TAG, "No notification access yet", e)
        }
        bluetoothWatcher = BluetoothWatcher(this).also { it.start() }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        activeController?.unregisterCallback(controllerCallback)
        bluetoothWatcher?.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { /* sessions listener handles it */ }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* no-op */ }

    private fun bindToTopController(controllers: List<MediaController>) {
        val top = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()

        if (top?.sessionToken == activeController?.sessionToken) {
            handleUpdate()
            return
        }

        activeController?.unregisterCallback(controllerCallback)
        activeController = top
        activeController?.registerCallback(controllerCallback)
        handleUpdate()
    }

    private fun handleUpdate() {
        val controller = activeController
        if (controller == null) {
            NowPlaying.update(TrackInfo())
            return
        }
        val metadata = controller.metadata
        val playback = controller.playbackState
        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()

        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        NowPlaying.update(
            TrackInfo(
                title = title,
                artist = artist,
                art = art,
                isPlaying = isPlaying,
                packageName = controller.packageName
            )
        )

        if (art != null) {
            val hash = art.generationId
            if (hash != lastArtHash) {
                lastArtHash = hash
                pushWallpaper(art)
            }
        }
    }

    private fun pushWallpaper(art: Bitmap) {
        scope.launch {
            try {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                (getSystemService(WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay.getRealMetrics(metrics)

                val backdrop = ImageEffects.makeWallpaperBackdrop(
                    art, metrics.widthPixels, metrics.heightPixels
                )
                val wm = WallpaperManager.getInstance(this@MediaNotificationListener)
                wm.setBitmap(backdrop, null, true, WallpaperManager.FLAG_LOCK)
                Log.d(TAG, "Lock wallpaper updated")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set wallpaper", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "MusicLockArt"
    }
}

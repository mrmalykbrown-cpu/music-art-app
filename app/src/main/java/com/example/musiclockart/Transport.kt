package com.example.musiclockart

import android.media.session.MediaController

/**
 * Lets the overlay's play/pause/next/prev buttons drive whatever player is active.
 * The MediaNotificationListener sets the current controller; the overlay calls the
 * transport methods. Held as a weak-ish app-global since both live in the same process.
 */
object Transport {
    @Volatile
    private var controller: MediaController? = null

    fun setController(c: MediaController?) {
        controller = c
    }

    fun playPause() {
        val c = controller ?: return
        val playing = c.playbackState?.state ==
            android.media.session.PlaybackState.STATE_PLAYING
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        // SHUFFLE_MODE_NONE = 0, SHUFFLE_MODE_ALL = 1 (PlaybackState constants
        // aren't exposed on android.media.session.PlaybackState, so use literals).
        val mode = c.shuffleMode
        c.transportControls.setShuffleMode(if (mode == 1) 0 else 1)
    }
}

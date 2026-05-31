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
        // Shuffle is intentionally not wired to the framework API: getShuffleMode/
        // setShuffleMode behave inconsistently across players and platform versions,
        // and most apps (Spotify, Apple Music) only expose shuffle via their own
        // custom session actions, not the standard transport. Left as a no-op so the
        // button is decorative without risking unsupported-API crashes.
    }
}

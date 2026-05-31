package com.example.musiclockart

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.musiclockart.databinding.ActivityLockOverlayBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Drawn over the lock screen (showOnLockScreen=true). Shows the Apple-Music style
 * card: a Ken-Burns animated, RenderEffect-blurred backdrop of the album art,
 * crisp foreground art, title/artist, and a Bluetooth "Playing on…" chip.
 */
class LockOverlayActivity : ComponentActivity() {

    private lateinit var binding: ActivityLockOverlayBinding
    private var kenBurns: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Live GPU blur on the backdrop (API 31+).
        binding.backdrop.setRenderEffect(
            RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP)
        )

        binding.dismiss.setOnClickListener { finish() }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(NowPlaying.track, NowPlaying.bluetoothDevice) { track, bt ->
                    track to bt
                }.collect { (track, bt) ->
                    render(track, bt)
                }
            }
        }
    }

    private fun render(track: TrackInfo, bluetooth: String?) {
        binding.title.text = track.title.ifBlank { getString(R.string.nothing_playing) }
        binding.artist.text = track.artist

        track.art?.let {
            binding.backdrop.setImageBitmap(it)
            binding.albumArt.setImageBitmap(it)
            startKenBurns()
        }

        if (bluetooth.isNullOrBlank()) {
            binding.bluetoothChip.visibility = android.view.View.GONE
        } else {
            binding.bluetoothChip.visibility = android.view.View.VISIBLE
            binding.bluetoothChip.text = getString(R.string.playing_on, bluetooth)
        }
    }

    private fun startKenBurns() {
        if (kenBurns?.isRunning == true) return
        binding.backdrop.scaleX = 1.1f
        binding.backdrop.scaleY = 1.1f
        kenBurns = ObjectAnimator.ofPropertyValuesHolder(
            binding.backdrop,
            PropertyValuesHolder.ofFloat("scaleX", 1.1f, 1.3f),
            PropertyValuesHolder.ofFloat("scaleY", 1.1f, 1.3f),
            PropertyValuesHolder.ofFloat("translationX", 0f, -40f),
            PropertyValuesHolder.ofFloat("translationY", 0f, -30f)
        ).apply {
            duration = 20000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        kenBurns?.cancel()
    }
}

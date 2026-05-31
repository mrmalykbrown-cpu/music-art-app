package com.example.musiclockart

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen, art-themed overlay drawn over the lock screen.
 * Top: adaptive-color clock + date. Bottom: player-controls card.
 * No album-art square. Brightness + blur driven by Prefs.
 */
class LockOverlayActivity : ComponentActivity() {

    private lateinit var binding: ActivityLockOverlayBinding
    private lateinit var prefs: Prefs
    private var kenBurns: ObjectAnimator? = null

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyBlur()
        applyBrightness(null)

        binding.dismiss.setOnClickListener { finish() }
        binding.playPause.setOnClickListener { Transport.playPause() }
        binding.next.setOnClickListener { Transport.next() }
        binding.prev.setOnClickListener { Transport.previous() }

        observeState()
    }

    override fun onResume() {
        super.onResume()
        updateClock()
        clockHandler.postDelayed(clockTick, 10_000)
        applyBlur()
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
    }

    private fun applyBlur() {
        val r = prefs.blurRadius.coerceAtLeast(1).toFloat()
        binding.backdrop.setRenderEffect(
            RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
        )
    }

    /** Manual slider value, or auto-dim based on artwork luminance when enabled. */
    private fun applyBrightness(luminance: Float?) {
        val target = if (prefs.autoBrightness && luminance != null) {
            // Dark art -> dimmer, bright art -> brighter, clamped to a sane range.
            (0.35f + luminance * 0.55f).coerceIn(0.15f, 0.95f)
        } else {
            prefs.brightness
        }
        val lp = window.attributes
        lp.screenBrightness = target
        window.attributes = lp
    }

    private fun updateClock() {
        val now = Date()
        binding.clock.text = timeFmt.format(now)
        binding.date.text = dateFmt.format(now)
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

        // Adaptive clock color from the artwork.
        binding.clock.setTextColor(track.clockColor)
        binding.date.setTextColor(track.clockColor)

        track.art?.let {
            binding.backdrop.setImageBitmap(it)
            startKenBurns()
        }

        applyBrightness(track.artLuminance)

        binding.playPause.setImageResource(
            if (track.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )

        if (bluetooth.isNullOrBlank()) {
            binding.bluetoothChip.visibility = View.GONE
        } else {
            binding.bluetoothChip.visibility = View.VISIBLE
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
        clockHandler.removeCallbacks(clockTick)
    }
}

package com.example.musiclockart

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
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
 * Full-screen, art-themed overlay over the lock screen.
 *  - Two stacked backdrops crossfade between artworks for smooth transitions.
 *  - A floating frosted-glass player card with a RenderEffect-blurred art layer
 *    behind it (mimics One UI 8.5 / iOS "liquid glass").
 *  - Adaptive-color clock, working transport controls, brightness + blur from Prefs.
 */
class LockOverlayActivity : ComponentActivity() {

    private lateinit var binding: ActivityLockOverlayBinding
    private lateinit var prefs: Prefs
    private var kenBurns: ObjectAnimator? = null

    // Which backdrop is currently shown (we crossfade to the other).
    private var showingA = true
    private var currentArtId = 0

    // Progress tracking
    private var trackDuration = 0L
    private var trackPosition = 0L
    private var isPlaying = false
    private var userSeeking = false

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 10_000)
        }
    }

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            if (isPlaying && !userSeeking) {
                trackPosition += 1000
                updateProgressUi()
            }
            progressHandler.postDelayed(this, 1000)
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

        applyBackdropBlur()
        applyCardFrost()
        applyBrightness(null)

        binding.dismiss.setOnClickListener { finish() }
        binding.playPause.setOnClickListener { Transport.playPause() }
        binding.next.setOnClickListener { Transport.next() }
        binding.prev.setOnClickListener { Transport.previous() }
        binding.shuffle.setOnClickListener { Transport.toggleShuffle() }

        binding.progress.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser && trackDuration > 0) {
                    val ms = (p / 1000f * trackDuration).toLong()
                    binding.elapsed.text = formatTime(ms)
                }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                if (trackDuration > 0) {
                    val ms = (binding.progress.progress / 1000f * trackDuration).toLong()
                    trackPosition = ms
                    Transport.seekTo(ms)
                }
                userSeeking = false
            }
        })

        observeState()
    }

    override fun onResume() {
        super.onResume()
        updateClock()
        clockHandler.postDelayed(clockTick, 10_000)
        progressHandler.postDelayed(progressTick, 1000)
        applyBackdropBlur()
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
        progressHandler.removeCallbacks(progressTick)
    }

    /** Soft blur on the full-screen backdrop (driven by Prefs blur slider). */
    private fun applyBackdropBlur() {
        val r = prefs.blurRadius.coerceAtLeast(1).toFloat()
        val fx = RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
        binding.backdropA.setRenderEffect(fx)
        binding.backdropB.setRenderEffect(fx)
    }

    /** Heavy frosted-glass blur on the player card's art layer (fixed, strong). */
    private fun applyCardFrost() {
        binding.cardBlur.setRenderEffect(
            RenderEffect.createBlurEffect(70f, 70f, Shader.TileMode.CLAMP)
        )
    }

    private fun applyBrightness(luminance: Float?) {
        val target = if (prefs.autoBrightness && luminance != null) {
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

        binding.clock.setTextColor(track.clockColor)
        binding.date.setTextColor(track.clockColor)

        track.art?.let { art ->
            if (art.generationId != currentArtId) {
                currentArtId = art.generationId
                // Compose the same full-cover + blurred-fill wallpaper the system gets,
                // so the overlay shows the whole cover (not a center-crop).
                val dm = resources.displayMetrics
                val composed = try {
                    ImageEffects.makeWallpaperBackdrop(
                        art, dm.widthPixels, dm.heightPixels, blurRadius = 0, scrim = 0.06f
                    )
                } catch (e: Exception) { art }
                crossfadeTo(composed)
                binding.cardBlur.animate().alpha(0f).setDuration(300).withEndAction {
                    binding.cardBlur.setImageBitmap(art)
                    binding.cardBlur.animate().alpha(1f).setDuration(500).start()
                }.start()
                startKenBurns()
            }
        }

        applyBrightness(track.artLuminance)

        isPlaying = track.isPlaying
        trackDuration = track.durationMs
        trackPosition = track.positionMs
        updateProgressUi()

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

    private fun updateProgressUi() {
        if (userSeeking) return
        if (trackDuration > 0) {
            val pct = (trackPosition.toFloat() / trackDuration * 1000).toInt().coerceIn(0, 1000)
            binding.progress.progress = pct
            binding.elapsed.text = formatTime(trackPosition)
            binding.total.text = formatTime(trackDuration)
        } else {
            binding.progress.progress = 0
            binding.elapsed.text = "0:00"
            binding.total.text = "0:00"
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    /** Smoothly dissolve from the visible backdrop to the new artwork. */
    private fun crossfadeTo(art: Bitmap) {
        val incoming: ImageView = if (showingA) binding.backdropB else binding.backdropA
        val outgoing: ImageView = if (showingA) binding.backdropA else binding.backdropB

        incoming.setImageBitmap(art)
        incoming.alpha = 0f
        // Incoming fades up first; outgoing lingers then fades out, so there's never
        // a frame where both are transparent (that's what caused the black flash).
        incoming.animate()
            .alpha(1f)
            .setDuration(900)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .start()
        outgoing.animate()
            .alpha(0f)
            .setStartDelay(300)
            .setDuration(900)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .start()

        showingA = !showingA
    }

    private fun startKenBurns() {
        kenBurns?.cancel()
        val target: ImageView = if (showingA) binding.backdropA else binding.backdropB

        // Living drift: a slow, organic pan + zoom that makes a static cover feel
        // alive. Each cycle picks a gentle direction so it never looks mechanical.
        val dir = (Math.random() * 4).toInt()
        val (dx, dy) = when (dir) {
            0 -> -36f to -28f
            1 -> 32f to -24f
            2 -> -30f to 26f
            else -> 34f to 22f
        }
        target.scaleX = 1.08f
        target.scaleY = 1.08f
        target.translationX = 0f
        target.translationY = 0f
        kenBurns = ObjectAnimator.ofPropertyValuesHolder(
            target,
            PropertyValuesHolder.ofFloat("scaleX", 1.08f, 1.22f),
            PropertyValuesHolder.ofFloat("scaleY", 1.08f, 1.22f),
            PropertyValuesHolder.ofFloat("translationX", 0f, dx),
            PropertyValuesHolder.ofFloat("translationY", 0f, dy)
        ).apply {
            duration = 28000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        kenBurns?.cancel()
        clockHandler.removeCallbacks(clockTick)
        progressHandler.removeCallbacks(progressTick)
    }
}

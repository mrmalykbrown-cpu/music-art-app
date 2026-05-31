package com.example.musiclockart

import android.content.Context

/**
 * Lightweight settings store backed by SharedPreferences.
 * Read by the listener (blur) and the overlay (brightness, clock, animation).
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("musiclockart", Context.MODE_PRIVATE)

    /** Wallpaper blur radius, 0..80. Default keeps it sharp. */
    var blurRadius: Int
        get() = sp.getInt(KEY_BLUR, 0)
        set(v) = sp.edit().putInt(KEY_BLUR, v.coerceIn(0, 80)).apply()

    /** Manual overlay brightness 0.05..1.0 (screen brightness override). */
    var brightness: Float
        get() = sp.getFloat(KEY_BRIGHTNESS, 0.9f)
        set(v) = sp.edit().putFloat(KEY_BRIGHTNESS, v.coerceIn(0.05f, 1.0f)).apply()

    /** If true, brightness auto-adapts to how dark the artwork is. */
    var autoBrightness: Boolean
        get() = sp.getBoolean(KEY_AUTO_BRIGHTNESS, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO_BRIGHTNESS, v).apply()

    /** If true, smooth handling for fast-changing (animated) album art. */
    var animatedArt: Boolean
        get() = sp.getBoolean(KEY_ANIMATED, true)
        set(v) = sp.edit().putBoolean(KEY_ANIMATED, v).apply()

    companion object {
        private const val KEY_BLUR = "blur_radius"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_AUTO_BRIGHTNESS = "auto_brightness"
        private const val KEY_ANIMATED = "animated_art"
    }
}

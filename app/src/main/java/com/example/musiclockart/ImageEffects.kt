package com.example.musiclockart

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlin.math.max

/**
 * Blur + tinting + color-analysis helpers.
 *
 * The wallpaper bitmap uses a fast downscale stack blur (RenderEffect is for live Views,
 * not bitmaps). The overlay card's live backdrop uses RenderEffect in LockOverlayActivity.
 */
object ImageEffects {

    /**
     * Produce a (optionally blurred, optionally darkened) full-screen backdrop.
     * blurRadius 0 keeps the art sharp.
     */
    fun makeWallpaperBackdrop(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        blurRadius: Int = 0,
        scrim: Float = 0.08f
    ): Bitmap {
        // Two-layer composition so square (1:1) art never distorts or gets heavily
        // cropped on a tall screen:
        //   1. A blurred, zoomed copy of the art fills the whole screen (no black bars).
        //   2. The full, undistorted art is drawn centered on top at its real aspect.
        val out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Layer 1: blurred fill (always blurred enough to read as a soft backdrop).
        val fillBlur = (if (blurRadius > 0) blurRadius else 55).coerceAtLeast(35)
        val filled = stackBlur(centerCrop(source, targetWidth, targetHeight), fillBlur)
        canvas.drawBitmap(filled, 0f, 0f, null)

        // Layer 2: the art itself, scaled to fit within the screen width (no crop),
        // centered vertically. If the user set a blur, blur this layer too; otherwise
        // keep it crisp.
        val fitWidth = (targetWidth * 0.94f).toInt()
        val srcRatio = source.width.toFloat() / source.height
        val artW = fitWidth
        val artH = (fitWidth / srcRatio).toInt()
        var artLayer = Bitmap.createScaledBitmap(source, artW, artH, true)
        if (blurRadius > 0) artLayer = stackBlur(artLayer, blurRadius)
        val left = (targetWidth - artW) / 2f
        val top = (targetHeight - artH) / 2f
        canvas.drawBitmap(artLayer, left, top, null)

        if (scrim > 0f) {
            val paint = Paint().apply {
                color = ColorUtils.setAlphaComponent(Color.BLACK, (scrim * 255).toInt())
            }
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), paint)
        }
        return out
    }

    /** Average luminance 0..1. Used for auto-brightness (dark art -> dimmer screen). */
    fun averageLuminance(bitmap: Bitmap): Float {
        val w = 24
        val h = 24
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pix = IntArray(w * h)
        small.getPixels(pix, 0, w, 0, 0, w, h)
        var sum = 0.0
        for (p in pix) {
            val r = (p ushr 16) and 0xff
            val g = (p ushr 8) and 0xff
            val b = p and 0xff
            sum += (0.299 * r + 0.587 * g + 0.114 * b)
        }
        return (sum / (pix.size * 255.0)).toFloat()
    }

    /**
     * Pick a legible clock color from the artwork: a light tint of the dominant
     * vibrant swatch, so the clock "matches" the wallpaper but stays readable.
     */
    fun clockColor(bitmap: Bitmap): Int {
        val palette = Palette.from(bitmap).generate()
        val base = palette.lightVibrantSwatch?.rgb
            ?: palette.vibrantSwatch?.rgb
            ?: palette.lightMutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: Color.WHITE
        return ColorUtils.blendARGB(base, Color.WHITE, 0.55f)
    }

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = w.toFloat() / h
        val (scaledW, scaledH) = if (srcRatio > dstRatio) {
            (h * srcRatio).toInt() to h
        } else {
            w to (w / srcRatio).toInt()
        }
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val x = max(0, (scaledW - w) / 2)
        val y = max(0, (scaledH - h) / 2)
        return Bitmap.createBitmap(scaled, x, y, w.coerceAtMost(scaledW), h.coerceAtMost(scaledH))
    }

    private fun stackBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return src.copy(Bitmap.Config.ARGB_8888, true)
        val scale = 0.35f
        val small = Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
        val r = (radius * scale).toInt().coerceAtLeast(1)
        val blurred = boxBlur(small, r)
        return Bitmap.createScaledBitmap(blurred, src.width, src.height, true)
    }

    private fun boxBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        boxBlurPass(pix, w, h, radius, horizontal = true)
        boxBlurPass(pix, w, h, radius, horizontal = false)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pix, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxBlurPass(pix: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val outer = if (horizontal) h else w
        val inner = if (horizontal) w else h
        val div = radius * 2 + 1
        val temp = IntArray(inner)
        for (o in 0 until outer) {
            var ra = 0; var rr = 0; var gg = 0; var bb = 0
            for (i in -radius..radius) {
                val idx = i.coerceIn(0, inner - 1)
                val p = if (horizontal) pix[o * w + idx] else pix[idx * w + o]
                ra += (p ushr 24) and 0xff
                rr += (p ushr 16) and 0xff
                gg += (p ushr 8) and 0xff
                bb += p and 0xff
            }
            for (i in 0 until inner) {
                temp[i] = ((ra / div) shl 24) or ((rr / div) shl 16) or ((gg / div) shl 8) or (bb / div)
                val addIdx = (i + radius + 1).coerceIn(0, inner - 1)
                val remIdx = (i - radius).coerceIn(0, inner - 1)
                val pAdd = if (horizontal) pix[o * w + addIdx] else pix[addIdx * w + o]
                val pRem = if (horizontal) pix[o * w + remIdx] else pix[remIdx * w + o]
                ra += ((pAdd ushr 24) and 0xff) - ((pRem ushr 24) and 0xff)
                rr += ((pAdd ushr 16) and 0xff) - ((pRem ushr 16) and 0xff)
                gg += ((pAdd ushr 8) and 0xff) - ((pRem ushr 8) and 0xff)
                bb += (pAdd and 0xff) - (pRem and 0xff)
            }
            for (i in 0 until inner) {
                if (horizontal) pix[o * w + i] = temp[i] else pix[i * w + o] = temp[i]
            }
        }
    }
}

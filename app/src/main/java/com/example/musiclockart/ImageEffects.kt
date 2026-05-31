package com.example.musiclockart

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import kotlin.math.max

/**
 * Blur + tinting helpers.
 *
 * For the wallpaper bitmap we can't use RenderEffect (that's a live-View GPU effect),
 * so we use a fast downscale-based stack blur. The live overlay card uses
 * RenderEffect.createBlurEffect() applied directly to the View in LockOverlayActivity.
 */
object ImageEffects {

    /**
     * Produce a darkened, blurred backdrop suitable for a full-screen lock wallpaper.
     */
    fun makeWallpaperBackdrop(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        blurRadius: Int = 45,
        scrim: Float = 0.35f
    ): Bitmap {
        // Center-crop the source to the target aspect ratio.
        val cropped = centerCrop(source, targetWidth, targetHeight)
        val blurred = stackBlur(cropped, blurRadius)
        // Apply a dark scrim so white text stays legible.
        val canvas = Canvas(blurred)
        val paint = Paint().apply {
            color = ColorUtils.setAlphaComponent(Color.BLACK, (scrim * 255).toInt())
        }
        canvas.drawRect(0f, 0f, blurred.width.toFloat(), blurred.height.toFloat(), paint)
        return blurred
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

    /**
     * Stack blur — fast, good-looking, no RenderScript dependency.
     * Works by downscaling, box-blurring, then upscaling.
     */
    private fun stackBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return src.copy(Bitmap.Config.ARGB_8888, true)

        // Downscale first; box blur on a small bitmap looks like a big blur upscaled.
        val scale = 0.25f
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

        // Horizontal pass
        boxBlurPass(pix, w, h, radius, horizontal = true)
        // Vertical pass
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

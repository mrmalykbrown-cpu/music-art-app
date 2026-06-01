package com.example.musiclockart

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
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
        // Edge-to-edge fill, no distortion: the cover is scaled to cover the whole
        // screen and cropped (never stretched). The crop is biased slightly upward so
        // the subject of the art (usually centered or upper-middle) stays in frame,
        // rather than a dead-center slice. High-quality bitmap sampling throughout.
        val out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        val cropped = smartCrop(source, targetWidth, targetHeight)
        val base = if (blurRadius > 0) stackBlur(cropped, blurRadius) else cropped
        canvas.drawBitmap(base, 0f, 0f, paint)

        // Subtle top & bottom darkening so the clock and any controls stay legible
        // over bright art (this is gradient, not a flat black-out).
        val edge = targetHeight * 0.22f
        val topGrad = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, edge,
                Color.argb(120, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        val botGrad = Paint().apply {
            shader = LinearGradient(
                0f, targetHeight - edge, 0f, targetHeight.toFloat(),
                Color.TRANSPARENT, Color.argb(140, 0, 0, 0), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, targetWidth.toFloat(), edge, topGrad)
        canvas.drawRect(0f, targetHeight - edge, targetWidth.toFloat(), targetHeight.toFloat(), botGrad)

        if (scrim > 0f) {
            val s = Paint().apply {
                color = ColorUtils.setAlphaComponent(Color.BLACK, (scrim * 255).toInt())
            }
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), s)
        }
        return out
    }

    /**
     * Content-aware cover crop. Scales the source to cover the target (no stretch),
     * then chooses the crop window centred on the image's focal point — the region
     * with the most visual "weight" (a blend of edge/detail density and luminance
     * contrast, which faces and subjects score highly on). Falls back gracefully to
     * centre if analysis is inconclusive.
     */
    private fun smartCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val scaledW = (src.width * scale).toInt().coerceAtLeast(w)
        val scaledH = (src.height * scale).toInt().coerceAtLeast(h)
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)

        // Only the vertical axis usually needs a smart anchor (square -> tall screen
        // crops top/bottom). Find the row band with the highest interest score.
        val focalY = focalPointY(scaled, h)
        val x = ((scaledW - w) / 2).coerceAtLeast(0)
        val y = focalY.coerceIn(0, (scaledH - h).coerceAtLeast(0))
        return Bitmap.createBitmap(scaled, x, y, w, h)
    }

    /**
     * Returns the top Y for an h-tall crop window so it's centred on the most
     * visually interesting horizontal band. Interest = local detail (difference
     * between neighbouring rows) + deviation from mean brightness.
     */
    private fun focalPointY(bmp: Bitmap, cropH: Int): Int {
        val sampleW = 32
        val sampleH = 64
        val small = Bitmap.createScaledBitmap(bmp, sampleW, sampleH, true)
        val pix = IntArray(sampleW * sampleH)
        small.getPixels(pix, 0, sampleW, 0, 0, sampleW, sampleH)

        // Per-row average luminance.
        val rowLum = FloatArray(sampleH)
        var meanLum = 0f
        for (r in 0 until sampleH) {
            var sum = 0f
            for (c in 0 until sampleW) {
                val p = pix[r * sampleW + c]
                val lum = 0.299f * ((p ushr 16) and 0xff) +
                          0.587f * ((p ushr 8) and 0xff) +
                          0.114f * (p and 0xff)
                sum += lum
            }
            rowLum[r] = sum / sampleW
            meanLum += rowLum[r]
        }
        meanLum /= sampleH

        // Interest per row: vertical detail (|row - neighbour|) + brightness deviation.
        val interest = FloatArray(sampleH)
        for (r in 0 until sampleH) {
            val detail = if (r > 0) kotlin.math.abs(rowLum[r] - rowLum[r - 1]) else 0f
            val dev = kotlin.math.abs(rowLum[r] - meanLum)
            interest[r] = detail * 1.5f + dev
        }

        // Slide a window the height of the crop (in sample space) and pick the band
        // with the greatest summed interest; centre the real crop on it.
        val winRows = (cropH.toFloat() / bmp.height * sampleH).toInt().coerceIn(1, sampleH)
        var bestStart = 0
        var bestScore = -1f
        var running = 0f
        for (r in 0 until sampleH) {
            running += interest[r]
            if (r >= winRows) running -= interest[r - winRows]
            if (r >= winRows - 1 && running > bestScore) {
                bestScore = running
                bestStart = r - winRows + 1
            }
        }
        // Map the sample-space band back to full scaled-bitmap coordinates.
        val bandCenterFrac = (bestStart + winRows / 2f) / sampleH
        val targetTop = (bandCenterFrac * bmp.height - cropH / 2f).toInt()
        return targetTop
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
        val scale = 0.5f
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

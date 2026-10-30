package com.printbridge.engine

import android.graphics.Bitmap
import android.graphics.Color

object Raster {
    data class Mono(val data: ByteArray, val widthBytes: Int, val height: Int) {
        operator fun component1() = data
        operator fun component2() = widthBytes
        operator fun component3() = height
    }

    /** Packed 1bpp rows, MSB first, black pixel = 1 bit. */
    fun toMonochrome(src: Bitmap, threshold: Int = 128, invert: Boolean = false, dither: Boolean = false): Mono {
        val w = src.width; val h = src.height
        val wBytes = (w + 7) / 8
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val lum = FloatArray(w * h) { i ->
            val c = px[i]
            if (Color.alpha(c) < 128) 255f
            else 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        }
        if (dither) {
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x; val old = lum[i]; val nw = if (old < threshold) 0f else 255f
                lum[i] = nw; val err = old - nw
                if (x + 1 < w) lum[i + 1] += err * 7 / 16
                if (y + 1 < h) {
                    if (x > 0) lum[i + w - 1] += err * 3 / 16
                    lum[i + w] += err * 5 / 16
                    if (x + 1 < w) lum[i + w + 1] += err * 1 / 16
                }
            }
        }
        val out = ByteArray(wBytes * h)
        for (y in 0 until h) for (x in 0 until w) {
            var black = lum[y * w + x] < threshold
            if (invert) black = !black
            if (black) out[y * wBytes + x / 8] = (out[y * wBytes + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
        }
        return Mono(out, wBytes, h)
    }
}

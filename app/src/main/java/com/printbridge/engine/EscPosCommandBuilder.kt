package com.printbridge.engine

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

class EscPosCommandBuilder {
    private val out = ByteArrayOutputStream()
    private fun b(vararg v: Int) = out.write(ByteArray(v.size) { v[it].toByte() })

    fun init() = apply { b(0x1B, 0x40) }
    fun text(s: String) = apply { out.write(s.toByteArray(Charsets.UTF_8)) }
    fun newline(n: Int = 1) = apply { repeat(n) { b(0x0A) } }
    fun feed(lines: Int) = apply { b(0x1B, 0x64, lines.coerceIn(0, 255)) }
    fun align(a: Int) = apply { b(0x1B, 0x61, a) }
    fun bold(on: Boolean) = apply { b(0x1B, 0x45, if (on) 1 else 0) }
    fun cut(partial: Boolean = true) = apply { b(0x1D, 0x56, if (partial) 1 else 0) }

    fun barcode(data: String, height: Int = 80) = apply {
        b(0x1D, 0x68, height); b(0x1D, 0x48, 2); b(0x1D, 0x77, 2)
        val payload = "{B$data"
        b(0x1D, 0x6B, 73, payload.length); out.write(payload.toByteArray(Charsets.US_ASCII))
    }

    fun qr(data: String, size: Int = 6) = apply {
        val d = data.toByteArray(Charsets.UTF_8); val len = d.size + 3
        b(0x1D, 0x28, 0x6B, 4, 0, 49, 65, 50, 0)
        b(0x1D, 0x28, 0x6B, 3, 0, 49, 67, size)
        b(0x1D, 0x28, 0x6B, 3, 0, 49, 69, 49)
        b(0x1D, 0x28, 0x6B, len and 0xFF, len shr 8, 49, 80, 48); out.write(d)
        b(0x1D, 0x28, 0x6B, 3, 0, 49, 81, 48)
    }

    /** GS v 0 raster, banded so small printer buffers don't overflow. */
    fun bitmap(bmp: Bitmap, threshold: Int = 128, invert: Boolean = false, dither: Boolean = false) = apply {
        val (data, wBytes, h) = Raster.toMonochrome(bmp, threshold, invert, dither)
        var y = 0
        while (y < h) {
            val rows = minOf(255, h - y)
            b(0x1D, 0x76, 0x30, 0, wBytes and 0xFF, wBytes shr 8, rows and 0xFF, rows shr 8)
            out.write(data, y * wBytes, rows * wBytes); y += rows
        }
    }
    fun build(): ByteArray = out.toByteArray()
}

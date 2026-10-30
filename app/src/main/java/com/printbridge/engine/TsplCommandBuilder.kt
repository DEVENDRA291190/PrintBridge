package com.printbridge.engine

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

enum class PaperType { GAP, BLACK_MARK, CONTINUOUS }

data class LabelConfig(
    val widthMm: Float, val heightMm: Float,
    val gapMm: Float = 2f, val paper: PaperType = PaperType.GAP,
    val density: Int = 8, val speed: Int = 4,
    val offsetX: Int = 0, val offsetY: Int = 0,
    val dpi: Int = 203, val copies: Int = 1,
) {
    init { require(widthMm > 0 && (paper == PaperType.CONTINUOUS || heightMm > 0)) { "Invalid label size" } }
    val widthDots get() = (widthMm * dpi / 25.4f).toInt()
    val heightDots get() = (heightMm * dpi / 25.4f).toInt()
}

class TsplCommandBuilder(private val cfg: LabelConfig) {
    private val out = ByteArrayOutputStream()
    private fun line(s: String) = out.write("$s\r\n".toByteArray(Charsets.US_ASCII))
    private fun fmt(f: Float) = if (f % 1f == 0f) f.toInt().toString() else "%.1f".format(f)

    fun begin() = apply {
        line("SIZE ${fmt(cfg.widthMm)} mm,${fmt(cfg.heightMm)} mm")
        when (cfg.paper) {
            PaperType.GAP -> line("GAP ${fmt(cfg.gapMm)} mm,0 mm")
            PaperType.BLACK_MARK -> line("BLINE ${fmt(cfg.gapMm)} mm,0 mm")
            PaperType.CONTINUOUS -> line("GAP 0 mm,0 mm")
        }
        line("DIRECTION 1,0")
        line("REFERENCE ${cfg.offsetX},${cfg.offsetY}")
        line("DENSITY ${cfg.density.coerceIn(0, 15)}")
        line("SPEED ${cfg.speed}")
        line("CLS")
    }

    fun text(x: Int, y: Int, text: String, font: String = "3", xMul: Int = 1, yMul: Int = 1, rot: Int = 0) =
        apply { line("TEXT $x,$y,\"$font\",$rot,$xMul,$yMul,\"${text.replace("\"", "\\\"")}\"") }

    fun barcode(x: Int, y: Int, data: String, type: String = "128", height: Int = 80, readable: Boolean = true) =
        apply { line("BARCODE $x,$y,\"$type\",$height,${if (readable) 1 else 0},0,2,4,\"$data\"") }

    fun qr(x: Int, y: Int, data: String, cell: Int = 6) =
        apply { line("QRCODE $x,$y,L,$cell,A,0,M2,S7,\"$data\"") }

    /** TSPL BITMAP mode 0: bit 0 = black, so rows are inverted from our black=1 packing. */
    fun bitmap(x: Int, y: Int, bmp: Bitmap, threshold: Int = 128, invert: Boolean = false, dither: Boolean = false) = apply {
        val (bytes, wBytes, h) = Raster.toMonochrome(bmp, threshold, invert, dither)
        val inv = ByteArray(bytes.size) { bytes[it].toInt().inv().toByte() }
        out.write("BITMAP $x,$y,$wBytes,$h,0,".toByteArray(Charsets.US_ASCII))
        out.write(inv); out.write("\r\n".toByteArray())
    }

    fun print(copies: Int = cfg.copies) = apply { line("PRINT 1,$copies") }
    fun cutter(on: Boolean) = apply { line("SET CUTTER ${if (on) "ON" else "OFF"}") }
    fun peel(on: Boolean) = apply { line("SET PEEL ${if (on) "ON" else "OFF"}") }
    fun calibrateGap() = apply { line("GAPDETECT") }
    fun feed() = apply { line("FORMFEED") }
    fun build(): ByteArray = out.toByteArray()
}

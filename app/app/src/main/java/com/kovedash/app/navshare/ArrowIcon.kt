package com.kovedash.app.navshare

import kotlin.math.abs

/**
 * The maneuver arrow from a Google Maps navigation notification, reduced to a small
 * normalized "ink" grid. Pure Kotlin (no android.graphics) so it's unit-testable off-device;
 * the listener converts the notification's large-icon Bitmap to ARGB pixels and calls [fromArgb].
 *
 * Why the icon at all: at named Japanese intersections Maps' text carries no direction
 * ("40 m · 〇〇交差点 · onto 〇〇通り") — the turn exists ONLY in the arrow icon, so the text
 * classifier falls through to UNKNOWN and the dash draws its straight fallback.
 *
 * [ink] is [SIZE]×[SIZE], row-major, values 0..255, cropped to the arrow's bounding box so a
 * differently-padded or differently-scaled render of the same glyph normalizes the same.
 */
class ArrowIcon private constructor(val ink: IntArray) {

    /** 256-bit average hash (one bit per cell: ink above the grid mean). Stable per glyph. */
    val hash: LongArray by lazy {
        val mean = ink.average()
        LongArray(CELLS / 64).also { h ->
            for (i in 0 until CELLS) if (ink[i] > mean) h[i / 64] = h[i / 64] or (1L shl (i % 64))
        }
    }

    val hashHex: String get() = hash.joinToString("") { "%016x".format(it) }

    /**
     * Horizontal lean of the arrow: centroid-x of the top third minus centroid-x of the bottom
     * third, as a fraction of the grid width (−1..1). Every Maps turn arrow starts from a stem at
     * the bottom and ends at the head up top, so the head's offset from the stem IS the direction:
     * ↑ ≈ 0, ↰ strongly negative, ↖ moderately negative, mirrors positive.
     */
    val lean: Double by lazy {
        val third = SIZE / 3
        centroidX(0, third) - centroidX(SIZE - third, SIZE)
    }

    /**
     * Reference-free direction read from [lean]. Deliberately coarse — it only has to beat the
     * straight fallback — and returns UNKNOWN when the lean is ambiguous (roundabouts, U-turns
     * and near-straight glyphs), so a learned [IconManeuverMemory] match always wins over it.
     */
    fun geometricGuess(): Maneuver {
        val l = lean
        return when {
            l.isNaN() -> Maneuver.UNKNOWN
            l <= -TURN_LEAN -> Maneuver.TURN_LEFT
            l <= -SLIGHT_LEAN -> Maneuver.SLIGHT_LEFT
            l >= TURN_LEAN -> Maneuver.TURN_RIGHT
            l >= SLIGHT_LEAN -> Maneuver.SLIGHT_RIGHT
            else -> Maneuver.UNKNOWN
        }
    }

    private fun centroidX(rowFrom: Int, rowTo: Int): Double {
        var sum = 0.0
        var weighted = 0.0
        for (y in rowFrom until rowTo) for (x in 0 until SIZE) {
            val v = ink[y * SIZE + x]
            sum += v
            weighted += v * (x + 0.5)
        }
        return if (sum == 0.0) Double.NaN else weighted / sum / SIZE
    }

    companion object {
        const val SIZE = 16
        private const val CELLS = SIZE * SIZE

        // Lean thresholds (fraction of width). A ↰ puts its head ~⅓+ of the width off the stem;
        // a ↖ bear roughly half that. Calibrate against real icons via the debug dump.
        internal const val TURN_LEAN = 0.28
        internal const val SLIGHT_LEAN = 0.12

        /** Hamming distance between two [hash]es. */
        fun distance(a: LongArray, b: LongArray): Int =
            a.indices.sumOf { java.lang.Long.bitCount(a[it] xor b[it]) }

        fun parseHex(hex: String): LongArray? {
            if (hex.length != CELLS / 4) return null
            return runCatching {
                LongArray(CELLS / 64) { java.lang.Long.parseUnsignedLong(hex.substring(it * 16, it * 16 + 16), 16) }
            }.getOrNull()
        }

        /**
         * @param argb row-major ARGB_8888 pixels (Bitmap.getPixels order).
         * @return null when the image has no discernible arrow (blank / uniform).
         *
         * Ink = alpha when the icon is transparent-backed (Maps' white-on-clear arrow); for an
         * opaque icon, ink = luminance distance from the corner (background) color instead.
         */
        fun fromArgb(argb: IntArray, width: Int, height: Int): ArrowIcon? {
            if (width <= 0 || height <= 0 || argb.size < width * height) return null
            val n = width * height
            val raw = IntArray(n)
            val hasAlpha = argb.take(n).any { (it ushr 24) < 250 }
            if (hasAlpha) {
                for (i in 0 until n) raw[i] = argb[i] ushr 24
            } else {
                val corners = intArrayOf(0, width - 1, n - width, n - 1)
                val bg = corners.map { lum(argb[it]) }.average()
                for (i in 0 until n) raw[i] = abs(lum(argb[i]) - bg).toInt().coerceIn(0, 255)
            }

            // Bounding box of meaningful ink.
            var minX = width; var minY = height; var maxX = -1; var maxY = -1
            for (y in 0 until height) for (x in 0 until width) {
                if (raw[y * width + x] >= INK_MIN) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            if (maxX < 0) return null
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1

            // Box-average downsample of the bounding box to SIZE×SIZE.
            val grid = IntArray(CELLS)
            for (gy in 0 until SIZE) for (gx in 0 until SIZE) {
                val x0 = minX + gx * bw / SIZE
                val x1 = maxOf(x0 + 1, minX + (gx + 1) * bw / SIZE)
                val y0 = minY + gy * bh / SIZE
                val y1 = maxOf(y0 + 1, minY + (gy + 1) * bh / SIZE)
                var s = 0L
                var c = 0
                for (y in y0 until minOf(y1, height)) for (x in x0 until minOf(x1, width)) {
                    s += raw[y * width + x]; c++
                }
                grid[gy * SIZE + gx] = if (c == 0) 0 else (s / c).toInt()
            }
            return ArrowIcon(grid)
        }

        private const val INK_MIN = 64

        private fun lum(c: Int): Double =
            0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
    }
}

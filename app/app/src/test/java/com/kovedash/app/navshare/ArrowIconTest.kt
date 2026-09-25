package com.kovedash.app.navshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic Maps-style arrows (white ink on transparent, 64×64) drawn as thick strokes:
 * a stem up from the bottom, then the head segment. Enough to exercise cropping, hashing,
 * the learned-match path and the lean heuristic without shipping real Maps bitmaps.
 */
class ArrowIconTest {

    private val dim = 64

    private fun canvas() = IntArray(dim * dim)

    private fun IntArray.rect(x0: Int, y0: Int, x1: Int, y1: Int): IntArray {
        for (y in y0 until y1) for (x in x0 until x1) this[y * dim + x] = 0xFFFFFFFF.toInt()
        return this
    }

    // Thick diagonal from (x0,y0) toward (x1,y1).
    private fun IntArray.line(x0: Int, y0: Int, x1: Int, y1: Int, t: Int = 5): IntArray {
        val steps = maxOf(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0))
        for (i in 0..steps) {
            val cx = x0 + (x1 - x0) * i / steps
            val cy = y0 + (y1 - y0) * i / steps
            rect(cx - t, cy - t, cx + t, cy + t)
        }
        return this
    }

    private fun icon(px: IntArray) = ArrowIcon.fromArgb(px, dim, dim)!!

    // ↑ stem + symmetric head
    private fun straight() = canvas().rect(28, 20, 36, 60).line(32, 6, 18, 22).line(32, 6, 46, 22)
    // ↰ stem at right, bar to the left, head at far left
    private fun turnLeft() = canvas().rect(40, 20, 48, 60).rect(14, 16, 48, 24).line(8, 20, 20, 8).line(8, 20, 20, 32)
    private fun turnRight() = canvas().rect(16, 20, 24, 60).rect(16, 16, 50, 24).line(56, 20, 44, 8).line(56, 20, 44, 32)
    // ↖ stem bottom-center, bending up-left
    private fun slightLeft() = canvas().rect(34, 36, 42, 60).line(38, 38, 22, 14).line(20, 12, 34, 12).line(20, 12, 20, 26)

    @Test
    fun blank_image_has_no_arrow() {
        assertNull(ArrowIcon.fromArgb(canvas(), dim, dim))
    }

    @Test
    fun lean_reads_direction() {
        assertEquals(Maneuver.UNKNOWN, icon(straight()).geometricGuess())
        assertEquals(Maneuver.TURN_LEFT, icon(turnLeft()).geometricGuess())
        assertEquals(Maneuver.TURN_RIGHT, icon(turnRight()).geometricGuess())
        assertTrue(icon(slightLeft()).lean < -ArrowIcon.SLIGHT_LEAN)
    }

    @Test
    fun hash_is_stable_across_padding() {
        // Same glyph shifted inside the canvas → identical after bounding-box crop.
        val shifted = canvas()
        val src = turnLeft()
        for (y in 0 until dim) for (x in 0 until dim - 4) shifted[y * dim + x + 4] = src[y * dim + x]
        assertEquals(0, ArrowIcon.distance(icon(src).hash, icon(shifted).hash))
        assertTrue(ArrowIcon.distance(icon(turnLeft()).hash, icon(turnRight()).hash) > IconManeuverMemory.MATCH_MAX_DISTANCE)
    }

    @Test
    fun hex_roundtrip() {
        val h = icon(turnLeft()).hash
        assertEquals(h.toList(), ArrowIcon.parseHex(icon(turnLeft()).hashHex)!!.toList())
    }

    private class MemStorage : IconManeuverMemory.Storage {
        var saved: Map<String, String> = emptyMap()
        override fun load() = saved
        override fun save(entries: Map<String, String>) { saved = entries }
    }

    @Test
    fun silent_text_uses_icon_and_learns_from_worded_frames() {
        val storage = MemStorage()
        val c = IconAwareManeuverClassifier(TextManeuverClassifier(), IconManeuverMemory(storage))
        // The photo case: named intersection, no direction word → icon decides (geometric).
        assertEquals(Maneuver.TURN_LEFT, c.classify("40 m · 〇〇交差点", "onto 国道0号 〇〇通り", null, icon(turnLeft())))

        // A worded frame teaches the (ambiguous-lean) slight-left glyph...
        assertEquals(Maneuver.SLIGHT_LEFT, c.classify(null, "Slight left onto Route 1", null, icon(slightLeft())))
        assertEquals(1, storage.saved.size)
        // ...so a later silent frame with that icon is matched exactly, even after a restart.
        val restarted = IconAwareManeuverClassifier(TextManeuverClassifier(), IconManeuverMemory(storage))
        assertEquals(Maneuver.SLIGHT_LEFT, restarted.classify(null, "〇〇交差点", null, icon(slightLeft())))
    }

    @Test
    fun worded_text_wins_and_no_icon_falls_back_to_text() {
        val c = IconAwareManeuverClassifier(TextManeuverClassifier(), IconManeuverMemory(MemStorage()))
        assertEquals(Maneuver.TURN_RIGHT, c.classify(null, "Turn right onto Broadway", null, icon(turnLeft())))
        assertEquals(Maneuver.UNKNOWN, c.classify(null, "〇〇交差点", null, null))
        // Straight icon + silent text stays on the straight fallback.
        assertEquals(Maneuver.UNKNOWN, c.classify(null, "〇〇交差点", null, icon(straight())))
    }

    @Test
    fun blank_text_uses_title_like_the_parser() {
        assertEquals(Maneuver.TURN_LEFT, TextManeuverClassifier().classify("40 m · Turn left onto X", "", null))
    }
}

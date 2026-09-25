package com.kovedash.app.navshare

/**
 * Self-calibrating icon → [Maneuver] table. We ship no reference bitmaps of Maps' arrows, so
 * the table is learned on the road: whenever a notification's TEXT names the maneuver
 * unambiguously ("Turn left onto …"), its icon hash is remembered as that maneuver. Later
 * frames whose text is silent (Japanese named intersections) match the icon against what was
 * learned — Gadgetbridge's Hamming-match idea, with the table filled in by use instead of by hand.
 *
 * Persistence goes through [Storage] so this stays pure/unit-testable; the listener backs it
 * with SharedPreferences.
 */
class IconManeuverMemory(private val storage: Storage) {

    interface Storage {
        fun load(): Map<String, String>
        fun save(entries: Map<String, String>)
    }

    private val entries: LinkedHashMap<String, Maneuver> = LinkedHashMap<String, Maneuver>().apply {
        storage.load().forEach { (hex, name) ->
            val m = runCatching { Maneuver.valueOf(name) }.getOrNull()
            if (m != null && ArrowIcon.parseHex(hex) != null) put(hex, m)
        }
    }

    @Synchronized
    fun lookup(icon: ArrowIcon): Maneuver? {
        var best: Maneuver? = null
        var bestDist = MATCH_MAX_DISTANCE + 1
        for ((hex, m) in entries) {
            val d = ArrowIcon.distance(icon.hash, ArrowIcon.parseHex(hex) ?: continue)
            if (d < bestDist) { bestDist = d; best = m }
        }
        return best
    }

    /** Remember [icon] as [maneuver]. Only call with a maneuver the text stated explicitly. */
    @Synchronized
    fun learn(icon: ArrowIcon, maneuver: Maneuver) {
        if (maneuver == Maneuver.UNKNOWN) return
        val hex = icon.hashHex
        if (entries[hex] == maneuver) return
        entries.remove(hex) // re-insert as most recent (latest text wins on conflict)
        entries[hex] = maneuver
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        storage.save(entries.mapValues { it.value.name })
    }

    companion object {
        // Maps renders each glyph identically, so a true match is near 0 of 256 bits; 20 absorbs
        // resampling noise between icon sizes without letting ↰ and ↖ collide.
        internal const val MATCH_MAX_DISTANCE = 20
        private const val MAX_ENTRIES = 128
    }
}

package com.kovedash.app.navshare

/**
 * Turns the raw fields of a Google Maps navigation notification into a [Maneuver].
 *
 * Text parsing is English-only and brittle to Maps wording, so [NavNotificationListener]
 * wraps it in [IconAwareManeuverClassifier], which falls back to the maneuver-arrow bitmap
 * (Gadgetbridge-style Hamming match, locale-independent) when the text names no direction.
 */
interface ManeuverClassifier {
    /** @param icon the notification's maneuver arrow, when one could be read (else null). */
    fun classify(title: String?, text: String?, subText: String?, icon: ArrowIcon? = null): Maneuver
}

/**
 * English keyword classifier over the notification text. Google Maps puts the turn
 * instruction in `android.text` (e.g. "Turn left onto Pearl St", "At the roundabout,
 * take the 2nd exit", "Slight right to stay on US-36").
 *
 * Ordering is deliberate: compound phrases ("slight left", "sharp right", "keep left")
 * are tested BEFORE the bare "left"/"right" so they never fall through to a plain turn.
 */
class TextManeuverClassifier : ManeuverClassifier {

    override fun classify(title: String?, text: String?, subText: String?, icon: ArrowIcon?): Maneuver {
        // Classic Maps puts the instruction in `text`; ProgressStyle puts it in `title` and leaves
        // `text` null or blank. Same field choice as NavNotificationParser.parse's `instruction`.
        val s = (text?.takeIf { it.isNotBlank() } ?: title ?: "").lowercase()
        if (s.isBlank()) return Maneuver.UNKNOWN

        return when {
            // Arrival / destination — check first; "arrive" can co-occur with a side.
            s.contains("arrive") || s.contains("destination") || s.contains("you have reached") -> Maneuver.ARRIVE

            // U-turn.
            s.contains("u-turn") || s.contains("uturn") || s.contains("make a u") -> Maneuver.UTURN

            // Roundabout / rotary (side/exit handled later as a follow-up).
            s.contains("roundabout") || s.contains("rotary") || s.contains("traffic circle") -> Maneuver.ROUNDABOUT

            // Compound directional — the actual turn geometry. MUST precede both the bare
            // turns AND the ramp/exit/merge type checks (an instruction like "Slight right
            // onto the ramp" is a slight-right, not a generic off-ramp).
            s.contains("sharp left") -> Maneuver.SHARP_LEFT
            s.contains("sharp right") -> Maneuver.SHARP_RIGHT
            s.contains("slight left") -> Maneuver.SLIGHT_LEFT
            s.contains("slight right") -> Maneuver.SLIGHT_RIGHT
            s.contains("keep left") -> Maneuver.KEEP_LEFT
            s.contains("keep right") -> Maneuver.KEEP_RIGHT

            // Ramps / merges / forks — maneuver TYPES that apply when no explicit
            // slight/sharp/keep direction was given above.
            s.contains("merge") -> Maneuver.MERGE
            s.contains("fork") && s.contains("left") -> Maneuver.FORK_LEFT
            s.contains("fork") && s.contains("right") -> Maneuver.FORK_RIGHT
            s.contains("exit") || s.contains("off-ramp") || s.contains("off ramp") || s.contains("ramp") -> Maneuver.OFF_RAMP

            // Bare turns.
            s.contains("turn left") || s.contains("left onto") || s.contains("left toward") || s.contains("left to ") -> Maneuver.TURN_LEFT
            s.contains("turn right") || s.contains("right onto") || s.contains("right toward") || s.contains("right to ") -> Maneuver.TURN_RIGHT

            // Continue / head / straight.
            s.contains("continue") || s.contains("head ") || s.contains("go straight") || s.contains("straight") || s.contains("stay on") -> Maneuver.CONTINUE

            // Last resort: a lone "left"/"right" mention.
            s.contains("left") -> Maneuver.TURN_LEFT
            s.contains("right") -> Maneuver.TURN_RIGHT

            else -> Maneuver.UNKNOWN
        }
    }
}

/**
 * Text first, icon second. Maps' wording is authoritative when it names the maneuver, and each
 * such frame teaches [memory] what that icon looks like. When the text is silent (UNKNOWN or a
 * bare CONTINUE — Japanese named-intersection frames read "〇〇交差点 · onto 〇〇通り"), the
 * icon decides: a learned match first, then the reference-free [ArrowIcon.geometricGuess].
 *
 * CONTINUE is overridable because "straight" is also what a silent frame degrades to; an icon
 * that clearly leans left/right is better evidence than the absence of a turn word.
 */
class IconAwareManeuverClassifier(
    private val text: ManeuverClassifier,
    private val memory: IconManeuverMemory,
) : ManeuverClassifier {

    override fun classify(title: String?, text: String?, subText: String?, icon: ArrowIcon?): Maneuver {
        val fromText = this.text.classify(title, text, subText, icon)
        if (icon == null) return fromText
        if (fromText != Maneuver.UNKNOWN && fromText != Maneuver.CONTINUE) {
            memory.learn(icon, fromText)
            return fromText
        }
        val fromIcon = memory.lookup(icon) ?: icon.geometricGuess()
        return if (fromIcon != Maneuver.UNKNOWN) fromIcon else fromText
    }
}

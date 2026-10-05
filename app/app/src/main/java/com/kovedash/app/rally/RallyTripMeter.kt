// Added by k-tetsuhiro for kove-dash-jp (2026): rally trip meter (ODO / PART / CAP).
package com.kovedash.app.rally

import com.kovedash.app.nav.haversineMeters
import com.kovedash.app.net.GpsFix

/**
 * GPS rally trip meter: ODO + PART distance, heading (CAP), speed, average and elapsed time.
 * Pure logic, no Android — [RallyHost] feeds it fixes and wall-clock time and persists it.
 *
 * Distance is summed fix-to-fix from an anchor that only advances while the bike is moving.
 * Standing still, GPS wanders a few metres per second; keeping the anchor put means that
 * wander never adds up, and anything the bike really covered at walking pace is still
 * counted once it moves off again. The same anchor makes a GPS dropout (tunnel, tree cover)
 * cost only the difference between the road and a straight line: the first fix back is
 * measured from the last one before the gap.
 *
 * Adjusting pauses the count (ICO's "OdoPause"): the rider sets ODO to the roadbook figure
 * without chasing a number that keeps rolling, and the distance covered meanwhile is added
 * once they stop pressing.
 */
class RallyTripMeter {

    private var odoM = 0.0
    private var partM = 0.0
    private var startedAtMs: Long? = null

    private var anchor: GpsFix? = null
    private var lastFixAtMs: Long? = null
    private var speedMps: Double? = null
    private var altitudeM: Double? = null
    private var capDeg: Double? = null
    private var capAtMs: Long? = null

    private var pauseUntilMs = 0L
    private var pendingRollM = 0.0
    private var adjustSumM = 0.0

    fun onFix(fix: GpsFix, nowMs: Long) {
        settle(nowMs)
        // A fix this vague can place the bike anywhere on the next street; it neither
        // counts distance nor keeps GPS "live" for the display.
        if ((fix.accuracyMeters ?: 0.0) > MAX_ACCURACY_M) return
        lastFixAtMs = nowMs
        speedMps = fix.speedMps
        fix.altitudeMeters?.let { altitudeM = it }
        // GPS course is noise below walking-plus pace; hold the last good one instead.
        val bearing = fix.bearingDeg
        if (bearing != null && (fix.speedMps ?: 0.0) >= CAP_MIN_SPEED_MPS) {
            capDeg = bearing
            capAtMs = nowMs
        }

        val a = anchor ?: run { anchor = fix; return }
        val dtSec = (fix.tsMillis - a.tsMillis) / 1000.0
        if (dtSec <= 0) return
        val d = haversineMeters(a.lat, a.lon, fix.lat, fix.lon)
        val moving = fix.speedMps?.let { it >= MIN_MOVING_SPEED_MPS }
            // No Doppler speed: only trust a move that clears the fix's own error circle.
            ?: (d >= maxOf(fix.accuracyMeters ?: 0.0, MIN_STEP_WITHOUT_SPEED_M) && d / dtSec >= MIN_MOVING_SPEED_MPS)
        if (!moving) return
        anchor = fix
        // A jump no bike could make is a bad fix; re-anchor and drop the segment.
        if (d / dtSec > MAX_PLAUSIBLE_SPEED_MPS) return
        if (startedAtMs == null) startedAtMs = nowMs
        if (nowMs < pauseUntilMs) pendingRollM += d else addDistance(d)
    }

    /** ODO and PART ±[deltaM] together, and (re)start the adjust pause. */
    fun adjust(deltaM: Double, nowMs: Long) {
        settle(nowMs)
        if (nowMs >= pauseUntilMs) adjustSumM = 0.0
        pauseUntilMs = nowMs + ADJUST_PAUSE_MS
        adjustSumM += deltaM
        addDistance(deltaM)
    }

    fun resetPart(nowMs: Long) {
        settle(nowMs)
        partM = 0.0
    }

    /** Start a new day: ODO, PART and the clock all back to zero. */
    fun resetAll(nowMs: Long) {
        odoM = 0.0
        partM = 0.0
        startedAtMs = null
        pendingRollM = 0.0
        pauseUntilMs = 0L
        adjustSumM = 0.0
        // Keep the anchor: the bike is wherever it is; counting resumes from here.
        settle(nowMs)
    }

    fun readout(nowMs: Long): RallyReadout {
        settle(nowMs)
        val gpsOk = lastFixAtMs?.let { nowMs - it <= GPS_LOST_MS } ?: false
        val elapsedMs = startedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
        val avgKmh = if (elapsedMs >= MIN_AVG_ELAPSED_MS) odoM / 1000.0 / (elapsedMs / 3_600_000.0) else null
        val capFresh = capAtMs?.let { nowMs - it <= CAP_STALE_MS } ?: false
        val adjusting = nowMs < pauseUntilMs
        return RallyReadout(
            odoKm = odoM / 1000.0,
            partKm = partM / 1000.0,
            speedKmh = if (gpsOk) speedMps?.let { it * 3.6 } else null,
            avgKmh = avgKmh,
            elapsedMs = elapsedMs,
            capDeg = capDeg,
            capStale = !gpsOk || !capFresh,
            gpsOk = gpsOk,
            altitudeM = altitudeM,
            adjustingKm = if (adjusting) adjustSumM / 1000.0 else null,
        )
    }

    fun saved(): RallySaved = RallySaved(odoM + pendingRollM, partM + pendingRollM, startedAtMs)

    fun restore(s: RallySaved) {
        odoM = s.odoM.coerceAtLeast(0.0)
        partM = s.partM.coerceAtLeast(0.0)
        startedAtMs = s.startedAtMs
    }

    private fun settle(nowMs: Long) {
        if (nowMs >= pauseUntilMs && pendingRollM != 0.0) {
            addDistance(pendingRollM)
            pendingRollM = 0.0
        }
    }

    private fun addDistance(d: Double) {
        odoM = (odoM + d).coerceAtLeast(0.0)
        partM = (partM + d).coerceAtLeast(0.0)
    }

    companion object {
        /** One press of ± moves ODO by the roadbook's last digit, 0.01 km. */
        const val ADJUST_STEP_M = 10.0

        // Starting points, to be tuned on real rides.
        internal const val MAX_ACCURACY_M = 25.0
        internal const val MIN_MOVING_SPEED_MPS = 0.8        // ~3 km/h
        internal const val MIN_STEP_WITHOUT_SPEED_M = 10.0
        internal const val MAX_PLAUSIBLE_SPEED_MPS = 70.0    // ~250 km/h
        internal const val CAP_MIN_SPEED_MPS = 2.8           // ~10 km/h
        internal const val CAP_STALE_MS = 15_000L
        internal const val GPS_LOST_MS = 5_000L
        internal const val ADJUST_PAUSE_MS = 2_000L
        internal const val MIN_AVG_ELAPSED_MS = 10_000L
    }
}

data class RallyReadout(
    val odoKm: Double,
    val partKm: Double,
    val speedKmh: Double?,
    val avgKmh: Double?,
    val elapsedMs: Long,
    val capDeg: Double?,
    /** No fresh course: GPS lost, or stopped / crawling long enough that CAP is just the last one. */
    val capStale: Boolean,
    val gpsOk: Boolean,
    val altitudeM: Double?,
    /** Net adjustment of the current press burst while the count is paused, else null. */
    val adjustingKm: Double?,
) {
    companion object {
        val EMPTY = RallyReadout(0.0, 0.0, null, null, 0L, null, true, false, null, null)
    }
}

/** What survives an app restart. The clock start is wall time, so TIME keeps running. */
data class RallySaved(val odoM: Double, val partM: Double, val startedAtMs: Long?)

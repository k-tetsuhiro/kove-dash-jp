// Added by k-tetsuhiro for kove-dash-jp (2026): rally trip meter tests.
package com.kovedash.app.rally

import com.kovedash.app.net.GpsFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RallyTripMeterTest {

    // ~111 m per 0.001° of latitude; everything runs due north from 0°, 0°.
    private val lat0 = 0.0
    private val lon0 = 0.0
    private val mPerDegLat = 111_195.0

    private fun fix(
        northM: Double,
        tSec: Int,
        speedMps: Double? = 15.0,
        bearing: Double? = 0.0,
        accuracy: Double? = 5.0,
        altitude: Double? = 100.0,
    ) = GpsFix(
        lat = lat0 + northM / mPerDegLat,
        lon = lon0,
        bearingDeg = bearing,
        speedMps = speedMps,
        accuracyMeters = accuracy,
        altitudeMeters = altitude,
        tsMillis = tSec * 1000L,
    )

    private fun ms(tSec: Int) = tSec * 1000L

    @Test
    fun countsDistanceWhileMoving() {
        val m = RallyTripMeter()
        for (t in 0..10) m.onFix(fix(t * 15.0, t), ms(t))
        val r = m.readout(ms(10))
        assertEquals(0.150, r.odoKm, 0.002)
        assertEquals(0.150, r.partKm, 0.002)
        assertEquals(54.0, r.speedKmh!!, 0.01)
    }

    @Test
    fun standingStillJitterDoesNotAddUp() {
        val m = RallyTripMeter()
        val jitter = listOf(0.0, 3.0, -2.0, 4.0, -3.0, 2.0, 0.0, 3.5, -1.0, 2.5)
        jitter.forEachIndexed { t, n -> m.onFix(fix(n, t, speedMps = 0.1), ms(t)) }
        assertEquals(0.0, m.readout(ms(10)).odoKm, 1e-9)
    }

    @Test
    fun jitterWithoutDopplerSpeedIsIgnoredToo() {
        val m = RallyTripMeter()
        val jitter = listOf(0.0, 4.0, -3.0, 5.0, -4.0)
        jitter.forEachIndexed { t, n -> m.onFix(fix(n, t, speedMps = null, accuracy = 8.0), ms(t)) }
        assertEquals(0.0, m.readout(ms(5)).odoKm, 1e-9)
    }

    @Test
    fun walkingPaceBeforeAStopIsCountedWhenMovingOff() {
        val m = RallyTripMeter()
        m.onFix(fix(0.0, 0, speedMps = 0.0), ms(0))
        // Creeping 5 m below the moving threshold, then riding off.
        m.onFix(fix(5.0, 10, speedMps = 0.5), ms(10))
        m.onFix(fix(20.0, 11, speedMps = 15.0), ms(11))
        assertEquals(0.020, m.readout(ms(11)).odoKm, 0.001)
    }

    @Test
    fun vagueFixesAreDropped() {
        val m = RallyTripMeter()
        m.onFix(fix(0.0, 0), ms(0))
        m.onFix(fix(15.0, 1, accuracy = 60.0), ms(1))
        assertEquals(0.0, m.readout(ms(1)).odoKm, 1e-9)
    }

    @Test
    fun gpsGapIsBridgedWithStraightLine() {
        val m = RallyTripMeter()
        m.onFix(fix(0.0, 0), ms(0))
        m.onFix(fix(15.0, 1), ms(1))
        assertTrue(m.readout(ms(1)).gpsOk)
        // Tunnel: nothing for 60 s.
        val lost = m.readout(ms(30))
        assertFalse(lost.gpsOk)
        assertTrue(lost.capStale)
        assertNull(lost.speedKmh)
        m.onFix(fix(915.0, 61), ms(61))
        val back = m.readout(ms(61))
        assertTrue(back.gpsOk)
        assertEquals(0.915, back.odoKm, 0.002)
    }

    @Test
    fun impossibleJumpIsRejected() {
        val m = RallyTripMeter()
        m.onFix(fix(0.0, 0), ms(0))
        m.onFix(fix(15.0, 1), ms(1))
        m.onFix(fix(2_000.0, 2), ms(2))   // 1985 m in 1 s
        assertEquals(0.015, m.readout(ms(2)).odoKm, 0.001)
    }

    @Test
    fun adjustMovesOdoAndPartAndPausesTheCount() {
        val m = RallyTripMeter()
        m.onFix(fix(0.0, 0), ms(0))
        m.onFix(fix(15.0, 1), ms(1))
        m.adjust(RallyTripMeter.ADJUST_STEP_M, ms(1))
        m.adjust(RallyTripMeter.ADJUST_STEP_M, ms(2))
        // Riding on while adjusting: the display holds still.
        m.onFix(fix(30.0, 2), ms(2))
        m.onFix(fix(45.0, 3), ms(3))
        val during = m.readout(ms(3))
        assertEquals(0.035, during.odoKm, 0.001)
        assertEquals(0.020, during.adjustingKm!!, 1e-9)
        // Two seconds after the last press the rolled distance lands.
        val after = m.readout(ms(4))
        assertNull(after.adjustingKm)
        assertEquals(0.065, after.odoKm, 0.001)
        assertEquals(0.065, after.partKm, 0.001)
    }

    @Test
    fun adjustDownNeverGoesNegative() {
        val m = RallyTripMeter()
        m.adjust(-RallyTripMeter.ADJUST_STEP_M, ms(0))
        assertEquals(0.0, m.readout(ms(0)).odoKm, 1e-9)
    }

    @Test
    fun resetPartKeepsOdo() {
        val m = RallyTripMeter()
        for (t in 0..4) m.onFix(fix(t * 15.0, t), ms(t))
        m.resetPart(ms(4))
        m.onFix(fix(75.0, 5), ms(5))
        val r = m.readout(ms(5))
        assertEquals(0.075, r.odoKm, 0.001)
        assertEquals(0.015, r.partKm, 0.001)
    }

    @Test
    fun clockStartsOnFirstMovementAndAverageIncludesStops() {
        val m = RallyTripMeter()
        m.onFix(fix(0.0, 0, speedMps = 0.0), ms(0))
        assertEquals(0L, m.readout(ms(5)).elapsedMs)
        // 360 m in 24 s from t=10, then stopped until t=71: the stop counts toward the average.
        for (t in 10..34) m.onFix(fix((t - 10) * 15.0, t), ms(t))
        val r = m.readout(ms(71))
        assertEquals(61_000L, r.elapsedMs)
        assertEquals(0.360 / (61.0 / 3600.0), r.avgKmh!!, 0.2)
    }

    @Test
    fun resetAllStopsTheClock() {
        val m = RallyTripMeter()
        for (t in 0..20) m.onFix(fix(t * 15.0, t), ms(t))
        m.resetAll(ms(20))
        val r = m.readout(ms(25))
        assertEquals(0.0, r.odoKm, 1e-9)
        assertEquals(0L, r.elapsedMs)
        assertNull(r.avgKmh)
    }

    @Test
    fun capFollowsCourseOnlyAtSpeed() {
        val m = RallyTripMeter()
        m.onFix(fix(0.0, 0, speedMps = 15.0, bearing = 245.0), ms(0))
        m.onFix(fix(1.0, 1, speedMps = 1.0, bearing = 10.0), ms(1))
        val r = m.readout(ms(1))
        assertEquals(245.0, r.capDeg!!, 1e-9)
        assertFalse(r.capStale)
        // Stopped long enough and the held heading goes stale.
        m.onFix(fix(1.0, 20, speedMps = 0.0, bearing = null), ms(20))
        assertTrue(m.readout(ms(20)).capStale)
    }

    @Test
    fun savedStateRoundTrips() {
        val m = RallyTripMeter()
        for (t in 0..10) m.onFix(fix(t * 15.0, t), ms(t))
        val s = m.saved()
        val m2 = RallyTripMeter().apply { restore(s) }
        // The clock started on the first movement (t=1) and keeps running across the restore.
        val r = m2.readout(ms(70))
        assertEquals(0.150, r.odoKm, 0.002)
        assertEquals(69_000L, r.elapsedMs)
    }
}

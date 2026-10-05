// Added by k-tetsuhiro for kove-dash-jp (2026): app-scoped rally trip meter.
package com.kovedash.app.rally

import android.content.Context
import android.content.SharedPreferences
import com.kovedash.app.AppHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the one [RallyTripMeter] for the app process. It counts from [AppHost.gps] whatever
 * the dash is showing — map, rally or nothing — so switching screens or stopping projection
 * never loses distance. State is saved as it changes so a crash or restart mid-stage comes
 * back with the same ODO / PART / clock.
 *
 * Everything runs on the main thread (GPS callbacks already land there), so the meter needs
 * no locking.
 */
object RallyHost {

    private val meter = RallyTripMeter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var prefs: SharedPreferences? = null
    private var lastSaved: RallySaved? = null

    private val _readout = MutableStateFlow(RallyReadout.EMPTY)
    val readout: StateFlow<RallyReadout> = _readout

    fun start(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        if (p.contains(KEY_ODO)) {
            val s = RallySaved(
                odoM = p.getFloat(KEY_ODO, 0f).toDouble(),
                partM = p.getFloat(KEY_PART, 0f).toDouble(),
                startedAtMs = p.getLong(KEY_STARTED, 0L).takeIf { it > 0L },
            )
            meter.restore(s)
            lastSaved = s
        }
        scope.launch {
            AppHost.gps.collect { fix ->
                if (fix != null) {
                    meter.onFix(fix, now())
                    publish()
                }
            }
        }
        // Between fixes: the clock, GPS-lost and the end of an adjust pause still move.
        scope.launch {
            while (true) {
                delay(TICK_MS)
                publish()
            }
        }
    }

    fun adjust(deltaM: Double) {
        meter.adjust(deltaM, now())
        publish()
    }

    fun resetPart() {
        meter.resetPart(now())
        publish()
    }

    fun resetAll() {
        meter.resetAll(now())
        publish()
    }

    private fun publish() {
        _readout.value = meter.readout(now())
        save()
    }

    private fun save() {
        val s = meter.saved()
        val last = lastSaved
        // ~1 m resolution is plenty; skips a write on every tick while parked.
        if (last != null && last.startedAtMs == s.startedAtMs &&
            kotlin.math.abs(last.odoM - s.odoM) < 1.0 && kotlin.math.abs(last.partM - s.partM) < 1.0
        ) return
        lastSaved = s
        prefs?.edit()
            ?.putFloat(KEY_ODO, s.odoM.toFloat())
            ?.putFloat(KEY_PART, s.partM.toFloat())
            ?.putLong(KEY_STARTED, s.startedAtMs ?: 0L)
            ?.apply()
    }

    private fun now() = System.currentTimeMillis()

    private const val TICK_MS = 500L
    private const val PREFS = "kovedash.rally"
    private const val KEY_ODO = "odo_m"
    private const val KEY_PART = "part_m"
    private const val KEY_STARTED = "started_at_ms"
}

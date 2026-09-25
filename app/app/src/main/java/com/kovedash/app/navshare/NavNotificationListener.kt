package com.kovedash.app.navshare

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.kovedash.app.BuildConfig
import java.io.File

/**
 * Reads Google Maps' ongoing navigation notification and forwards each turn to the dash
 * over BLE (via [NavForwarder] → DashService). The Garmin/Gadgetbridge model: Google Maps
 * does all routing; we're a relay. Requires the user to grant Notification Access
 * (Settings → Notification access) — see AppHost.openNotificationAccessSettings().
 *
 * The maneuver comes from the text when it names one, else from the arrow icon (large icon)
 * via [IconAwareManeuverClassifier] — see ArrowIcon for why Japanese intersections need it.
 */
class NavNotificationListener : NotificationListenerService() {

    private val classifier: ManeuverClassifier by lazy {
        IconAwareManeuverClassifier(TextManeuverClassifier(), IconManeuverMemory(PrefsIconStorage(this)))
    }

    // Debug builds dump each distinct arrow once (hash-named PNG) to calibrate ArrowIcon's lean
    // thresholds against real Maps glyphs: adb pull …/files/maneuver_icons.
    private val dumpedHashes = HashSet<String>()

    // The StatusBarNotification key of the active Maps nav notification, so we can match
    // its removal (nav ended) precisely rather than tearing down on any Maps notification.
    @Volatile
    private var activeNavKey: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS_PKG) return
        val n = sbn.notification
        val ongoing = n != null && (n.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
        val localOnly = n != null && (n.flags and android.app.Notification.FLAG_LOCAL_ONLY) != 0
        val title = n?.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)
        val text = n?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)
        val subText = n?.extras?.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)
        // subText carries the trip-level values (distance to destination + time remaining); log
        // it (and, in debug, the full extras key set) so a real ride confirms Maps' exact format.
        Log.i(TAG, "navshare RX maps notif: ongoing=$ongoing localOnly=$localOnly " +
            "title='$title' text='$text' subText='$subText'")
        if (BuildConfig.DEBUG && n != null) {
            Log.d(TAG, "navshare notif extras: ${n.extras.keySet().joinToString(",")}")
        }
        if (n == null || !ongoing || !localOnly) return
        val icon = readArrow(n)
        if (icon == null) {
            Log.i(TAG, "navshare icon: none readable")
        } else {
            Log.i(TAG, "navshare icon: hash=${icon.hashHex.take(16)}… lean=${"%.2f".format(icon.lean)} " +
                "guess=${icon.geometricGuess()}")
        }
        val update = NavNotificationParser.parse(n.extras, classifier, icon)
        if (update == null) {
            Log.i(TAG, "navshare: parse=null (no usable maneuver/distance) — skipping")
            return
        }
        activeNavKey = sbn.key
        Log.i(TAG, "navshare parsed: ${update.maneuver} road='${update.nextRoad}' " +
            "dist=${update.distanceToTurnMeters}m destM=${update.distanceToDestinationMeters} " +
            "remainS=${update.remainingTimeSec}")
        NavForwarder.onUpdate(applicationContext, update)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS_PKG) return
        if (activeNavKey != null && sbn.key == activeNavKey) {
            activeNavKey = null
            NavForwarder.onEnded(applicationContext)
        }
    }

    override fun onListenerConnected() {
        Log.i(TAG, "navshare listener connected")
        instance = this
        // Resume an already-running navigation (e.g. after a listener rebind).
        runCatching {
            activeNotifications
                ?.firstOrNull { isMapsNav(it) }
                ?.let { sbn ->
                    val n = sbn.notification
                    NavNotificationParser.parse(n.extras, classifier, readArrow(n))?.let { update ->
                        activeNavKey = sbn.key
                        NavForwarder.onUpdate(applicationContext, update)
                    }
                }
        }
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "navshare listener disconnected")
        activeNavKey = null
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * The notification's large icon — Maps' maneuver arrow — rasterized and reduced to an
     * [ArrowIcon]. Null when there's no icon or it can't be drawn; the caller then relies on text.
     */
    private fun readArrow(n: Notification): ArrowIcon? = runCatching {
        @Suppress("DEPRECATION")
        val drawable: Drawable = n.getLargeIcon()?.loadDrawable(this)
            ?: (n.extras.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap)?.let { BitmapDrawable(resources, it) }
            ?: return null
        val w = drawable.intrinsicWidth.let { if (it in 1..ICON_MAX_PX) it else ICON_MAX_PX }
        val h = drawable.intrinsicHeight.let { if (it in 1..ICON_MAX_PX) it else ICON_MAX_PX }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bmp))
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val icon = ArrowIcon.fromArgb(px, w, h)
        if (BuildConfig.DEBUG && icon != null && dumpedHashes.add(icon.hashHex)) dumpIcon(bmp, icon)
        bmp.recycle()
        icon
    }.onFailure { Log.w(TAG, "navshare icon read failed", it) }.getOrNull()

    private fun dumpIcon(bmp: Bitmap, icon: ArrowIcon) = runCatching {
        val dir = File(getExternalFilesDir(null), "maneuver_icons").apply { mkdirs() }
        val f = File(dir, "${icon.hashHex.take(16)}_lean${"%.2f".format(icon.lean)}.png")
        if (!f.exists()) f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Google Maps + ongoing + local-only = the navigation notification (Gadgetbridge's filter). */
    private fun isMapsNav(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != MAPS_PKG) return false
        val n = sbn.notification ?: return false
        val ongoing = (n.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
        return ongoing && n.flags and android.app.Notification.FLAG_LOCAL_ONLY != 0
    }

    companion object {
        private const val TAG = "KoveDash"
        private const val MAPS_PKG = "com.google.android.apps.maps"
        private const val ICON_MAX_PX = 128

        // Live listener instance, so a full app quit can release it. The system binds a
        // NotificationListenerService PERSISTENTLY and RESPAWNS the process whenever it dies —
        // so killing the process on "Disconnect" isn't enough; the OS immediately rebinds and
        // brings us back. requestUnbind() releases that hold so the process can truly die and
        // STAY dead until the app is launched again (which re-binds via [requestRebindNow]).
        @Volatile
        private var instance: NavNotificationListener? = null

        /** Release the system's hold on our listener so a killed process won't be respawned. */
        fun releaseForQuit() {
            runCatching {
                instance?.requestUnbind()
                Log.i(TAG, "navshare listener requestUnbind() for full quit")
            }
        }

        /** Re-bind the listener on app launch (after a prior [releaseForQuit]). Best-effort. */
        fun requestRebindNow(ctx: android.content.Context) {
            runCatching {
                requestRebind(android.content.ComponentName(ctx, NavNotificationListener::class.java))
            }
        }
    }
}

/** [IconManeuverMemory] persistence: one SharedPreferences entry per learned icon hash. */
private class PrefsIconStorage(ctx: Context) : IconManeuverMemory.Storage {
    private val prefs = ctx.applicationContext.getSharedPreferences("icon_maneuvers", Context.MODE_PRIVATE)

    override fun load(): Map<String, String> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    override fun save(entries: Map<String, String>) {
        prefs.edit().clear().apply { entries.forEach { (k, v) -> putString(k, v) } }.apply()
    }
}

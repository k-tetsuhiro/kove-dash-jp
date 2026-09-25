// Modified by k-tetsuhiro for kove-dash-jp (2026): draw and tap-select route preview candidates.
package com.kovedash.app.ui.dash

import android.graphics.Bitmap
import android.view.Gravity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CoordinateBounds
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.ScreenBox
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.localization.localizeLabels
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.layers.properties.generated.SymbolPlacement
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.locationcomponent.LocationComponentConstants
import com.mapbox.maps.plugin.locationcomponent.location
import com.kovedash.app.AppHost
import com.kovedash.app.nav.ActiveRoute
import com.kovedash.app.nav.GpxCourse
import com.kovedash.app.nav.Navigator
import com.kovedash.app.nav.RoutePreview
import com.kovedash.app.nav.cumulativeMeters
import com.kovedash.app.ui.theme.AppColors
import com.kovedash.app.ui.theme.KoveColors
import com.kovedash.app.ui.theme.KoveFonts
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Mapbox MapView wrapped for both the dash Presentation and the in-app tab. Reads
 * [AppHost.gps] for phone GPS fixes and eases the camera to follow.
 *
 * [keepAlive] enables a tiny corner pip that toggles every 33ms. On the dash, the
 * H.264 encoder needs continuous pixel deltas — Mapbox's render thread idles when
 * the map is visually static, which would starve the encoder and time the dash
 * decoder out. The pip forces SurfaceFlinger to keep compositing fresh frames into
 * the encoder's input Surface. On the in-app tab Compose drives invalidation
 * naturally, so the pip stays off.
 */
@Composable
fun NavMap(
    modifier: Modifier = Modifier,
    keepAlive: Boolean = false,
    autoFollow: Boolean = false,
    // How much of the map's bottom edge the caller's bottom sheet covers. The in-app
    // overlays inset by this so they don't end up hidden underneath it.
    overlayBottomInset: Dp = 16.dp,
    // True for the phone's main map: its zoom (pinch, recenter, course fit) becomes the
    // dash map's zoom via AppHost.mapZoom. Off for route preview, whose fit-to-route zoom is
    // a phone-only look at alternatives.
    publishZoom: Boolean = false,
) {
    // collectAsState (not collectAsStateWithLifecycle) because the dash Presentation's
    // lifecycle is manually driven and can stall mid-ride — observed symptom: map froze
    // after a few seconds of riding. Composition lifetime is the correct scope here; the
    // composition is alive exactly when the dash Surface is alive, which is what we want.
    val gpsFix by AppHost.gps.collectAsState()
    val activeRoute by Navigator.activeRoute.collectAsState()
    val gpxCourse by Navigator.gpxCourse.collectAsState()
    val preview by Navigator.preview.collectAsState()
    // Route previews are an in-app affair: the dash keeps showing the live route (if any)
    // while the rider compares alternatives on the phone.
    val shownPreview = if (keepAlive) null else preview
    val dashView by AppHost.dashView.collectAsState()
    val sharedZoom by AppHost.mapZoom.collectAsState()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var snappedToFirstFix by remember { mutableStateOf(false) }
    // Inside the dash Presentation, LocalLifecycleOwner.current resolves to the
    // DashPresentation (it sets ViewTreeLifecycleOwner on its decor view) — which
    // stays RESUMED until dismiss(). In the in-app tab it's the host Activity.
    // Either way, stamp this owner on the MapView before attach so Mapbox's
    // ViewTreeLifecycleOwner lookup can't race onAttachedToWindow and fall through
    // to a parent owner that drops to STOPPED when the user switches apps.
    val lifecycleOwner = LocalLifecycleOwner.current

    // Camera follow policy:
    //   - First fix always snaps the camera so the map opens centered on the rider.
    //   - After that, [autoFollow] decides. The dash side passes true (no manual pan
    //     possible — the rider needs the camera locked). The in-app map passes false so
    //     the user can pan freely; the recenter button is the explicit return path.
    //   - Bearing only updates when GPS course is reliable (speed >= threshold) so the
    //     map doesn't spin at stoplights. Pitch isn't touched after the first snap so
    //     a two-finger tilt persists.
    LaunchedEffect(gpsFix) {
        val fix = gpsFix ?: return@LaunchedEffect
        val mv = mapView ?: return@LaunchedEffect
        val movingFastEnough = (fix.speedMps ?: 0.0) >= MIN_BEARING_SPEED_MPS
        // Auto-tilt: on a 3D view the pitch tracks speed — flat/top-down when stopped
        // (so you see the surrounding area), tilting up to the view's full angle by
        // cruising speed. NAV_2D (pitch 0) stays flat.
        val pitch = targetPitch(dashView.pitch, fix.speedMps)
        if (!snappedToFirstFix) {
            val camBuilder = CameraOptions.Builder()
                .center(Point.fromLngLat(fix.lon, fix.lat))
                .zoom(if (keepAlive) sharedZoom else AppHost.DEFAULT_MAP_ZOOM)
                .pitch(pitch)
            if (keepAlive) camBuilder.padding(riderLowPadding(mv))
            if (movingFastEnough) fix.bearingDeg?.let { camBuilder.bearing(it) }
            else camBuilder.bearing(0.0)
            mv.mapboxMap.setCamera(camBuilder.build())
            snappedToFirstFix = true
            return@LaunchedEffect
        }
        if (!autoFollow) return@LaunchedEffect
        // Zoom rides along with each follow step so this ease doesn't cancel a zoom change
        // mid-animation (easeTo replaces any running camera animation).
        val camBuilder = CameraOptions.Builder()
            .center(Point.fromLngLat(fix.lon, fix.lat))
            .pitch(pitch)
            .zoom(sharedZoom)
            .padding(riderLowPadding(mv))
        if (movingFastEnough) fix.bearingDeg?.let { camBuilder.bearing(it) }
        mv.mapboxMap.easeTo(camBuilder.build(), MapAnimationOptions.mapAnimationOptions { duration(900L) })
    }

    // Route polyline: re-render whenever the active route changes (set / cleared / new
    // destination). `getStyle` defers until the style is loaded, so we don't race the
    // initial loadStyle call in the AndroidView factory.
    // While previewing, the in-app map swaps the live route for the candidates.
    LaunchedEffect(activeRoute, shownPreview, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        mv.mapboxMap.getStyle { style ->
            renderRouteLine(style, if (shownPreview != null) null else activeRoute)
            renderPreviewLines(style, shownPreview)
        }
    }

    // Fit the camera to all candidates whenever a new set arrives (not on selection
    // change — tapping between alternatives shouldn't make the map jump). Flat and
    // north-up, like Google Maps' route overview.
    LaunchedEffect(shownPreview?.routes, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        val routes = shownPreview?.routes.orEmpty()
        if (routes.isEmpty()) return@LaunchedEffect
        delay(150)
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        routes.forEach { r ->
            r.coords.forEach {
                minLat = minOf(minLat, it.latitude()); maxLat = maxOf(maxLat, it.latitude())
                minLon = minOf(minLon, it.longitude()); maxLon = maxOf(maxLon, it.longitude())
            }
        }
        val cam = mv.mapboxMap.cameraForCoordinateBounds(
            CoordinateBounds(Point.fromLngLat(minLon, minLat), Point.fromLngLat(maxLon, maxLat)),
            EdgeInsets(70.0, 50.0, 70.0, 50.0), 0.0, 0.0,
        )
        mv.mapboxMap.easeTo(cam, MapAnimationOptions.mapAnimationOptions { duration(700L) })
    }

    // GPX course line (adventure mode). Re-render on load/clear. On the in-app map
    // (!autoFollow) also fit the camera to the whole course so the rider can preview it;
    // on the dash the GPS-follow camera stays in charge.
    LaunchedEffect(gpxCourse, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        mv.mapboxMap.getStyle { style -> renderGpxLine(style, gpxCourse) }
        val course = gpxCourse ?: return@LaunchedEffect
        if (autoFollow || course.coords.size < 2) return@LaunchedEffect
        // Let the MapView finish laying out before fitting — cameraForCoordinateBounds
        // needs a measured viewport, and computing it mid-layout (e.g. the map still
        // re-rendering after a reconnect) yields a wildly wrong zoom.
        delay(250)
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        course.coords.forEach {
            minLat = minOf(minLat, it.latitude()); maxLat = maxOf(maxLat, it.latitude())
            minLon = minOf(minLon, it.longitude()); maxLon = maxOf(maxLon, it.longitude())
        }
        val bounds = CoordinateBounds(
            Point.fromLngLat(minLon, minLat),
            Point.fromLngLat(maxLon, maxLat),
        )
        val cam = mv.mapboxMap.cameraForCoordinateBounds(
            bounds, EdgeInsets(80.0, 60.0, 80.0, 60.0), null, null,
        )
        mv.mapboxMap.easeTo(cam, MapAnimationOptions.mapAnimationOptions { duration(700L) })
    }

    // View change (rider cycled style/pitch): reload the new style, re-apply the puck
    // and route line (a style swap resets style-owned layers/sources), then ease the
    // camera to the view's pitch. Skipped on first composition — the factory already
    // loaded the initial view's style.
    var appliedViewOnce by remember { mutableStateOf(false) }
    LaunchedEffect(dashView, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        if (!appliedViewOnce) { appliedViewOnce = true; return@LaunchedEffect }
        mv.mapboxMap.loadStyle(dashView.styleUri) { style ->
            style.localizeLabels(MAP_LABEL_LOCALE)
            applyPuck(mv)
            renderRouteLine(style, if (shownPreview != null) null else activeRoute)
            renderPreviewLines(style, shownPreview)
        }
        gpsFix?.let { fix ->
            mv.mapboxMap.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(fix.lon, fix.lat))
                    .pitch(targetPitch(dashView.pitch, fix.speedMps))
                    .build(),
                MapAnimationOptions.mapAnimationOptions { duration(600L) },
            )
        }
    }

    // Phone → dash zoom link. Publisher: the phone's main map reports every camera zoom
    // change. Subscriber: the dash map eases to it; the effect restarts on each change, so the
    // delay debounces a pinch's per-frame stream into one ease once the fingers settle.
    DisposableEffect(mapView, publishZoom) {
        val mv = mapView
        val sub = if (mv != null && publishZoom && !keepAlive) {
            mv.mapboxMap.subscribeCameraChanged { AppHost.setMapZoom(it.cameraState.zoom) }
        } else null
        onDispose { sub?.cancel() }
    }
    LaunchedEffect(sharedZoom, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        if (!keepAlive || !snappedToFirstFix) return@LaunchedEffect
        delay(ZOOM_SYNC_DEBOUNCE_MS)
        mv.mapboxMap.easeTo(
            CameraOptions.Builder().zoom(sharedZoom).build(),
            MapAnimationOptions.mapAnimationOptions { duration(400L) },
        )
    }

    // Map ornaments. Mapbox's wordmark + (i) attribution must stay visible (Mapbox ToS), but their default
    // bottom-left spot sits under the phone UI's sheet / landscape side panel. Park them
    // bottom-right, just left of the recenter button, riding the same inset as it. The dash
    // projection (keepAlive) has no phone chrome and keeps the defaults.
    val density = LocalDensity.current
    LaunchedEffect(mapView, overlayBottomInset) {
        val mv = mapView ?: return@LaunchedEffect
        // Scale bar: this build is metric-only, but Mapbox picks units from the device locale
        // (an English phone got "660 ft"). On the phone UI it also sat under the floating
        // search bar, half-hidden, so it's dropped there; the dash projection keeps it in km/m.
        mv.scalebar.updateSettings {
            isMetricUnits = true
            enabled = keepAlive
        }
        if (keepAlive) return@LaunchedEffect
        with(density) {
            // Vertically centred on the 48dp recenter button (ornaments are ~20dp tall).
            val bottom = (overlayBottomInset + 14.dp).toPx()
            val attributionEnd = (12.dp + 48.dp + 8.dp).toPx()
            mv.attribution.updateSettings {
                position = Gravity.BOTTOM or Gravity.END
                marginRight = attributionEnd
                marginBottom = bottom
            }
            mv.logo.updateSettings {
                position = Gravity.BOTTOM or Gravity.END
                marginRight = attributionEnd + 28.dp.toPx()
                marginBottom = bottom
            }
        }
    }

    // Encoder keep-alive tick — only when used in the dash projection pipeline.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(keepAlive) {
        if (!keepAlive) return@LaunchedEffect
        while (true) {
            tick++
            delay(33L)  // ~30 Hz Compose redraws to match encoder's 30 fps target
        }
    }

    Box(modifier = modifier.fillMaxSize().background(KoveColors.Void)) {
        // Dash side gets the compact maneuver banner overlaid at the top; in-app side
        // renders the full banner stacked above the map via MapTab so it doesn't
        // overlap the map's interactive surface.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).also { mv ->
                    mv.setViewTreeLifecycleOwner(lifecycleOwner)
                    // No initial camera — the first GPS fix snaps the camera (see the
                    // LaunchedEffect above). Mapbox defaults to a world view in the
                    // sub-second gap before the first fix arrives.
                    // Load the currently-selected view's style (Navigation Day, Outdoors,
                    // …). Nav Day's bold roads / low label clutter survive H.264 encoding
                    // and read at a glance; Outdoors adds terrain + trails for backcountry.
                    mv.mapboxMap.loadStyle(dashView.styleUri) { style ->
                        style.localizeLabels(MAP_LABEL_LOCALE)
                    }
                    applyPuck(mv)
                    if (!keepAlive) mv.enableAlternativeTap()
                    mapView = mv
                }
            },
        )
        if (keepAlive) {
            FrameKeepAlivePip(tick = tick)
            // In full projection the dash keeps its own status bars (top clock, bottom
            // fuel/gear) composited over our frame — so anything at the extreme top or
            // bottom is hidden behind them. Inset all overlays into the central band the
            // dash actually shows. Values are eyeballed against the 1280×640 panel; tune
            // DASH_SAFE_* if the dash chrome covers more/less on your unit.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = DASH_SAFE_TOP,
                        bottom = DASH_SAFE_BOTTOM,
                        start = DASH_SAFE_SIDE,
                        end = DASH_SAFE_SIDE,
                    ),
            ) {
                // Directions turn-by-turn HUD (shows only with a computed route).
                ManeuverBanner(
                    modifier = Modifier.align(Alignment.TopCenter),
                    compact = true,
                )
                // Big upcoming-turn arrow where the speed HUD used to be (the dash's own
                // speedo already reads km/h). ETA/distance-remaining gets its own corner so
                // it persists when the turn banner is hidden between maneuvers.
                TurnArrow(modifier = Modifier.align(Alignment.BottomStart))
                DashTripHud(modifier = Modifier.align(Alignment.BottomEnd))
                // GPX course-following HUD (shows only with a loaded course). Mutually
                // exclusive with the Directions HUD above — you follow one or the other.
                OffCourseBanner(modifier = Modifier.align(Alignment.TopCenter))
                CourseHud(modifier = Modifier.align(Alignment.BottomEnd))
            }
        }
        if (!keepAlive) {
            RecenterButton(
                bottomInset = overlayBottomInset,
                enabled = gpsFix != null,
                onClick = {
                    val fix = gpsFix ?: return@RecenterButton
                    val mv = mapView ?: return@RecenterButton
                    val movingFastEnough = (fix.speedMps ?: 0.0) >= MIN_BEARING_SPEED_MPS
                    val camBuilder = CameraOptions.Builder()
                        .center(Point.fromLngLat(fix.lon, fix.lat))
                        .zoom(AppHost.DEFAULT_MAP_ZOOM)
                    if (movingFastEnough) fix.bearingDeg?.let { camBuilder.bearing(it) }
                    // Pitch intentionally omitted — keep whatever tilt the user gestured to.
                    mv.mapboxMap.easeTo(
                        camBuilder.build(),
                        MapAnimationOptions.mapAnimationOptions { duration(600L) },
                    )
                },
            )
            // Course-following HUD on the in-app map too, so progress + off-course are
            // visible without projecting. Off-course banner spans the top; the readout
            // sits mid-right, clear of both the banner and the bottom buttons.
            OffCourseBanner(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
            CourseHud(modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp))
        }
    }
}

/**
 * Loads a GPX course (opens the file picker) or, when one is loaded, shows its name in
 * rally orange and clears it on tap. In-app map only.
 */
/**
 * Cycles the map view (style + pitch). Lives on the in-app map so the rider can preview
 * and select a view before/around a ride; a bike button can call the same
 * [AppHost.cycleDashView] once we confirm the wire event.
 */
@Composable
private fun BoxScope.RecenterButton(bottomInset: Dp, enabled: Boolean, onClick: () -> Unit) {
    // Maps' own treatment: a white button carrying a blue arrow, not a blue button.
    val tint = if (enabled) AppColors.Blue else AppColors.Ink3
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 12.dp, bottom = bottomInset)
            .size(48.dp)
            .shadow(2.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(AppColors.Surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w, h)
                lineTo(w / 2f, h * 0.65f)
                lineTo(0f, h)
                close()
            }
            drawPath(path, color = tint)
        }
    }
}

@Composable
private fun FrameKeepAlivePip(tick: Long) {
    val on = (tick and 1L) == 0L
    Box(
        modifier = Modifier
            .padding(10.dp)
            .size(8.dp)
            .background(if (on) KoveColors.Mint else Color.Transparent),
    )
}

// Map label language. Mapbox styles default to name_en where a feature has one, which
// renders Japanese POIs transliterated ("Seven Eleven Nerima Kasugacho 4-chome") while
// ones without an English name stay Japanese — a mix that reads worse than either. This
// pins every symbol layer to name_ja (Mapbox Streets v8 carries it), falling back to the
// local name. Hardcoded rather than Locale.getDefault(): this fork is Japan-specific,
// same call as the metric-only unit handling.
private val MAP_LABEL_LOCALE = java.util.Locale.JAPANESE

/**
 * Dash camera padding that puts the rider a third of the way up from the bottom instead of
 * dead centre (the camera centres on the padded area): more road ahead, and the rider
 * always in the same spot. Only autoFollow maps call this — the dash — and the dash map is
 * sized to the fixed 1280×640 panel, so this stays well above the bottom fuel/gear bar.
 */
private fun riderLowPadding(mv: MapView): EdgeInsets =
    EdgeInsets(mv.height * RIDER_OFFSET_FRACTION, 0.0, 0.0, 0.0)

// Top padding as a fraction of map height: 1/3 → the rider sits at 2/3 down the frame.
private const val RIDER_OFFSET_FRACTION = 1.0 / 3.0

// Quiet period before the dash applies a phone zoom change (a pinch streams every frame).
private const val ZOOM_SYNC_DEBOUNCE_MS = 150L

// Speed threshold below which GPS bearing readings are too noisy to trust. ~5.4 km/h.
private const val MIN_BEARING_SPEED_MPS = 1.5

// Auto-tilt speed band: below TILT_MIN the camera is flat (top-down); at/above TILT_FULL
// it's at the view's full pitch; linear between. ~7 km/h → ~40 km/h.
private const val TILT_MIN_MPS = 2.0
private const val TILT_FULL_MPS = 11.0

/**
 * Camera pitch for the current speed. Zero for a flat view (maxPitch 0) or when stopped;
 * ramps to [maxPitch] by cruising speed. Keeps the map readable as an overview at a stop
 * and gives the 3D perspective once moving.
 */
private fun targetPitch(maxPitch: Double, speedMps: Double?): Double {
    if (maxPitch <= 0.0) return 0.0
    val s = speedMps ?: 0.0
    val f = ((s - TILT_MIN_MPS) / (TILT_FULL_MPS - TILT_MIN_MPS)).coerceIn(0.0, 1.0)
    return maxPitch * f
}

// Safe-area insets for dash overlays (dp; the dash panel is 1280×640 @ density 2.0, so
// 640×320 dp). The dash composites its own top clock bar and bottom fuel/gear bar over
// our frame in full projection; these keep our banner + speed HUD in the visible band.
private val DASH_SAFE_TOP = 44.dp
private val DASH_SAFE_BOTTOM = 60.dp
private val DASH_SAFE_SIDE = 20.dp

/**
 * (Re)applies the standard bearing-arrow location puck. Called on first load and after
 * every style swap, since loading a new style resets style-owned config.
 */
private fun applyPuck(mv: MapView) {
    mv.location.updateSettings {
        enabled = true
        pulsingEnabled = false
        locationPuck = riderPuck(mv.context.resources.displayMetrics.density)
        puckBearing = PuckBearing.COURSE
        puckBearingEnabled = true
    }
}

/**
 * The rider marker: a red navigation chevron with a white rim over a soft dark halo. The
 * stock puck was a blue dot — the same blue as the route line — and once the dash's H.264
 * encode softened the edges it vanished into the route. A different shape (points the way
 * you're heading), a different hue and a rim/halo that separates it from any background
 * keep it findable at a glance. The whole indicator rotates with the course bearing.
 */
private fun riderPuck(density: Float): LocationPuck2D {
    val px = (PUCK_SIZE_DP * density).roundToInt()
    val chevron = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { bmp ->
        val c = android.graphics.Canvas(bmp)
        val w = px.toFloat()
        val path = android.graphics.Path().apply {
            moveTo(w * 0.50f, w * 0.10f)
            lineTo(w * 0.84f, w * 0.86f)
            lineTo(w * 0.50f, w * 0.68f)
            lineTo(w * 0.16f, w * 0.86f)
            close()
        }
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = PUCK_COLOR
        }
        val rim = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2.5f * density
            strokeJoin = android.graphics.Paint.Join.ROUND
            color = android.graphics.Color.WHITE
        }
        c.drawPath(path, fill)
        c.drawPath(path, rim)
    }
    val halo = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { bmp ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                px / 2f, px / 2f, px / 2f,
                intArrayOf(0x66000000, 0x33000000, 0x00000000),
                floatArrayOf(0f, 0.6f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        android.graphics.Canvas(bmp).drawCircle(px / 2f, px / 2f, px / 2f, paint)
    }
    return LocationPuck2D(
        topImage = ImageHolder.from(chevron),
        shadowImage = ImageHolder.from(halo),
    )
}

// ~1.6× the stock 22dp dot, so it survives the dash's video encode.
private const val PUCK_SIZE_DP = 36f
private val PUCK_COLOR = android.graphics.Color.parseColor("#E53935")

/** Add [layer] under the rider puck when it's on the map, so no line ever covers the rider. */
private fun com.mapbox.maps.Style.addLayerBelowPuck(layer: com.mapbox.maps.extension.style.layers.Layer) {
    if (styleLayerExists(LocationComponentConstants.LOCATION_INDICATOR_LAYER)) {
        addLayerBelow(layer, LocationComponentConstants.LOCATION_INDICATOR_LAYER)
    } else {
        addLayer(layer)
    }
}

private const val ROUTE_SOURCE_ID = "kove.route.src"
private const val ROUTE_LAYER_ID = "kove.route.layer"
private const val GPX_SOURCE_ID = "kove.gpx.src"
private const val GPX_LAYER_ID = "kove.gpx.layer"

/**
 * Upsert the loaded GPX course polyline. Drawn in rally orange, distinct from the blue
 * Directions route so a loaded course and a computed route don't blend. Same
 * update-in-place pattern as [renderRouteLine]; a null course removes it.
 */
private fun renderGpxLine(style: com.mapbox.maps.Style, course: GpxCourse?) {
    if (course == null || course.coords.size < 2) {
        style.removeStyleLayer(GPX_LAYER_ID)
        style.removeStyleSource(GPX_SOURCE_ID)
        return
    }
    val feature = Feature.fromGeometry(LineString.fromLngLats(course.coords))
    val existing = style.getSourceAs<GeoJsonSource>(GPX_SOURCE_ID)
    if (existing != null) {
        existing.feature(feature)
        return
    }
    style.addSource(geoJsonSource(GPX_SOURCE_ID) { feature(feature) })
    style.addLayerBelowPuck(
        lineLayer(GPX_LAYER_ID, GPX_SOURCE_ID) {
            lineColor("#FF6D00")
            lineWidth(7.0)
            lineOpacity(0.9)
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
        }
    )
}

/**
 * Upsert the route polyline onto [style]. Updates the existing GeoJsonSource feature in
 * place when present (no flicker, no layer re-add), or creates source + layer on first
 * render. Passing a null route removes both — the polyline disappears cleanly.
 */
private fun renderRouteLine(style: com.mapbox.maps.Style, route: ActiveRoute?) {
    if (route == null || route.coords.size < 2) {
        style.removeStyleLayer(ROUTE_LAYER_ID)
        style.removeStyleSource(ROUTE_SOURCE_ID)
        return
    }
    val feature = Feature.fromGeometry(LineString.fromLngLats(route.coords))
    val existing = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
    if (existing != null) {
        existing.feature(feature)
        return
    }
    style.addSource(
        geoJsonSource(ROUTE_SOURCE_ID) {
            feature(feature)
        }
    )
    style.addLayerBelowPuck(
        lineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID) {
            lineColor("#1E88E5")
            lineWidth(9.0)
            lineOpacity(0.9)
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
        }
    )
}

private const val PREVIEW_ALT_SOURCE_ID = "kove.preview.alt.src"
private const val PREVIEW_ALT_LAYER_ID = "kove.preview.alt.layer"
private const val PREVIEW_ALT_CASING_LAYER_ID = "kove.preview.alt.casing"
private const val PREVIEW_SEL_SOURCE_ID = "kove.preview.sel.src"
private const val PREVIEW_SEL_LAYER_ID = "kove.preview.sel.layer"
private const val PREVIEW_SEL_CASING_LAYER_ID = "kove.preview.sel.casing"
private const val PREVIEW_LABEL_SOURCE_ID = "kove.preview.label.src"
private const val PREVIEW_LABEL_LAYER_ID = "kove.preview.label.layer"
private const val PROP_ROUTE_INDEX = "idx"

/**
 * Tapping near a grey alternative on the in-app map selects it (Google Maps behavior).
 * The query is async, so the click is never consumed — panning etc. still work.
 */
private fun MapView.enableAlternativeTap() {
    mapboxMap.addOnMapClickListener { point ->
        val p = Navigator.preview.value
        if (p == null || p.routes.size < 2) return@addOnMapClickListener false
        val px = mapboxMap.pixelForCoordinate(point)
        val slop = 24.0
        val box = ScreenBox(ScreenCoordinate(px.x - slop, px.y - slop), ScreenCoordinate(px.x + slop, px.y + slop))
        mapboxMap.queryRenderedFeatures(
            RenderedQueryGeometry(box),
            RenderedQueryOptions(listOf(PREVIEW_ALT_LAYER_ID, PREVIEW_LABEL_LAYER_ID), null),
        ) { result ->
            val idx = result.value
                ?.firstNotNullOfOrNull { it.queriedFeature.feature.getNumberProperty(PROP_ROUTE_INDEX) }
                ?.toInt() ?: return@queryRenderedFeatures
            Navigator.selectPreviewRoute(idx)
        }
        false
    }
}

/**
 * Draws route candidates: alternatives in grey underneath, the selected one in blue on
 * top (both with a darker casing), plus a duration bubble on each. Null removes it all.
 */
private fun renderPreviewLines(style: Style, preview: RoutePreview?) {
    val layers = listOf(
        PREVIEW_LABEL_LAYER_ID, PREVIEW_SEL_LAYER_ID, PREVIEW_SEL_CASING_LAYER_ID,
        PREVIEW_ALT_LAYER_ID, PREVIEW_ALT_CASING_LAYER_ID,
    )
    val sources = listOf(PREVIEW_LABEL_SOURCE_ID, PREVIEW_SEL_SOURCE_ID, PREVIEW_ALT_SOURCE_ID)
    if (preview == null || preview.routes.isEmpty()) {
        layers.forEach { style.removeStyleLayer(it) }
        sources.forEach { style.removeStyleSource(it) }
        return
    }
    val sel = preview.selectedIndex
    val alts = FeatureCollection.fromFeatures(
        preview.routes.mapIndexedNotNull { i, r ->
            if (i == sel || r.coords.size < 2) null
            else Feature.fromGeometry(LineString.fromLngLats(r.coords)).apply { addNumberProperty(PROP_ROUTE_INDEX, i) }
        }
    )
    val selected = preview.routes[sel].coords.takeIf { it.size >= 2 }
        ?.let { FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(it))) }
        ?: FeatureCollection.fromFeatures(emptyList())
    val labels = FeatureCollection.fromFeatures(
        preview.routes.mapIndexedNotNull { i, r ->
            val at = labelPoint(r.coords) ?: return@mapIndexedNotNull null
            Feature.fromGeometry(at).apply {
                addNumberProperty(PROP_ROUTE_INDEX, i)
                addStringProperty("label", formatPreviewDuration(r.durationSeconds))
                addBooleanProperty("sel", i == sel)
            }
        }
    )

    val altSrc = style.getSourceAs<GeoJsonSource>(PREVIEW_ALT_SOURCE_ID)
    if (altSrc != null) {
        altSrc.featureCollection(alts)
        style.getSourceAs<GeoJsonSource>(PREVIEW_SEL_SOURCE_ID)?.featureCollection(selected)
        style.getSourceAs<GeoJsonSource>(PREVIEW_LABEL_SOURCE_ID)?.featureCollection(labels)
        return
    }
    style.addSource(geoJsonSource(PREVIEW_ALT_SOURCE_ID) { featureCollection(alts) })
    style.addSource(geoJsonSource(PREVIEW_SEL_SOURCE_ID) { featureCollection(selected) })
    style.addSource(geoJsonSource(PREVIEW_LABEL_SOURCE_ID) { featureCollection(labels) })
    style.addLayerBelowPuck(
        lineLayer(PREVIEW_ALT_CASING_LAYER_ID, PREVIEW_ALT_SOURCE_ID) {
            lineColor("#7C8594"); lineWidth(11.0); lineCap(LineCap.ROUND); lineJoin(LineJoin.ROUND)
        }
    )
    style.addLayerBelowPuck(
        lineLayer(PREVIEW_ALT_LAYER_ID, PREVIEW_ALT_SOURCE_ID) {
            lineColor("#B7C0CC"); lineWidth(8.0); lineCap(LineCap.ROUND); lineJoin(LineJoin.ROUND)
        }
    )
    style.addLayerBelowPuck(
        lineLayer(PREVIEW_SEL_CASING_LAYER_ID, PREVIEW_SEL_SOURCE_ID) {
            lineColor("#0D47A1"); lineWidth(12.0); lineCap(LineCap.ROUND); lineJoin(LineJoin.ROUND)
        }
    )
    style.addLayerBelowPuck(
        lineLayer(PREVIEW_SEL_LAYER_ID, PREVIEW_SEL_SOURCE_ID) {
            lineColor("#1E88E5"); lineWidth(8.0); lineCap(LineCap.ROUND); lineJoin(LineJoin.ROUND)
        }
    )
    style.addLayerBelowPuck(
        symbolLayer(PREVIEW_LABEL_LAYER_ID, PREVIEW_LABEL_SOURCE_ID) {
            symbolPlacement(SymbolPlacement.POINT)
            textField(Expression.get("label"))
            textFont(listOf("DIN Pro Bold", "Arial Unicode MS Bold"))
            textSize(14.0)
            textColor(
                Expression.switchCase(
                    Expression.get("sel"), Expression.color(android.graphics.Color.WHITE),
                    Expression.color(android.graphics.Color.parseColor("#20242B")),
                )
            )
            textHaloColor(
                Expression.switchCase(
                    Expression.get("sel"), Expression.color(android.graphics.Color.parseColor("#1E88E5")),
                    Expression.color(android.graphics.Color.WHITE),
                )
            )
            textHaloWidth(3.0)
            textAllowOverlap(true)
            textIgnorePlacement(true)
            symbolSortKey(Expression.switchCase(Expression.get("sel"), Expression.literal(1.0), Expression.literal(0.0)))
        }
    )
}

/**
 * Where to hang a route's duration bubble: halfway along it by distance. Alternatives
 * share their start and end, so the midpoint is where they're most likely to diverge.
 */
private fun labelPoint(coords: List<Point>): Point? {
    if (coords.size < 2) return null
    val cum = cumulativeMeters(coords)
    val half = cum.last() / 2
    val i = cum.indexOfFirst { it >= half }.coerceAtLeast(1)
    return coords[i]
}

/** "45 MIN" / "1 H 05" — the bubble on each preview route. */
internal fun formatPreviewDuration(seconds: Double): String {
    val totalMin = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    if (totalMin < 60) return "$totalMin MIN"
    return "%d H %02d".format(totalMin / 60, totalMin % 60)
}

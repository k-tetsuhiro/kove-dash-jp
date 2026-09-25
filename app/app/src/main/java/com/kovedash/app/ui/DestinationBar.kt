// Modified by k-tetsuhiro for kove-dash-jp (2026): Maps-style search + route picker.
package com.kovedash.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.geojson.Point
import com.kovedash.app.AppHost
import com.kovedash.app.nav.Destination
import com.kovedash.app.nav.Navigator
import com.kovedash.app.nav.PreviewStatus
import com.kovedash.app.nav.RoutePreview
import com.kovedash.app.nav.RouteStatus
import com.kovedash.app.net.MapboxDirections
import com.kovedash.app.net.MapboxGeocoder
import com.kovedash.app.ui.components.AppButton
import com.kovedash.app.ui.components.BottomSheet
import com.kovedash.app.ui.components.ButtonTone
import com.kovedash.app.ui.components.FloatingSearchBar
import com.kovedash.app.ui.components.Glyph
import com.kovedash.app.ui.components.MapPill
import com.kovedash.app.ui.components.ResultIcon
import com.kovedash.app.ui.dash.NavMap
import com.kovedash.app.ui.theme.AppColors
import com.kovedash.app.ui.theme.AppFonts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The pill floating at the top of the map — Maps' search field, doing double duty as the
 * destination display. Two states:
 *   - nothing set: "どこへ行く？" plus the settings cog on the right.
 *   - destination set: the destination's name plus a clear button, with a second pill
 *     underneath carrying route status (fetching / rerouting / distance + ETA).
 *
 * Tapping fires [onActivateSearch] so the parent can present [FullscreenSearch] over
 * everything. We don't host the text field here: in landscape the IME eats over half the
 * screen, which would collapse the map behind it.
 */
@Composable
fun DestinationBar(
    modifier: Modifier = Modifier,
    onActivateSearch: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onEasterEgg: () -> Unit = {},
) {
    val destination by Navigator.destination.collectAsState()
    val activeRoute by Navigator.activeRoute.collectAsState()
    val status by Navigator.routeStatus.collectAsState()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FloatingSearchBar(
            placeholder = "どこへ行く？",
            value = destination?.name,
            onClick = onActivateSearch,
            trailing = {
                if (destination != null) {
                    CircleIconButton(glyph = "✕", onClick = { Navigator.clearDestination() })
                } else {
                    // Long-press the cog for the "this is fine" easter egg the retro build
                    // hid on the wordmark, which no longer exists.
                    CircleIconButton(
                        glyph = "⚙",
                        onClick = onOpenSettings,
                        onLongPress = onEasterEgg,
                    )
                }
            },
        )
        val note = routeNote(status, activeRoute?.distanceMeters, activeRoute?.durationSeconds)
        if (destination != null && note != null) {
            MapPill(label = note, accent = routeNoteColor(status), onClick = onActivateSearch)
        }
    }
}

@Composable
private fun CircleIconButton(
    glyph: String,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(AppColors.Surface3)
            .then(
                if (onLongPress != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
                    }
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, AppColors.Ink2, 15.sp)
    }
}

/**
 * Full-screen destination search. Opens with the keyboard up, queries Mapbox SearchBox as
 * you type (debounced), and lists matches as Maps-style rows.
 *
 * Suggestions render in a LazyColumn so a long list scrolls cleanly above the keyboard.
 * [Modifier.imePadding] on the root keeps the list above the IME without fighting
 * `adjustResize` (the window resizes; the LazyColumn fills what's left).
 */
@Composable
fun FullscreenSearch(
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    val gpsFix by AppHost.gps.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<MapboxGeocoder.Suggestion>>(emptyList()) }
    var retrieving by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    // SearchBox sessions: one UUID covers all /suggest calls + one /retrieve in a
    // 2-minute window for billing. Regenerated each time the overlay opens. After a
    // selection successfully retrieves we close, so the next open gets a fresh token.
    val sessionToken = rememberSaveable { UUID.randomUUID().toString() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(query, gpsFix?.lat?.roundToCellKey(), gpsFix?.lon?.roundToCellKey()) {
        if (query.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = MapboxGeocoder.suggest(query, sessionToken, gpsFix)
    }

    fun select(sug: MapboxGeocoder.Suggestion) {
        if (retrieving) return
        retrieving = true
        focusManager.clearFocus()
        scope.launch {
            val r = MapboxGeocoder.retrieve(sug.mapboxId, sessionToken, sug.language)
            retrieving = false
            if (r != null) {
                Navigator.previewDestination(
                    Destination(
                        name = r.name,
                        context = r.context,
                        point = Point.fromLngLat(r.lon, r.lat),
                    )
                )
                onDone()
            }
            // On retrieve failure we stay on the overlay so the rider can try another
            // suggestion. The HTTP error already logged via Log.w.
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Surface)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        focusManager.clearFocus()
                        onDone()
                    },
                contentAlignment = Alignment.Center,
            ) { Glyph("←", AppColors.Ink, 20.sp) }
            Box(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = AppColors.Ink,
                    fontSize = 16.sp,
                    fontFamily = AppFonts.Sans,
                ),
                cursorBrush = SolidColor(AppColors.Blue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { suggestions.firstOrNull()?.let(::select) },
                ),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "どこへ行く？",
                            color = AppColors.Ink3,
                            fontFamily = AppFonts.Sans,
                            fontSize = 16.sp,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { query = "" },
                    contentAlignment = Alignment.Center,
                ) { Glyph("✕", AppColors.Ink2, 15.sp) }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppColors.Line))

        when {
            retrieving -> SearchMessage("目的地を取得しています…")
            suggestions.isEmpty() && query.length >= 2 -> SearchMessage("該当する場所がありません")
            suggestions.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(suggestions) { sug ->
                    SuggestionRow(
                        name = sug.name,
                        context = sug.context,
                        featureType = sug.featureType,
                        onClick = { select(sug) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(text = text, color = AppColors.Ink2, fontFamily = AppFonts.Sans, fontSize = 14.sp)
    }
}

@Composable
private fun SuggestionRow(name: String, context: String, featureType: String, onClick: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            ResultIcon(featureTypeGlyph(featureType))
            Box(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = name,
                    color = AppColors.Ink,
                    fontFamily = AppFonts.Sans,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (context.isNotBlank()) {
                    Text(
                        text = context,
                        color = AppColors.Ink2,
                        fontFamily = AppFonts.Sans,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 65.dp)
                .height(1.dp)
                .background(AppColors.Surface3),
        )
    }
}

/** Feature-type glyph for the result icon. Mapbox's types, as symbols Roboto carries. */
private fun featureTypeGlyph(featureType: String): String = when (featureType) {
    "poi" -> "◉"
    "address" -> "⌂"
    "place", "locality" -> "◍"
    "neighborhood" -> "◌"
    "street" -> "╱"
    else -> "◦"
}

/**
 * Full-screen route preview: the map fills everything, the destination sits in a floating
 * bar at the top, and the candidates are in a bottom sheet. Back cancels the preview (a
 * route already being navigated carries on).
 */
@Composable
fun RoutePreviewScreen(modifier: Modifier = Modifier) {
    BackHandler { Navigator.cancelPreview() }
    val preview by Navigator.preview.collectAsState()
    val options by Navigator.routeOptions.collectAsState()
    val p = preview ?: return

    Box(modifier = modifier.fillMaxSize().background(AppColors.Surface2)) {
        NavMap(
            modifier = Modifier.fillMaxSize(),
            keepAlive = false,
            autoFollow = false,
            // The route sheet is taller than the connection sheet — three route rows,
            // the avoid chips and START.
            overlayBottomInset = 260.dp,
        )

        FloatingSearchBar(
            placeholder = "",
            value = p.destination.name,
            onClick = { Navigator.cancelPreview() },
            leading = { Glyph("←", AppColors.Ink, 20.sp) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        RoutePreviewSheet(
            preview = p,
            options = options,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Route picker: one row per candidate (time leading, distance and via underneath), avoid
 * toggles, and START. Time leads because that's what a rider picks on; the retro build
 * led with distance.
 */
@Composable
private fun RoutePreviewSheet(
    preview: RoutePreview,
    options: MapboxDirections.Options,
    modifier: Modifier = Modifier,
) {
    BottomSheet(modifier = modifier) {
        when (preview.status) {
            PreviewStatus.WaitingForGps -> PreviewMessage("GPS を待っています…")
            PreviewStatus.Fetching -> PreviewMessage("ルートを探しています…")
            PreviewStatus.Error -> PreviewMessage(
                "ルートが見つかりませんでした。タップして再試行",
                color = AppColors.Red,
                onClick = { Navigator.retryPreview() },
            )
            PreviewStatus.Ready -> {
                val fastest = preview.routes.minOf { it.durationSeconds }
                preview.routes.forEachIndexed { i, r ->
                    RouteRow(
                        route = r,
                        selected = i == preview.selectedIndex,
                        isFastest = r.durationSeconds <= fastest,
                        onClick = { Navigator.selectPreviewRoute(i) },
                    )
                }
            }
        }

        Box(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            AvoidChip("高速を避ける", options.avoidMotorways) {
                Navigator.setRouteOptions(options.copy(avoidMotorways = !options.avoidMotorways))
            }
            AvoidChip("有料道路", options.avoidTolls) {
                Navigator.setRouteOptions(options.copy(avoidTolls = !options.avoidTolls))
            }
            AvoidChip("フェリー", options.avoidFerries) {
                Navigator.setRouteOptions(options.copy(avoidFerries = !options.avoidFerries))
            }
        }

        Box(Modifier.height(14.dp))
        AppButton(
            label = "開始",
            onClick = { Navigator.startPreview() },
            enabled = preview.status == PreviewStatus.Ready,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RouteRow(
    route: MapboxDirections.Route,
    selected: Boolean,
    isFastest: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) AppColors.BlueTint else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = formatDuration(route.durationSeconds),
                color = if (selected) AppColors.BluePressed else AppColors.Ink,
                fontFamily = AppFonts.Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 19.sp,
            )
            Text(
                text = buildString {
                    append(formatKm(route.distanceMeters))
                    append(" · ")
                    append(arrivalClock(route.durationSeconds))
                    append(" 着")
                    if (route.summary.isNotBlank()) append(" · ").append(route.summary)
                },
                color = AppColors.Ink2,
                fontFamily = AppFonts.Sans,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val tags = buildList {
                if (route.usesMotorway) add("高速")
                if (route.usesToll) add("有料")
                if (route.usesFerry) add("フェリー")
            }
            if (tags.isNotEmpty()) {
                Text(
                    text = tags.joinToString(" · "),
                    color = AppColors.Ink3,
                    fontFamily = AppFonts.Sans,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
        if (isFastest) {
            Box(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) AppColors.BlueTint else AppColors.Surface3)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "最速",
                    color = if (selected) AppColors.BluePressed else AppColors.Ink2,
                    fontFamily = AppFonts.Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun AvoidChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (on) AppColors.BlueTint else AppColors.Surface)
            .then(if (on) Modifier else Modifier.border(1.dp, AppColors.Line, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = if (on) AppColors.BluePressed else AppColors.Ink2,
            fontFamily = AppFonts.Sans,
            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PreviewMessage(
    text: String,
    color: Color = AppColors.Ink2,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = color, fontFamily = AppFonts.Sans, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------- formatting

/** Status line under the search bar while a destination is set. Null = nothing to say. */
private fun routeNote(
    status: RouteStatus,
    distanceMeters: Double?,
    durationSeconds: Double?,
): String? = when (status) {
    RouteStatus.Idle -> null
    RouteStatus.WaitingForGps -> "GPS を待っています…"
    RouteStatus.Fetching -> "ルートを取得中…"
    RouteStatus.Rerouting -> "ルートを再探索中…"
    RouteStatus.Error -> "ルートを取得できませんでした"
    RouteStatus.Active ->
        if (distanceMeters == null || durationSeconds == null) null
        else "${formatKm(distanceMeters)} · ${formatDuration(durationSeconds)}"
}

private fun routeNoteColor(status: RouteStatus): Color = when (status) {
    RouteStatus.Active -> AppColors.Green
    RouteStatus.Error -> AppColors.Red
    else -> AppColors.Ink2
}

private fun formatKm(meters: Double): String {
    val km = meters / 1000.0
    return if (km >= 10) "${km.roundToInt()} km" else "%.1f km".format(km)
}

private fun formatDuration(seconds: Double): String {
    val totalMin = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    if (totalMin < 60) return "$totalMin 分"
    return "${totalMin / 60} 時間 ${totalMin % 60} 分"
}

private val ARRIVAL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun arrivalClock(durationSeconds: Double): String =
    LocalTime.now().plusSeconds(durationSeconds.toLong()).format(ARRIVAL_FORMAT)

// Quantize lat/lon to a ~10m grid so trivial GPS jitter doesn't re-fire the geocoder.
private fun Double.roundToCellKey(): Long = (this * 10_000.0).toLong()

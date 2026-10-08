// Modified by k-tetsuhiro for kove-dash-jp (2026): Maps-style phone UI (design/v2-mockup.html);
// map / rally dash screen toggle and rally controls (design/rally-switch-mockup.html); Wi-Fi /
// Bluetooth link rows and the meter-screen picker in the connection sheet
// (design/connection-status-mockup.html, design/meter-screen-picker-mockup.html).
package com.kovedash.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.kovedash.app.AppHost
import com.kovedash.app.DashScreen
import com.kovedash.app.rally.RallyHost
import com.kovedash.app.rally.RallyTripMeter
import com.kovedash.app.nav.Navigator
import com.kovedash.app.nav.RouteStatus
import com.kovedash.app.service.ConnectionPhase
import com.kovedash.app.service.DashState
import com.kovedash.app.service.LinkStatus
import com.kovedash.app.ui.components.AppButton
import com.kovedash.app.ui.components.BottomSheet
import com.kovedash.app.ui.components.ButtonTone
import com.kovedash.app.ui.components.Glyph
import com.kovedash.app.ui.components.MapFab
import com.kovedash.app.ui.components.MapPill
import com.kovedash.app.ui.components.StateDot
import com.kovedash.app.ui.components.WarningBox
import com.kovedash.app.ui.dash.NavMap
import com.kovedash.app.ui.dash.arrowFor
import com.kovedash.app.ui.dash.fallbackInstruction
import com.kovedash.app.ui.dash.formatDistance
import com.kovedash.app.ui.theme.AppColors
import com.kovedash.app.ui.theme.AppFonts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The main screen: a full-bleed map with the search bar floating on top, map controls on
 * the right, and everything else folded into a bottom sheet — Google Maps' structure.
 *
 * The retro layout this replaces spent ~120dp of the top on a sponsor band, wordmark,
 * status pill and rule, then stacked action buttons along the bottom, leaving the map a
 * strip in the middle. Here the map IS the screen and the chrome sits over it.
 *
 * Landscape moves the chrome into a left side panel, as Maps does: a full-width sheet plus
 * the maneuver card ate half of a ~360dp-tall screen and couldn't be put away. The sheet also
 * collapses to one row in both orientations — on its own once the link is up, or by dragging it.
 */
@Composable
fun ConnectScreen(
    state: DashState,
    onConnect: () -> Unit,
    onProject: () -> Unit,
    onStopProjection: () -> Unit = {},
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onActivateSearch: () -> Unit = {},
    onEasterEgg: () -> Unit = {},
    onGrantNotifAccess: () -> Unit = {},
) {
    // Expanded while there's something to do (connect, watch progress, read an error);
    // collapsed once linked, where the only frequent action is start/stop projection.
    val linked = state.phase in LINKED
    var sheetExpanded by rememberSaveable { mutableStateOf(!linked) }
    LaunchedEffect(linked) { sheetExpanded = !linked }
    LaunchedEffect(state.errorMessage) { if (state.errorMessage != null) sheetExpanded = true }
    // With the dash on the rally screen, the turn-card slot carries the ODO controls instead.
    val dashScreen by AppHost.dashScreen.collectAsState()
    val showRally = linked && dashScreen == DashScreen.RALLY

    val sheet: @Composable (Modifier, Shape) -> Unit = { modifier, shape ->
        ConnectionSheet(
            state = state,
            expanded = sheetExpanded,
            onExpandedChange = { sheetExpanded = it },
            onConnect = onConnect,
            onProject = onProject,
            onStopProjection = onStopProjection,
            onDisconnect = onDisconnect,
            modifier = modifier,
            shape = shape,
        )
    }
    val topChrome: @Composable () -> Unit = {
        DestinationBar(
            modifier = Modifier.fillMaxWidth(),
            onActivateSearch = onActivateSearch,
            onOpenSettings = onOpenSettings,
            onEasterEgg = onEasterEgg,
        )
        TopNotices(state, onGrantNotifAccess)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(AppColors.Surface2)) {
        if (maxWidth > maxHeight) {
            LandscapeLayout(panelWidth = min(SIDE_PANEL_MAX, maxWidth * 0.45f), showRally, topChrome, sheet)
        } else {
            PortraitLayout(showRally, topChrome, sheet)
        }
    }
}

@Composable
private fun BoxScope.PortraitLayout(
    showRally: Boolean,
    topChrome: @Composable () -> Unit,
    sheet: @Composable (Modifier, Shape) -> Unit,
) {
    // The sheet's height changes with its state (collapsed, progress, error), so the card and
    // the map's recenter button are placed from measured heights rather than a fixed inset.
    val density = LocalDensity.current
    var sheetHeight by remember { mutableStateOf(0.dp) }
    var cardHeight by remember { mutableStateOf(0.dp) }

    NavMap(
        modifier = Modifier.fillMaxSize(),
        keepAlive = false,
        autoFollow = false,
        publishZoom = true,
        overlayBottomInset = sheetHeight + cardHeight + 16.dp,
    )

    // ---- floating chrome over the map -------------------------------------
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { topChrome() }

    MapControlRail(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 12.dp),
    )

    // Next turn, readable without looking at the bike. Its own Material card rather
    // than the dash's ManeuverBanner, which is tuned for H.264 at speed (dark panel,
    // pixel type) and would land as a foreign object in the middle of this screen.
    // The Box measures 0 when there's no route, which zeroes cardHeight.
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 12.dp, end = 12.dp, bottom = sheetHeight + 8.dp)
            .onSizeChanged { cardHeight = with(density) { it.height.toDp() } },
    ) { if (showRally) RallyControlCard() else ManeuverCard() }

    sheet(
        Modifier
            .align(Alignment.BottomCenter)
            .onSizeChanged { sheetHeight = with(density) { it.height.toDp() } },
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    )
}

@Composable
private fun BoxScope.LandscapeLayout(
    panelWidth: Dp,
    showRally: Boolean,
    topChrome: @Composable () -> Unit,
    sheet: @Composable (Modifier, Shape) -> Unit,
) {
    NavMap(
        modifier = Modifier.fillMaxSize(),
        keepAlive = false,
        autoFollow = false,
        publishZoom = true,
    )

    MapControlRail(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
            .padding(end = 12.dp),
    )

    // Search + notices up top, turn card + connection at the bottom, map everywhere else.
    // Content that outgrows the height (expanded sheet + error + notices) is clipped at the
    // gap in the middle; collapsing the sheet is the way out.
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxHeight()
            .width(panelWidth)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        topChrome()
        Spacer(Modifier.weight(1f))
        if (showRally) RallyControlCard() else ManeuverCard()
        sheet(Modifier, RoundedCornerShape(16.dp))
    }
}

@Composable
private fun TopNotices(state: DashState, onGrantNotifAccess: () -> Unit) {
    val gpxCourse by Navigator.gpxCourse.collectAsState()
    // Turn-by-turn reads Google Maps' notification, so without Notification access
    // the app's main job silently does nothing. Surfaced right under the search bar.
    if (!state.notificationAccessGranted) {
        NotifAccessPrompt(onClick = onGrantNotifAccess)
    }
    if (gpxCourse != null) {
        MapPill(
            label = gpxCourse?.name ?: "GPX コース",
            accent = AppColors.Green,
            onClick = { AppHost.clearGpxCourse() },
        )
    }
}

/**
 * Map-type switcher and GPX loader. Vertical stack on the right, where Maps puts its
 * layers and compass controls. Recenter stays inside [NavMap], which owns the camera.
 */
@Composable
private fun MapControlRail(modifier: Modifier = Modifier) {
    val dashView by AppHost.dashView.collectAsState()
    val gpxCourse by Navigator.gpxCourse.collectAsState()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        MapFab(onClick = { AppHost.cycleDashView() }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Glyph("◈", AppColors.Ink2, 15.sp)
                Text(
                    text = dashView.label.substringBefore(' '),
                    color = AppColors.Ink3,
                    fontFamily = AppFonts.Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 7.sp,
                )
            }
        }
        MapFab(
            onClick = { if (gpxCourse != null) AppHost.clearGpxCourse() else AppHost.requestGpxPick() },
        ) {
            Glyph("⤳", if (gpxCourse != null) AppColors.Green else AppColors.Ink2, 17.sp)
        }
    }
}

@Composable
private fun NotifAccessPrompt(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        WarningBox(
            text = "ターンバイターンには通知へのアクセスが必要です。タップして許可してください。",
            modifier = Modifier.fillMaxWidth(),
        )
        // WarningBox has no click of its own; this makes the whole block the target.
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        )
    }
}

/**
 * Connection state and its actions. The header says where things stand; under it, one row per
 * radio, so a stalled connect shows which half it's waiting on (design/connection-status-mockup.html).
 * Not connected: one Connect button. Working: the rows and cancel. Connected: which screen goes
 * to the meter (design/meter-screen-picker-mockup.html), show it / stop, disconnect.
 */
@Composable
private fun ConnectionSheet(
    state: DashState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onProject: () -> Unit,
    onStopProjection: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
) {
    val dashScreen by AppHost.dashScreen.collectAsState()
    BottomSheet(
        modifier = modifier,
        shape = shape,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        if (!expanded) {
            CollapsedSheetRow(state, dashScreen, onConnect, onProject, onStopProjection, onDisconnect)
            return@BottomSheet
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateDot(color = dotColor(state))
            Box(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headlineFor(state),
                    color = AppColors.Ink,
                    fontFamily = AppFonts.Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Text(
                    text = subtitleFor(state, dashScreen),
                    color = AppColors.Ink2,
                    fontFamily = AppFonts.Sans,
                    fontSize = 13.sp,
                )
            }
            if (isWorking(state.phase)) {
                AppButton(label = "キャンセル", tone = ButtonTone.Text, onClick = onDisconnect)
            }
        }

        if (state.phase != ConnectionPhase.IDLE) {
            Box(Modifier.height(14.dp))
            LinkRows(state)
        }

        ActivationNotice(state)

        if (state.errorMessage != null) {
            Box(Modifier.height(12.dp))
            WarningBox(text = state.errorMessage)
        }

        when (state.phase) {
            ConnectionPhase.IDLE, ConnectionPhase.ERROR -> {
                Box(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.bleLink == LinkStatus.OFF) {
                        AppButton(
                            label = "Bluetooth をオンにする",
                            tone = ButtonTone.Outline,
                            onClick = AppHost::requestBluetoothEnable,
                        )
                    }
                    AppButton(
                        label = "接続",
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            in LINKED -> {
                Box(Modifier.height(14.dp))
                MeterScreenPicker(
                    selected = dashScreen,
                    showing = state.phase == ConnectionPhase.PROJECTING,
                    onSelect = AppHost::setDashScreen,
                )
                Box(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.liveMode) {
                        AppButton(
                            label = "表示をやめる",
                            tone = ButtonTone.Danger,
                            onClick = onStopProjection,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        AppButton(
                            label = "メーターに表示",
                            tone = ButtonTone.Tonal,
                            onClick = onProject,
                            enabled = canShowOnMeter(state),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    AppButton(label = "切断", tone = ButtonTone.Outline, onClick = onDisconnect)
                }
                // Activated earlier, Wi-Fi turned off since: widgets still run over BLE, only
                // the video needs it. Say why the button is grey.
                if (!state.liveMode && !state.needsWifiActivation && state.wifiLink == LinkStatus.OFF) {
                    Box(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "メーターに表示するには Wi-Fi が必要です",
                            color = AppColors.Ink2,
                            fontFamily = AppFonts.Sans,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        AppButton(label = "Wi-Fi をオンにする", tone = ButtonTone.Text, onClick = AppHost::openWifiPanel)
                    }
                }
            }
            else -> Unit  // working phases: the cancel button in the header row is enough
        }
    }
}

/**
 * Connected over BLE without this power-cycle's Wi-Fi activation, so the dash may not draw
 * its widgets yet. Says so, with the one action that fixes it.
 */
@Composable
private fun ActivationNotice(state: DashState) {
    if (state.phase !in LINKED || !state.needsWifiActivation) return
    val (text, label, action) = when (state.wifiLink) {
        LinkStatus.CONNECTING -> return  // activation running; the Wi-Fi row says so
        LinkStatus.OFF -> Triple(
            "ダッシュにナビや天気を表示するには、最初に一度 Wi-Fi で接続する必要があります。Wi-Fi をオンにすると、自動で続きを行います。",
            "Wi-Fi をオンにする",
            AppHost::openWifiPanel,
        )
        else -> Triple(
            "ダッシュの Wi-Fi に接続できませんでした。メーターにナビや天気が出ないときは、もう一度試してください。",
            "Wi-Fi でもう一度試す",
            AppHost::activateWifi,
        )
    }
    Box(Modifier.height(12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.WarnBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = AppColors.WarnInk,
            fontFamily = AppFonts.Sans,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Box(Modifier.height(8.dp))
        AppButton(label = label, tone = ButtonTone.Outline, onClick = action)
    }
}

/**
 * The sheet folded to one row: state, both radios, and the single action that matters in it.
 * Drag the sheet up to expand it back; disconnect lives only in the expanded form.
 */
@Composable
private fun CollapsedSheetRow(
    state: DashState,
    dashScreen: DashScreen,
    onConnect: () -> Unit,
    onProject: () -> Unit,
    onStopProjection: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val linked = state.phase in LINKED
    val wifiOffBlocking = linked && state.needsWifiActivation && state.wifiLink == LinkStatus.OFF
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            StateDot(color = dotColor(state))
            Box(Modifier.width(12.dp))
            Text(
                text = headlineFor(state),
                color = AppColors.Ink,
                fontFamily = AppFonts.Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.phase != ConnectionPhase.IDLE) {
            Box(Modifier.width(8.dp))
            LinkMiniIcons(state)
        }
        // Which screen the meter gets, as the same little meter the picker shows.
        if (linked && !wifiOffBlocking) {
            Box(Modifier.width(8.dp))
            MeterThumbnail(dashScreen, Modifier.width(40.dp))
        }
        Box(Modifier.width(8.dp))
        when {
            linked && state.liveMode ->
                AppButton(label = "やめる", tone = ButtonTone.Danger, onClick = onStopProjection)
            wifiOffBlocking ->
                AppButton(label = "Wi-Fi をオン", tone = ButtonTone.Tonal, onClick = AppHost::openWifiPanel)
            linked ->
                AppButton(label = "表示", tone = ButtonTone.Tonal, onClick = onProject, enabled = canShowOnMeter(state))
            isWorking(state.phase) ->
                AppButton(label = "キャンセル", tone = ButtonTone.Text, onClick = onDisconnect)
            else ->
                AppButton(label = "接続", onClick = onConnect)
        }
    }
}

/**
 * ODO controls while the dash shows the rally screen, in the turn card's place: the current
 * ODO / PART and glove-sized ±0.01 (hold to repeat) and PART reset. Resetting everything
 * sits behind ⋯ so a stray tap mid-stage can't wipe the day.
 */
@Composable
private fun RallyControlCard(modifier: Modifier = Modifier) {
    val r by RallyHost.readout.collectAsState()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            RallyKey("ODO")
            Box(Modifier.width(8.dp))
            Text(
                text = "%.2f".format(Locale.US, r.odoKm),
                color = if (r.adjustingKm != null) AppColors.Blue else AppColors.Ink,
                fontFamily = AppFonts.Mono,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
            )
            Box(Modifier.width(14.dp))
            RallyKey("PART")
            Box(Modifier.width(8.dp))
            Text(
                text = "%.2f".format(Locale.US, r.partKm),
                color = AppColors.Ink2,
                fontFamily = AppFonts.Mono,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
            )
            Spacer(Modifier.weight(1f))
            if (!r.gpsOk) {
                Text(
                    text = "GPS ロスト",
                    color = AppColors.Red,
                    fontFamily = AppFonts.Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }
        }
        Box(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatKey("−0.01", Modifier.weight(1f)) { RallyHost.adjust(-RallyTripMeter.ADJUST_STEP_M) }
            RepeatKey("+0.01", Modifier.weight(1f)) { RallyHost.adjust(RallyTripMeter.ADJUST_STEP_M) }
            KeyBox(
                label = "PART 0",
                bg = AppColors.Surface3,
                fg = AppColors.Ink,
                modifier = Modifier.weight(1f).clickable { RallyHost.resetPart() },
            )
            KeyBox(
                label = "⋯",
                bg = Color.Transparent,
                fg = AppColors.Ink2,
                modifier = Modifier.width(48.dp).clickable { menuOpen = !menuOpen },
            )
        }
        if (menuOpen) {
            Box(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Line))
            Text(
                text = "ODO・PART・時間をすべてリセット",
                color = AppColors.Red,
                fontFamily = AppFonts.Sans,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        RallyHost.resetAll()
                        menuOpen = false
                    }
                    .padding(horizontal = 6.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun RallyKey(text: String) {
    Text(
        text = text,
        color = AppColors.Ink3,
        fontFamily = AppFonts.Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 3.dp),
    )
}

/** Fires on press, then repeats while held — a big ODO correction without 30 taps. */
@Composable
private fun RepeatKey(label: String, modifier: Modifier = Modifier, onStep: () -> Unit) {
    val scope = rememberCoroutineScope()
    KeyBox(
        label = label,
        bg = AppColors.BlueTint,
        fg = AppColors.BluePressed,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onStep()
                    val repeat = scope.launch {
                        delay(REPEAT_DELAY_MS)
                        while (true) {
                            onStep()
                            delay(REPEAT_INTERVAL_MS)
                        }
                    }
                    tryAwaitRelease()
                    repeat.cancel()
                },
            )
        },
    )
}

@Composable
private fun KeyBox(label: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontFamily = AppFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
        )
    }
}

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 100L

/**
 * Upcoming maneuver, phone-side: a white card carrying the turn glyph, the distance to it
 * and the instruction. Hidden whenever no route is active, so the sheet sits directly on
 * the map the rest of the time.
 */
@Composable
private fun ManeuverCard(modifier: Modifier = Modifier) {
    val progress by Navigator.progress.collectAsState()
    val status by Navigator.routeStatus.collectAsState()
    if (status == RouteStatus.Rerouting) {
        ManeuverShell(modifier) {
            Glyph("↻", AppColors.Amber, 24.sp)
            Box(Modifier.width(12.dp))
            Text(
                text = "ルートを再探索中…",
                color = AppColors.Ink,
                fontFamily = AppFonts.Sans,
                fontSize = 15.sp,
            )
        }
        return
    }
    val p = progress ?: return
    ManeuverShell(modifier) {
        Glyph(arrowFor(p.step.type, p.step.modifier), AppColors.Blue, 28.sp)
        Box(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatDistance(p.distanceToManeuverMeters),
                color = AppColors.BluePressed,
                fontFamily = AppFonts.Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
            Text(
                text = p.step.instruction.ifBlank {
                    fallbackInstruction(p.step.type, p.step.modifier)
                },
                color = AppColors.Ink2,
                fontFamily = AppFonts.Sans,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ManeuverShell(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content,
    )
}

internal val SIDE_PANEL_MAX = 360.dp

/** Phases where the dash link is up — the sheet's project / stop / disconnect state. */
private val LINKED = setOf(
    ConnectionPhase.DEVICE_DIALED,
    ConnectionPhase.READY,
    ConnectionPhase.PROJECTING,
)

// ---------------------------------------------------------------- state -> words

private fun isWorking(phase: ConnectionPhase) = phase in setOf(
    ConnectionPhase.JOINING_WIFI,
    ConnectionPhase.WIFI_READY,
    ConnectionPhase.BLE_HANDSHAKE,
    ConnectionPhase.BLE_READY,
    ConnectionPhase.TCP_LISTENING,
    ConnectionPhase.RECONNECTING,
)

/** Video goes over Wi-Fi; with the phone's Wi-Fi off there's nothing to show it with. */
private fun canShowOnMeter(state: DashState) = state.wifiLink != LinkStatus.OFF

private fun headlineFor(state: DashState): String = when (state.phase) {
    ConnectionPhase.IDLE -> "ダッシュ未接続"
    ConnectionPhase.ERROR -> "接続できませんでした"
    ConnectionPhase.RECONNECTING -> "再接続中"
    ConnectionPhase.PROJECTING -> "メーターに表示中"
    ConnectionPhase.DEVICE_DIALED, ConnectionPhase.READY -> when {
        state.needsWifiActivation && state.wifiLink == LinkStatus.CONNECTING -> "表示を有効化中"
        state.needsWifiActivation -> "接続済み（表示は未有効）"
        state.liveMode -> "表示の準備ができました"
        else -> "接続済み"
    }
    else -> "接続中"
}

private fun subtitleFor(state: DashState, screen: DashScreen): String = when (state.phase) {
    ConnectionPhase.IDLE -> "K450 Rally"
    ConnectionPhase.JOINING_WIFI -> "ダッシュの Wi-Fi に接続しています"
    ConnectionPhase.WIFI_READY -> "Wi-Fi に接続しました"
    ConnectionPhase.BLE_HANDSHAKE -> "Bluetooth で接続しています"
    ConnectionPhase.BLE_READY -> "Bluetooth に接続しました"
    ConnectionPhase.TCP_LISTENING -> "メーターの応答を待っています"
    ConnectionPhase.DEVICE_DIALED, ConnectionPhase.READY -> when {
        state.needsWifiActivation && state.wifiLink == LinkStatus.CONNECTING -> "Wi-Fi で一度だけ接続しています"
        state.needsWifiActivation -> "Bluetooth で接続しました"
        state.liveMode -> "メーターの UP ボタンを長押ししてください"
        else -> "メーターは標準画面（速度・天気・ナビ）"
    }
    ConnectionPhase.PROJECTING -> "${screen.meterName()}をメーターに表示しています"
    ConnectionPhase.RECONNECTING -> "再試行 ${state.reconnectAttempt} 回目"
    ConnectionPhase.ERROR -> "下の内容を確認して、もう一度お試しください"
}

private fun dotColor(state: DashState): Color = when (state.phase) {
    ConnectionPhase.IDLE -> AppColors.Ink3
    ConnectionPhase.READY, ConnectionPhase.DEVICE_DIALED ->
        if (state.needsWifiActivation) AppColors.Amber else AppColors.Green
    ConnectionPhase.PROJECTING -> AppColors.Red
    ConnectionPhase.ERROR -> AppColors.Red
    else -> AppColors.Amber
}

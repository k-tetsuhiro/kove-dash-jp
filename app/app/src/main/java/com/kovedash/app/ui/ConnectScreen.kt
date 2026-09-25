// Modified by k-tetsuhiro for kove-dash-jp (2026): Maps-style phone UI (design/v2-mockup.html).
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
import com.kovedash.app.nav.Navigator
import com.kovedash.app.nav.RouteStatus
import com.kovedash.app.service.ConnectionPhase
import com.kovedash.app.service.DashState
import com.kovedash.app.ui.components.AppButton
import com.kovedash.app.ui.components.BottomSheet
import com.kovedash.app.ui.components.ButtonTone
import com.kovedash.app.ui.components.Glyph
import com.kovedash.app.ui.components.LinearProgress
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
 * collapses to one row in both orientations — on its own once the link is up, or by its grabber.
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
            LandscapeLayout(panelWidth = min(SIDE_PANEL_MAX, maxWidth * 0.45f), topChrome, sheet)
        } else {
            PortraitLayout(topChrome, sheet)
        }
    }
}

@Composable
private fun BoxScope.PortraitLayout(
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
    ) { ManeuverCard() }

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
        ManeuverCard()
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
 * Connection state and its actions. Three shapes, matching what the rider can actually do:
 * not connected (one Connect button), working (stage + progress + cancel), connected
 * (project / disconnect).
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
    BottomSheet(
        modifier = modifier,
        shape = shape,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        if (!expanded) {
            CollapsedSheetRow(state, onExpand = { onExpandedChange(true) }, onConnect, onProject, onStopProjection, onDisconnect)
            return@BottomSheet
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateDot(color = phaseColor(state.phase))
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
                    text = subtitleFor(state),
                    color = AppColors.Ink2,
                    fontFamily = AppFonts.Sans,
                    fontSize = 13.sp,
                )
            }
            if (isWorking(state.phase)) {
                AppButton(label = "キャンセル", tone = ButtonTone.Text, onClick = onDisconnect)
            }
        }

        if (isWorking(state.phase)) {
            Box(Modifier.height(14.dp))
            LinearProgress(fraction = stageIndex(state.phase) / 6f)
        }

        if (state.errorMessage != null) {
            Box(Modifier.height(12.dp))
            WarningBox(text = state.errorMessage)
        }

        when (state.phase) {
            ConnectionPhase.IDLE, ConnectionPhase.ERROR -> {
                Box(Modifier.height(14.dp))
                AppButton(
                    label = "接続",
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            in LINKED -> {
                Box(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.liveMode) {
                        AppButton(
                            label = "投影を停止",
                            tone = ButtonTone.Danger,
                            onClick = onStopProjection,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        AppButton(
                            label = "画面を投影",
                            tone = ButtonTone.Tonal,
                            onClick = onProject,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    AppButton(label = "切断", tone = ButtonTone.Outline, onClick = onDisconnect)
                }
            }
            else -> Unit  // working phases: the cancel button in the header row is enough
        }
    }
}

/**
 * The sheet folded to one row: state, and the single action that matters in it. Tapping the
 * text expands it back (as does the grabber); disconnect lives only in the expanded form.
 */
@Composable
private fun CollapsedSheetRow(
    state: DashState,
    onExpand: () -> Unit,
    onConnect: () -> Unit,
    onProject: () -> Unit,
    onStopProjection: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = "ひろげる", onClick = onExpand),
        ) {
            StateDot(color = phaseColor(state.phase))
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
        when {
            state.phase in LINKED && state.liveMode ->
                AppButton(label = "投影を停止", tone = ButtonTone.Danger, onClick = onStopProjection)
            state.phase in LINKED ->
                AppButton(label = "画面を投影", tone = ButtonTone.Tonal, onClick = onProject)
            isWorking(state.phase) ->
                AppButton(label = "キャンセル", tone = ButtonTone.Text, onClick = onDisconnect)
            else ->
                AppButton(label = "接続", onClick = onConnect)
        }
    }
}

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

private val SIDE_PANEL_MAX = 360.dp

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

/** Where this phase sits in the six-step connect sequence, for the progress bar. */
private fun stageIndex(phase: ConnectionPhase): Float = when (phase) {
    ConnectionPhase.JOINING_WIFI -> 1f
    ConnectionPhase.WIFI_READY -> 2f
    ConnectionPhase.BLE_HANDSHAKE -> 3f
    ConnectionPhase.BLE_READY -> 4f
    ConnectionPhase.TCP_LISTENING -> 5f
    ConnectionPhase.DEVICE_DIALED, ConnectionPhase.READY, ConnectionPhase.PROJECTING -> 6f
    ConnectionPhase.RECONNECTING -> 1f
    else -> 0f
}

private fun headlineFor(state: DashState): String = when (state.phase) {
    ConnectionPhase.IDLE -> "ダッシュ未接続"
    ConnectionPhase.ERROR -> "接続できませんでした"
    ConnectionPhase.RECONNECTING -> "再接続中"
    ConnectionPhase.PROJECTING -> "投影中"
    ConnectionPhase.DEVICE_DIALED, ConnectionPhase.READY -> "接続済み"
    else -> "接続中"
}

private fun subtitleFor(state: DashState): String = when (state.phase) {
    ConnectionPhase.IDLE -> "K450 Rally"
    ConnectionPhase.JOINING_WIFI -> "ダッシュの Wi-Fi に接続 · 1/6"
    ConnectionPhase.WIFI_READY -> "Wi-Fi 確立 · 2/6"
    ConnectionPhase.BLE_HANDSHAKE -> "BLE ハンドシェイク · 3/6"
    ConnectionPhase.BLE_READY -> "BLE 確立 · 4/6"
    ConnectionPhase.TCP_LISTENING -> "制御チャンネル待機 · 5/6"
    ConnectionPhase.DEVICE_DIALED -> "リンク確立 · 6/6"
    ConnectionPhase.READY -> "ウィジェットを BLE で送信中 · 映像オフ"
    ConnectionPhase.PROJECTING -> "地図の映像を送信中"
    ConnectionPhase.RECONNECTING -> "再試行 ${state.reconnectAttempt} 回目"
    ConnectionPhase.ERROR -> "設定を確認して、もう一度お試しください"
}

private fun phaseColor(phase: ConnectionPhase): Color = when (phase) {
    ConnectionPhase.IDLE -> AppColors.Ink3
    ConnectionPhase.READY, ConnectionPhase.DEVICE_DIALED -> AppColors.Green
    ConnectionPhase.PROJECTING -> AppColors.Red
    ConnectionPhase.ERROR -> AppColors.Red
    else -> AppColors.Amber
}

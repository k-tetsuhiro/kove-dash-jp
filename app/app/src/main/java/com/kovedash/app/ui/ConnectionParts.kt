// Added by k-tetsuhiro for kove-dash-jp (2026): Wi-Fi / Bluetooth link rows and the meter-screen
// picker for the connection sheet (design/connection-status-mockup.html, design/meter-screen-picker-mockup.html).
package com.kovedash.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kovedash.app.DashScreen
import com.kovedash.app.service.DashState
import com.kovedash.app.service.LinkStatus
import com.kovedash.app.ui.theme.AppColors
import com.kovedash.app.ui.theme.AppFonts
import com.kovedash.app.ui.theme.KoveColors
import com.kovedash.app.ui.theme.KoveFonts

// ---------------------------------------------------------------- link rows

/**
 * The Wi-Fi and Bluetooth rows: one line each for what that radio is doing, so a stalled
 * connect shows which half it's waiting on instead of a single progress bar.
 */
@Composable
internal fun LinkRows(state: DashState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, AppColors.Line, shape),
    ) {
        LinkRow(
            name = "Wi-Fi",
            detail = wifiDetail(state),
            status = state.wifiLink,
            icon = { WifiIcon(it, off = state.wifiLink == LinkStatus.OFF) },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Line))
        LinkRow(
            name = "Bluetooth",
            detail = bleDetail(state),
            status = state.bleLink,
            icon = { BluetoothIcon(it) },
        )
    }
}

@Composable
private fun LinkRow(
    name: String,
    detail: String,
    status: LinkStatus,
    icon: @Composable (Color) -> Unit,
) {
    val tone = toneFor(status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(tone.iconBg),
            contentAlignment = Alignment.Center,
        ) { icon(tone.iconFg) }
        Box(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = AppColors.Ink,
                fontFamily = AppFonts.Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
            Text(
                text = detail,
                color = AppColors.Ink2,
                fontFamily = AppFonts.Sans,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.width(8.dp))
        StatusPill(status)
    }
}

@Composable
private fun StatusPill(status: LinkStatus) {
    val tone = toneFor(status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tone.pillBg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        if (status == LinkStatus.CONNECTING) {
            CircularProgressIndicator(
                color = tone.pillFg,
                strokeWidth = 2.dp,
                modifier = Modifier.size(10.dp),
            )
            Box(Modifier.width(6.dp))
        }
        Text(
            text = labelFor(status),
            color = tone.pillFg,
            fontFamily = AppFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
    }
}

/** Both radios as two small tinted circles, for the folded sheet row. */
@Composable
internal fun LinkMiniIcons(state: DashState) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MiniCircle(state.wifiLink) { WifiIcon(it, off = state.wifiLink == LinkStatus.OFF, size = 15.dp) }
        MiniCircle(state.bleLink) { BluetoothIcon(it, size = 15.dp) }
    }
}

@Composable
private fun MiniCircle(status: LinkStatus, icon: @Composable (Color) -> Unit) {
    val tone = toneFor(status)
    Box(
        modifier = Modifier.size(26.dp).clip(CircleShape).background(tone.iconBg),
        contentAlignment = Alignment.Center,
    ) { icon(tone.iconFg) }
}

private data class Tone(val iconFg: Color, val iconBg: Color, val pillFg: Color, val pillBg: Color)

private fun toneFor(status: LinkStatus): Tone = when (status) {
    LinkStatus.CONNECTED -> Tone(AppColors.Green, AppColors.GreenTint, AppColors.Green, AppColors.GreenTint)
    LinkStatus.CONNECTING -> Tone(AppColors.Blue, AppColors.BlueTint, AppColors.Blue, AppColors.BlueTint)
    LinkStatus.OFF -> Tone(AppColors.WarnInk, AppColors.WarnBg, AppColors.WarnInk, AppColors.WarnBg)
    LinkStatus.FAILED -> Tone(AppColors.Red, AppColors.RedTint, AppColors.Red, AppColors.RedTint)
    LinkStatus.IDLE, LinkStatus.STANDBY -> Tone(AppColors.Ink3, AppColors.Surface3, AppColors.Ink2, AppColors.Surface3)
}

private fun labelFor(status: LinkStatus): String = when (status) {
    LinkStatus.IDLE, LinkStatus.STANDBY -> "待機中"
    LinkStatus.CONNECTING -> "接続中"
    LinkStatus.CONNECTED -> "接続済み"
    LinkStatus.OFF -> "オフ"
    LinkStatus.FAILED -> "失敗"
}

private fun wifiDetail(state: DashState): String = when (state.wifiLink) {
    LinkStatus.IDLE -> "まだ接続していません"
    LinkStatus.CONNECTING ->
        if (state.needsWifiActivation) "ダッシュの Wi-Fi で表示を有効化しています" else "ダッシュの Wi-Fi に参加しています"
    LinkStatus.CONNECTED -> if (state.liveMode) "映像を送信します" else "ダッシュの Wi-Fi に参加済み"
    LinkStatus.STANDBY -> "メーターに表示するときに自動で接続します"
    LinkStatus.OFF -> "スマホの Wi-Fi がオフです"
    LinkStatus.FAILED -> "ダッシュの Wi-Fi が見つかりません"
}

private fun bleDetail(state: DashState): String = when (state.bleLink) {
    LinkStatus.IDLE, LinkStatus.STANDBY -> "Wi-Fi のあとに接続します"
    LinkStatus.CONNECTING -> "メーターを探しています"
    LinkStatus.CONNECTED -> if (state.liveMode) "ボタン操作を受信中" else "天気・高度・ナビを送信中"
    LinkStatus.OFF -> "スマホの Bluetooth がオフです"
    LinkStatus.FAILED -> "メーターが見つかりません"
}

// ---------------------------------------------------------------- icons

@Composable
private fun WifiIcon(color: Color, off: Boolean, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round)
        val center = Offset(w / 2f, w * 0.86f)
        for (r in listOf(0.72f, 0.48f, 0.24f)) {
            val radius = w * r
            drawArc(
                color = color,
                startAngle = -135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = stroke,
            )
        }
        drawCircle(color, radius = w * 0.07f, center = center)
        if (off) {
            drawLine(color, Offset(w * 0.12f, w * 0.12f), Offset(w * 0.88f, w * 0.88f), w * 0.1f, StrokeCap.Round)
        }
    }
}

@Composable
private fun BluetoothIcon(color: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val path = Path().apply {
            moveTo(w * 0.28f, w * 0.30f)
            lineTo(w * 0.72f, w * 0.68f)
            lineTo(w * 0.50f, w * 0.88f)
            lineTo(w * 0.50f, w * 0.12f)
            lineTo(w * 0.72f, w * 0.32f)
            lineTo(w * 0.28f, w * 0.70f)
        }
        drawPath(path, color, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun MeterIcon(color: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.1f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.08f, w * 0.2f),
            size = Size(w * 0.84f, w * 0.54f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = Stroke(width = sw),
        )
        drawLine(color, Offset(w * 0.5f, w * 0.74f), Offset(w * 0.5f, w * 0.88f), sw)
        drawLine(color, Offset(w * 0.32f, w * 0.88f), Offset(w * 0.68f, w * 0.88f), sw, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- meter picker

internal fun DashScreen.meterName(): String = when (this) {
    DashScreen.MAP -> "地図"
    DashScreen.RALLY -> "ラリーメーター"
}

/**
 * What the meter shows when the app displays on it. The choices are drawn as little meters —
 * dark, landscape, in the dash's own palette — so they don't read as a switch for the phone's
 * map right above them. Selectable while merely connected too: the choice is remembered and
 * the next display starts on it.
 */
@Composable
internal fun MeterScreenPicker(
    selected: DashScreen,
    showing: Boolean,
    onSelect: (DashScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MeterIcon(AppColors.Ink2)
            Box(Modifier.width(6.dp))
            Text(
                text = "メーターに出す画面",
                color = AppColors.Ink2,
                fontFamily = AppFonts.Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
        }
        Box(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (screen in DashScreen.entries) {
                MeterTile(
                    screen = screen,
                    selected = screen == selected,
                    live = showing && screen == selected,
                    onClick = { onSelect(screen) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.MeterTile(screen: DashScreen, selected: Boolean, live: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) AppColors.Blue else AppColors.Line, shape)
            .background(AppColors.Surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(6.dp),
    ) {
        MeterThumbnail(screen, Modifier.fillMaxWidth())
        Box(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) Modifier.background(AppColors.Blue)
                        else Modifier.border(1.5.dp, AppColors.Line, CircleShape)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("✓", color = Color.White, fontFamily = AppFonts.Sans, fontSize = 10.sp)
            }
            Box(Modifier.width(6.dp))
            Text(
                text = screen.meterName(),
                color = AppColors.Ink,
                fontFamily = AppFonts.Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (live) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "表示中",
                    color = AppColors.Green,
                    fontFamily = AppFonts.Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/**
 * A drawing of the meter showing [screen], at any width (16:10). Illustrative, not live: it
 * says which kind of screen goes to the meter, the meter itself shows the real thing.
 */
@Composable
internal fun MeterThumbnail(screen: DashScreen, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.aspectRatio(1.6f)) {
        val w = size.width
        val h = size.height
        val bezel = w * 0.025f
        drawRoundRect(Color(0xFF23232F), cornerRadius = CornerRadius(w * 0.05f))
        drawRoundRect(
            KoveColors.Void,
            topLeft = Offset(bezel, bezel),
            size = Size(w - bezel * 2, h - bezel * 2),
            cornerRadius = CornerRadius(w * 0.035f),
        )
        when (screen) {
            DashScreen.MAP -> drawMapScreen(w, h)
            DashScreen.RALLY -> {
                val hair = KoveColors.Hairline
                val pad = w * 0.07f
                val gap = w * 0.04f
                val tileTop = h * 0.66f
                val leftW = (w - pad * 2 - gap) * 0.59f
                // ODO panel, CAP panel, then the row of small tiles — the rally screen's layout.
                drawRoundRect(hair, Offset(pad, pad), Size(leftW, tileTop - pad - gap), CornerRadius(w * 0.02f), style = Stroke(1f))
                drawRoundRect(
                    hair, Offset(pad + leftW + gap, pad), Size(w - pad * 2 - leftW - gap, tileTop - pad - gap),
                    CornerRadius(w * 0.02f), style = Stroke(1f),
                )
                val tileW = (w - pad * 2 - gap * 3) / 4f
                repeat(4) { i ->
                    drawRoundRect(
                        hair, Offset(pad + i * (tileW + gap), tileTop), Size(tileW, h - pad - tileTop),
                        CornerRadius(w * 0.02f), style = Stroke(1f),
                    )
                }
                val label = TextStyle(fontFamily = KoveFonts.VT323, fontSize = (h * 0.1f).toSp(), color = KoveColors.Sky)
                drawText(measurer, "ODO", Offset(pad + w * 0.03f, pad + h * 0.03f), label)
                drawText(measurer, "CAP", Offset(pad + leftW + gap + w * 0.03f, pad + h * 0.03f), label)
                drawText(
                    measurer, "42.18", Offset(pad + w * 0.03f, pad + h * 0.13f),
                    TextStyle(fontFamily = KoveFonts.VT323, fontSize = (h * 0.3f).toSp(), color = KoveColors.MintBright),
                )
                drawText(
                    measurer, "127°", Offset(pad + leftW + gap + w * 0.03f, pad + h * 0.17f),
                    TextStyle(fontFamily = KoveFonts.VT323, fontSize = (h * 0.22f).toSp(), color = KoveColors.Yellow),
                )
            }
        }
    }
}

private fun DrawScope.drawMapScreen(w: Float, h: Float) {
    // Laid out on a 160×100 grid, like the mockup's SVG.
    fun x(v: Float) = v / 160f * w
    fun y(v: Float) = v / 100f * h
    val road = Color(0xFF1D1D38)
    drawLine(road, Offset(x(4f), y(70f)), Offset(x(156f), y(56f)), y(7f))
    drawLine(road, Offset(x(60f), y(4f)), Offset(x(55f), y(96f)), y(5f))
    drawLine(road, Offset(x(108f), y(4f)), Offset(x(150f), y(92f)), y(5f))
    val route = Path().apply {
        moveTo(x(57f), y(96f))
        lineTo(x(59f), y(63f))
        lineTo(x(120f), y(57f))
    }
    drawPath(route, KoveColors.Mint, style = Stroke(width = y(3.5f), cap = StrokeCap.Round, join = StrokeJoin.Round))
    val me = Path().apply {
        moveTo(x(59f), y(76f))
        lineTo(x(53f), y(86f))
        lineTo(x(65f), y(86f))
        close()
    }
    drawPath(me, KoveColors.Sky)
    drawRoundRect(KoveColors.Void2, Offset(x(9f), y(9f)), Size(x(40f), y(22f)), CornerRadius(x(3f)))
    val turn = Path().apply {
        moveTo(x(17f), y(26f))
        lineTo(x(17f), y(16f))
        lineTo(x(27f), y(16f))
        moveTo(x(23f), y(12f))
        lineTo(x(27f), y(16f))
        lineTo(x(23f), y(20f))
    }
    drawPath(turn, KoveColors.Yellow, style = Stroke(width = y(2.4f), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawLine(KoveColors.Paper, Offset(x(32f), y(20f)), Offset(x(44f), y(20f)), y(3f))
}

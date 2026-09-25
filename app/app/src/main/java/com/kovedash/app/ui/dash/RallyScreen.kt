// Added by k-tetsuhiro for kove-dash-jp (2026): projected rally trip-meter screen.
package com.kovedash.app.ui.dash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.kovedash.app.net.WeatherSource
import com.kovedash.app.rally.RallyHost
import com.kovedash.app.rally.RallyReadout
import com.kovedash.app.ui.theme.KoveColors
import com.kovedash.app.ui.theme.KoveFonts
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The rally screen projected in place of the map: ODO (with PART) and CAP on top, then
 * speed, average, elapsed time, altitude and weather. Paper roadbook on the bars, this on
 * the dash — no separate rally computer.
 *
 * Laid out in the band the dash leaves visible: in full projection it draws its own clock
 * bar over the top and fuel/gear bar over the bottom of our 640×320 dp frame (same insets
 * as NavMap's HUD). Sizes are set in dp and converted, so the phone's font-size setting
 * can't push the numbers out of their panels.
 */
@Composable
fun RallyScreen(modifier: Modifier = Modifier) {
    val r by RallyHost.readout.collectAsState()
    val weather by WeatherSource.latest.collectAsState()
    RallyScreenContent(r, weather, modifier)
}

/** [RallyScreen] with its data passed in, for previews. */
@Composable
internal fun RallyScreenContent(r: RallyReadout, weather: WeatherSource.Weather?, modifier: Modifier = Modifier) {
    // Encoder keep-alive, as in NavMap: most of this screen is still between GPS fixes and
    // the H.264 stream needs a pixel changing every frame or the dash decoder times out.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            tick++
            delay(33L)
        }
    }
    val blinkOn = (tick / 15) % 2 == 0L

    Box(modifier = modifier.fillMaxSize().background(KoveColors.Void)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = SAFE_TOP, bottom = SAFE_BOTTOM, start = SAFE_SIDE, end = SAFE_SIDE),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(GAP),
            ) {
                OdoPanel(r, blinkOn, Modifier.weight(1.45f).fillMaxHeight())
                CapPanel(r, Modifier.weight(1f).fillMaxHeight())
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(TILE_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(GAP),
            ) {
                Tile("SPEED", "km/h", Modifier.weight(1f)) {
                    Num(r.speedKmh?.roundToInt()?.toString() ?: "--", TILE_NUM)
                }
                Tile("AVG", "km/h", Modifier.weight(1f)) {
                    Num(r.avgKmh?.let { fmt("%.1f", it) } ?: "--.-", TILE_NUM)
                }
                Tile("TIME", null, Modifier.weight(1.35f)) {
                    Num(formatElapsed(r.elapsedMs), TIME_NUM)
                }
                Tile("ALT", "m", Modifier.weight(1f)) {
                    Num(r.altitudeM?.roundToInt()?.toString() ?: "--", TILE_NUM)
                }
                Tile("WEATHER", weather?.let { "${(it.windKmh / 3.6).roundToInt()}m/s" }, Modifier.weight(1.15f)) {
                    val w = weather
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(weatherGlyph(w?.dashCode), style = TextStyle(fontSize = 22.dp.asSp()))
                        Spacer(Modifier.width(6.dp))
                        Num(w?.tempC?.toString() ?: "--", TILE_NUM)
                        Label("℃", size = 8.dp, color = KoveColors.Sky)
                    }
                }
            }
        }
        KeepAlivePip(tick)
    }
}

@Composable
private fun OdoPanel(r: RallyReadout, blinkOn: Boolean, modifier: Modifier) {
    Panel(modifier, PaddingH, 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("ODO")
            Spacer(Modifier.weight(1f))
            GpsStatus(r.gpsOk, blinkOn)
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom,
        ) {
            // White while an adjustment holds the count, yellow when it's live.
            Num(fmt("%.2f", r.odoKm), ODO_NUM, if (r.adjustingKm != null) KoveColors.HotWhite else KoveColors.Yellow)
            Spacer(Modifier.width(6.dp))
            Label("km", size = 10.dp, color = KoveColors.Sky, modifier = Modifier.padding(bottom = 6.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KoveColors.Hairline2))
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("PART")
            Spacer(Modifier.width(8.dp))
            Num(fmt("%.2f", r.partKm), PART_NUM)
            Spacer(Modifier.width(4.dp))
            Label("km", size = 8.dp, color = KoveColors.Sky)
            Spacer(Modifier.weight(1f))
            r.adjustingKm?.let { AdjustBadge(it) }
        }
    }
}

@Composable
private fun CapPanel(r: RallyReadout, modifier: Modifier) {
    val cap = r.capDeg?.let { ((it.roundToInt() % 360) + 360) % 360 }
    Panel(modifier, PaddingH, 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("CAP")
            Spacer(Modifier.weight(1f))
            Label(cap?.let(::cardinal) ?: "", size = 13.dp, color = if (r.capStale) STALE else KoveColors.MintBright)
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Num(cap?.let { "%03d".format(Locale.US, it) } ?: "---", CAP_NUM, if (r.capStale) STALE else KoveColors.HotWhite)
            // Drawn ring: VT323's ° is a filled block that reads as a zero at this size.
            Box(
                Modifier
                    .align(Alignment.Top)
                    .padding(start = 4.dp, top = 10.dp)
                    .size(12.dp)
                    .border(3.dp, KoveColors.Sky, CircleShape),
            )
        }
        CompassTape(r.capDeg, r.capStale, Modifier.fillMaxWidth().height(TAPE_HEIGHT).clipToBounds())
    }
}

/** Heading tape: ticks every 5°, degrees every 30°, cardinals every 45°, marker at centre. */
@Composable
private fun CompassTape(capDeg: Double?, stale: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = KoveFonts.PressStart2P, fontSize = 8.dp.asSp())
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val pxPerDeg = with(density) { 3.dp.toPx() }
        val cx = size.width / 2f
        val center = capDeg ?: 0.0
        drawLine(KoveColors.Hairline2, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        if (capDeg != null) {
            val halfSpan = cx / pxPerDeg + 5
            var d = floor((center - halfSpan) / 5.0) * 5.0
            while (d <= center + halfSpan) {
                val x = cx + ((d - center) * pxPerDeg).toFloat()
                val n = ((d.roundToInt() % 360) + 360) % 360
                val major = n % 15 == 0
                drawLine(
                    color = if (major) KoveColors.Sky else KoveColors.Hairline,
                    start = Offset(x, 0f),
                    end = Offset(x, (if (major) 11.dp else 6.dp).toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
                val text = when {
                    n % 45 == 0 -> cardinal(n)
                    n % 30 == 0 -> n.toString()
                    else -> null
                }
                if (text != null) {
                    val layout = measurer.measure(text, labelStyle)
                    // Fade toward the ends, like the mockup's edge mask.
                    val fade = (1f - abs(x - cx) / cx).coerceIn(0f, 1f)
                    val color = if (n % 45 == 0) KoveColors.Yellow else KoveColors.Sky
                    drawText(
                        layout,
                        color = (if (stale) STALE else color).copy(alpha = fade),
                        topLeft = Offset(x - layout.size.width / 2f, 14.dp.toPx()),
                    )
                }
                d += 5.0
            }
        }
        val tri = Path().apply {
            moveTo(cx - 7.dp.toPx(), 0f)
            lineTo(cx + 7.dp.toPx(), 0f)
            lineTo(cx, 10.dp.toPx())
            close()
        }
        drawPath(tri, KoveColors.Magenta)
    }
}

@Composable
private fun GpsStatus(ok: Boolean, blinkOn: Boolean) {
    val color = if (ok) KoveColors.Mint else KoveColors.Magenta
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .background(if (ok || blinkOn) color else Color.Transparent, CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Label(if (ok) "GPS" else "GPS LOST", size = 8.dp, color = color)
    }
}

@Composable
private fun AdjustBadge(km: Double) {
    val negative = km < 0
    Box(
        Modifier
            .background(if (negative) KoveColors.Magenta else KoveColors.Mint, RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Label(
            (if (negative) "−" else "+") + fmt("%.2f", abs(km)),
            size = 9.dp,
            color = if (negative) KoveColors.HotWhite else KoveColors.Void,
        )
    }
}

@Composable
private fun Tile(label: String, unit: String?, modifier: Modifier, content: @Composable () -> Unit) {
    Panel(modifier.fillMaxHeight(), 8.dp, 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(label, size = 9.dp)
            Spacer(Modifier.weight(1f))
            if (unit != null) Label(unit, size = 7.dp, color = KoveColors.Sky)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomStart) { content() }
    }
}

@Composable
private fun Panel(modifier: Modifier, padH: Dp, padV: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .background(KoveColors.Void2, PanelShape)
            .border(1.dp, KoveColors.Hairline, PanelShape)
            .padding(horizontal = padH, vertical = padV),
        content = content,
    )
}

@Composable
private fun Label(text: String, size: Dp = 11.dp, color: Color = KoveColors.Sky, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = tight(KoveFonts.PressStart2P, size.asSp(), color), maxLines = 1)
}

@Composable
private fun Num(text: String, size: Dp, color: Color = KoveColors.HotWhite) {
    Text(text, style = tight(KoveFonts.VT323, size.asSp(), color), maxLines = 1, softWrap = false)
}

/** Line box = glyph box: no font padding, so panel heights are what the layout says. */
private fun tight(family: androidx.compose.ui.text.font.FontFamily, size: TextUnit, color: Color) = TextStyle(
    fontFamily = family,
    fontSize = size,
    lineHeight = size,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

@Composable
private fun Dp.asSp(): TextUnit = with(LocalDensity.current) { this@asSp.toSp() }

@Composable
private fun KeepAlivePip(tick: Long) {
    Box(
        modifier = Modifier
            .padding(10.dp)
            .size(8.dp)
            .background(if ((tick and 1L) == 0L) KoveColors.Mint else Color.Transparent),
    )
}

private fun fmt(pattern: String, v: Double) = String.format(Locale.US, pattern, v)

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d:%02d".format(Locale.US, s / 3600, (s / 60) % 60, s % 60)
}

private fun cardinal(deg: Int): String =
    listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((deg + 22) % 360) / 45]

/** WeatherSource's dash glyph code (1..8) as an emoji for the projected screen. */
private fun weatherGlyph(dashCode: Int?): String = when (dashCode) {
    1 -> "☀️"
    2 -> "🌧️"
    3, 7 -> "☁️"
    4 -> "❄️"
    5 -> "⛅"
    6 -> "⛈️"
    8 -> "🌫️"
    else -> "·"
}

// Same visible band as NavMap's DASH_SAFE_* (the dash's own bars cover the rest).
private val SAFE_TOP = 44.dp
private val SAFE_BOTTOM = 60.dp
private val SAFE_SIDE = 20.dp

private val GAP = 6.dp
private val PaddingH = 12.dp
private val TILE_HEIGHT = 62.dp
private val TAPE_HEIGHT = 26.dp
private val PanelShape = RoundedCornerShape(2.dp)

private val ODO_NUM = 104.dp
private val PART_NUM = 30.dp
private val CAP_NUM = 84.dp
private val TILE_NUM = 38.dp
private val TIME_NUM = 34.dp

private val STALE = Color(0xFF5A5A78)

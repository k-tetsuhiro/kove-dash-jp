package com.kovedash.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kovedash.app.service.DashState
import com.kovedash.app.service.TelemetryFinding
import com.kovedash.app.ui.components.BeveledButton
import com.kovedash.app.ui.components.BeveledButtonVariant
import com.kovedash.app.ui.theme.KoveColors
import com.kovedash.app.ui.theme.KoveFonts

/**
 * The diagnostics half of the Settings screen. These used to be a top-level MAP / TELEMETRY
 * tab, which cost the map a tab row for something a rider never opens mid-ride — they now
 * live behind the settings cog instead.
 *
 * Two sections: [IdentitySection] (what dash are we talking to) is always populated;
 * [ProbesSection] is the capability sweep, which only runs when explicitly asked.
 */

/** What dash we're connected to, and how. Read-only; populated by the handshake. */
@Composable
fun IdentitySection(state: DashState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KoveColors.Void)
            .border(1.dp, KoveColors.Hairline2)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Kv("Gateway", state.dashGatewayIp ?: "—", if (state.dashGatewayIp != null) KoveColors.Mint else dim())
        Kv("Firmware", state.firmware ?: "—", if (state.firmware != null) KoveColors.Paper else dim())
        Kv("MAC", state.mac ?: "—", if (state.mac != null) KoveColors.Paper else dim())
        Kv("Device", state.deviceType ?: "—", if (state.deviceType != null) KoveColors.Yellow else dim())
        Kv("Battery", state.batteryLevel?.let { "$it%" } ?: "—", batteryColor(state.batteryLevel))
    }
}

/**
 * Capability sweep: fires every msg_id 27 GET we know about and lists what answered. Manual
 * by design — it floods the BLE link, which blocks native turn-by-turn from reassembling
 * (the "quiet link" recipe in DashService). Hence the warning and the explicit button.
 */
@Composable
fun ProbesSection(
    state: DashState,
    onRunSweep: () -> Unit,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KoveColors.Void)
            .border(1.dp, KoveColors.Hairline2)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "⚠ Floods the BLE link. Don't run this while following a route — " +
                "it can stall the dash's turn arrow until the next reconnect.",
            color = KoveColors.Yellow,
            fontFamily = KoveFonts.VT323,
            fontSize = 16.sp,
        )
        BeveledButton(
            label = if (state.probeSweepRunning) "SWEEPING…" else "RUN PROBE SWEEP",
            meta = if (connected) null else "Connect to the dash first",
            onClick = onRunSweep,
            variant = BeveledButtonVariant.Info,
            enabled = connected && !state.probeSweepRunning,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.telemetry.isEmpty()) {
            Text(
                text = if (state.probeSweepRunning) "querying…" else "no sweep run yet",
                color = dim(),
                fontFamily = KoveFonts.VT323,
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
            )
        } else {
            state.telemetry.forEach { f -> ProbeRow(f) }
        }
    }
}

@Composable
private fun Kv(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = label.uppercase(),
            color = KoveColors.Sky,
            fontFamily = KoveFonts.PressStart2P,
            fontSize = 8.sp,
            letterSpacing = 0.06.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = KoveFonts.VT323,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun ProbeRow(f: TelemetryFinding) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = f.label.uppercase(),
            color = KoveColors.Sky,
            fontFamily = KoveFonts.PressStart2P,
            fontSize = 7.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = f.value ?: "no response",
            color = if (f.value == null) dim() else KoveColors.Paper,
            fontFamily = KoveFonts.VT323,
            fontSize = 15.sp,
            fontStyle = if (f.value == null) FontStyle.Italic else FontStyle.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun dim() = KoveColors.Sky.copy(alpha = 0.45f)

private fun batteryColor(level: Int?) = when {
    level == null -> KoveColors.Paper.copy(alpha = 0.5f)
    level < 10 -> KoveColors.Magenta
    level < 20 -> KoveColors.Yellow
    else -> KoveColors.Mint
}

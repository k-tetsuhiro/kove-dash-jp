package com.kovedash.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kovedash.app.service.ConnectionPhase
import com.kovedash.app.service.DashState
import com.kovedash.app.ui.components.BeveledButton
import com.kovedash.app.ui.components.BeveledButtonVariant
import com.kovedash.app.ui.components.SectionHeader
import com.kovedash.app.ui.components.SponsorBand
import com.kovedash.app.ui.components.scanlineOverlay
import com.kovedash.app.ui.theme.KoveColors
import com.kovedash.app.ui.theme.KoveFonts

/**
 * Everything that isn't the map: AP credentials plus the diagnostics that used to sit in a
 * top-level TELEMETRY tab. Reachable from the cog in both orientations — the landscape
 * header is dropped for screen space, so the cog also lives in the right rail there.
 *
 * Styled to match the rest of the app (KoveColors / PressStart2P) rather than stock
 * Material, so the diagnostics sections don't read as a different app.
 */
@Composable
fun SettingsScreen(
    state: DashState,
    onSave: (password: String, ssidPrefix: String) -> Unit,
    onBack: () -> Unit,
    onRunProbeSweep: () -> Unit,
) {
    var password by remember { mutableStateOf(state.savedDashPassword ?: "") }
    var ssidPrefix by remember { mutableStateOf(state.savedSsidPrefix) }
    // The probe sweep talks to the dash over BLE — pointless (and skipped service-side)
    // before the handshake has landed.
    val connected = state.phase in setOf(
        ConnectionPhase.READY,
        ConnectionPhase.PROJECTING,
        ConnectionPhase.BLE_READY,
        ConnectionPhase.TCP_LISTENING,
        ConnectionPhase.DEVICE_DIALED,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KoveColors.Void)
            .scanlineOverlay()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SponsorBand(modifier = Modifier.fillMaxWidth())
        Text(
            text = "SETTINGS",
            color = KoveColors.Yellow,
            fontFamily = KoveFonts.BungeeInline,
            fontSize = 22.sp,
            letterSpacing = 0.08.sp,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(number = "§00", title = "Connection")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KoveColors.Void)
                    .border(1.dp, KoveColors.Hairline2)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RetroField(label = "SSID Prefix", value = ssidPrefix, onChange = { ssidPrefix = it })
                RetroField(label = "AP Password", value = password, onChange = { password = it })
                Text(
                    text = "The dash AP password rotates whenever the dash is reset. " +
                        "If auto-connect keeps failing, put the new one in here.",
                    color = KoveColors.Sky.copy(alpha = 0.75f),
                    fontFamily = KoveFonts.VT323,
                    fontSize = 16.sp,
                )
            }

            SectionHeader(number = "§01", title = "Identity")
            IdentitySection(state)

            SectionHeader(number = "§02", title = "Probes")
            ProbesSection(state = state, onRunSweep = onRunProbeSweep, connected = connected)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BeveledButton(
                label = "CANCEL",
                onClick = onBack,
                variant = BeveledButtonVariant.Ghost,
                leadingGlyph = "◄",
                trailingGlyph = "",
                modifier = Modifier.weight(1f),
            )
            BeveledButton(
                label = "SAVE",
                onClick = {
                    onSave(password.trim(), ssidPrefix.trim().ifBlank { "CQKY_" })
                },
                variant = BeveledButtonVariant.Go,
                enabled = password.isNotBlank() && ssidPrefix.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Text field in the app's own vocabulary — pixel label over a VT323 value on a hairline
 * box. BasicTextField rather than OutlinedTextField so the Material theme doesn't drag
 * its own typography and focus colors in.
 */
@Composable
private fun RetroField(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            color = KoveColors.Sky,
            fontFamily = KoveFonts.PressStart2P,
            fontSize = 8.sp,
            letterSpacing = 0.06.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = KoveColors.Paper,
                fontFamily = KoveFonts.VT323,
                fontSize = 20.sp,
            ),
            cursorBrush = SolidColor(KoveColors.Mint),
            modifier = Modifier
                .fillMaxWidth()
                .background(KoveColors.Void2)
                .border(1.dp, KoveColors.Hairline)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

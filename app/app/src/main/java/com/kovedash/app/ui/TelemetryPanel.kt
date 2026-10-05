package com.kovedash.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kovedash.app.service.DashState
import com.kovedash.app.ui.components.AppButton
import com.kovedash.app.ui.components.ButtonTone
import com.kovedash.app.ui.components.ListRow
import com.kovedash.app.ui.components.RowDivider
import com.kovedash.app.ui.components.WarningBox
import com.kovedash.app.ui.theme.AppColors
import com.kovedash.app.ui.theme.AppFonts

/**
 * The diagnostics half of Settings. [IdentitySection] (what dash are we talking to) is
 * always populated from the handshake; [ProbesSection] is the capability sweep, which
 * only runs when explicitly asked.
 */

/** What dash we're connected to, and how. Read-only. */
@Composable
fun IdentitySection(state: DashState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        ListRow(title = "ゲートウェイ", value = state.dashGatewayIp ?: "—", mono = true,
            valueColor = if (state.dashGatewayIp != null) AppColors.Green else AppColors.Ink3)
        RowDivider()
        ListRow(title = "ファームウェア", value = state.firmware ?: "—", mono = true,
            valueColor = valueColor(state.firmware))
        RowDivider()
        ListRow(title = "MAC アドレス", value = state.mac ?: "—", mono = true,
            valueColor = valueColor(state.mac))
        RowDivider()
        ListRow(title = "機種", value = state.deviceType ?: "—", mono = true,
            valueColor = valueColor(state.deviceType))
        RowDivider()
        ListRow(title = "バッテリー", value = state.batteryLevel?.let { "$it%" } ?: "—", mono = true,
            valueColor = batteryColor(state.batteryLevel))
    }
}

/**
 * Capability sweep: fires every msg_id 27 GET we know about and lists what answered.
 * Manual by design — it floods the BLE link, which blocks native turn-by-turn from
 * reassembling (the quiet-link recipe in DashService). Hence the warning and the button.
 */
@Composable
fun ProbesSection(
    state: DashState,
    onRunSweep: () -> Unit,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WarningBox(
            text = "BLE が混雑し、ダッシュのターン矢印が一時的に止まります。走行中は実行しないでください。",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            AppButton(
                label = if (state.probeSweepRunning) "検出中…" else "機能を検出",
                tone = ButtonTone.Outline,
                onClick = onRunSweep,
                enabled = connected && !state.probeSweepRunning,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.telemetry.isEmpty()) {
            Text(
                text = when {
                    state.probeSweepRunning -> "問い合わせ中…"
                    !connected -> "先にダッシュへ接続してください"
                    else -> "まだ実行していません"
                },
                color = AppColors.Ink3,
                fontFamily = AppFonts.Sans,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else {
            state.telemetry.forEachIndexed { i, f ->
                if (i > 0) RowDivider()
                ListRow(
                    title = f.label,
                    value = f.value ?: "応答なし",
                    mono = true,
                    valueColor = if (f.value == null) AppColors.Ink3 else AppColors.Ink2,
                )
            }
        }
    }
}

private fun valueColor(v: String?): Color = if (v != null) AppColors.Ink2 else AppColors.Ink3

private fun batteryColor(level: Int?): Color = when {
    level == null -> AppColors.Ink3
    level < 10 -> AppColors.Red
    level < 20 -> AppColors.Amber
    else -> AppColors.Green
}

package com.kovedash.app.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kovedash.app.service.ConnectionPhase
import com.kovedash.app.service.DashState
import com.kovedash.app.ui.components.AppButton
import com.kovedash.app.ui.components.ButtonTone
import com.kovedash.app.ui.components.Glyph
import com.kovedash.app.ui.components.GroupLabel
import com.kovedash.app.ui.theme.AppColors
import com.kovedash.app.ui.theme.AppFonts

/**
 * Everything that isn't the map: AP credentials plus the diagnostics that used to sit in
 * a top-level TELEMETRY tab. A Material list under a plain app bar — the numbered
 * §00/§01/§02 headers and pixel type of the retro build are gone.
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
    // The sweep talks to the dash over BLE — pointless (and skipped service-side) before
    // the handshake has landed.
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
            .background(AppColors.Surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Glyph("←", AppColors.Ink, 20.sp) }
            Box(Modifier.width(10.dp))
            Text(
                text = "設定",
                color = AppColors.Ink,
                fontFamily = AppFonts.Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 19.sp,
            )
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            GroupLabel("接続")
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsField(label = "ダッシュ AP の SSID", value = ssidPrefix) { ssidPrefix = it }
                SettingsField(label = "AP パスワード", value = password) { password = it }
                Text(
                    text = "ダッシュを初期化すると AP のパスワードは変わります。自動接続に繰り返し失敗する場合は、新しいパスワードをここに入れてください。",
                    color = AppColors.Ink2,
                    fontFamily = AppFonts.Sans,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }

            GroupLabel("端末情報")
            IdentitySection(state)

            GroupLabel("診断")
            ProbesSection(state = state, onRunSweep = onRunProbeSweep, connected = connected)

            Box(Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppButton(
                label = "キャンセル",
                tone = ButtonTone.Outline,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                label = "保存",
                onClick = { onSave(password.trim(), ssidPrefix.trim().ifBlank { "CQKY_" }) },
                enabled = password.isNotBlank() && ssidPrefix.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Outlined text field in the Material vocabulary. BasicTextField rather than
 * OutlinedTextField so the app's own type and colors apply without dragging in the
 * Material theme's.
 */
@Composable
private fun SettingsField(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            color = AppColors.Ink2,
            fontFamily = AppFonts.Sans,
            fontSize = 13.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = AppColors.Ink,
                fontFamily = AppFonts.Sans,
                fontSize = 16.sp,
            ),
            cursorBrush = SolidColor(AppColors.Blue),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.Surface2)
                .border(1.dp, AppColors.Line, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

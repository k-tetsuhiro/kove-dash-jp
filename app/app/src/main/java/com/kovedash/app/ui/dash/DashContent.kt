// Added by k-tetsuhiro for kove-dash-jp (2026): projected screen switch (map / rally).
package com.kovedash.app.ui.dash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kovedash.app.AppHost
import com.kovedash.app.DashScreen

/**
 * What the dash projection renders, per [AppHost.dashScreen]. The map is dropped from
 * composition while the rally screen is up rather than kept rendering unseen; switching
 * back reloads its style, a moment of blank map, in exchange for not drawing Mapbox for
 * nothing on a long stage.
 */
@Composable
fun DashContent() {
    val screen by AppHost.dashScreen.collectAsState()
    when (screen) {
        DashScreen.MAP -> NavMap(keepAlive = true, autoFollow = true)
        DashScreen.RALLY -> RallyScreen()
    }
}

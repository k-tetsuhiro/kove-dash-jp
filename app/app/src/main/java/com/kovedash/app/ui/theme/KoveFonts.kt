// Modified by k-tetsuhiro for kove-dash-jp (2026): bundle VT323 / Press Start 2P.
package com.kovedash.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.kovedash.app.R

/**
 * Fonts via Google's Downloadable Fonts API. First app launch fetches each
 * family from Google Play Services and caches them. Subsequent launches are
 * offline. If GMS is missing/old, Compose falls back to the system default.
 */
object KoveFonts {

    private val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

    // Hero display — chunky outlined letters with a clean inline highlight.
    // Used for wordmark + PRESS UP critical headlines.
    val BungeeInline = FontFamily(
        Font(googleFont = GoogleFont("Bungee Inline"), fontProvider = provider),
    )

    // Chunky italic-skew speed display — used for FPS, position pills, stage codes.
    val Bungee = FontFamily(
        Font(googleFont = GoogleFont("Bungee"), fontProvider = provider),
    )

    // Bauhaus-rounded italic-skew display — section headers, button labels.
    val BowlbyOne = FontFamily(
        Font(googleFont = GoogleFont("Bowlby One"), fontProvider = provider),
        Font(googleFont = GoogleFont("Bowlby One"), fontProvider = provider, style = FontStyle.Italic),
    )

    // Pixel/bitmap display — tiny UI labels, status badges, KV keys.
    // Bundled (res/font) rather than downloaded: the dash HUD and rally screen size their
    // numbers to these exact metrics, and a first use out of signal would otherwise fall back
    // to a system font of a different width.
    val PressStart2P = FontFamily(
        androidx.compose.ui.text.font.Font(R.font.press_start_2p_regular),
    )

    // Terminal — KV values, code/log readouts.
    val VT323 = FontFamily(
        androidx.compose.ui.text.font.Font(R.font.vt323_regular),
    )

    // Narrow industrial — fine print, stage meta.
    val BarlowCondensed = FontFamily(
        Font(googleFont = GoogleFont("Barlow Condensed"), fontProvider = provider, weight = FontWeight.Normal),
        Font(googleFont = GoogleFont("Barlow Condensed"), fontProvider = provider, weight = FontWeight.Bold),
        Font(googleFont = GoogleFont("Barlow Condensed"), fontProvider = provider, weight = FontWeight.Black),
    )

    // Stencil — stage titles, military-utility headings.
    val SairaStencilOne = FontFamily(
        Font(googleFont = GoogleFont("Saira Stencil One"), fontProvider = provider),
    )
}

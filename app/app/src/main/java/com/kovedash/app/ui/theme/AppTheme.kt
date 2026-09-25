package com.kovedash.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.kovedash.app.R

/**
 * The phone UI's palette and type — Google Maps' own vocabulary, so the app reads like the
 * thing it sits next to rather than a separate world. Replaces the retro rally treatment
 * (neon on near-black, pixel fonts) for everything the rider holds in their hand.
 *
 * [KoveColors] / [KoveFonts] deliberately survive alongside this: the dash HUD
 * (ManeuverBanner, TurnArrow, DashTripHud) still uses them. That surface is encoded to
 * H.264 at 1280×640 and read at a glance at speed, where a dark translucent panel with
 * heavy white type survives compression and a light Material card would not.
 *
 * Neutrals carry a slight blue bias so they sit with the accent instead of reading as
 * flat grey. Blue means "you can act on this" and nothing else; state is carried by the
 * separate semantic set (Green / Amber / Red), so a color never has two jobs.
 */
object AppColors {
    val Blue = Color(0xFF1A73E8)
    val BluePressed = Color(0xFF1765CC)
    val BlueTint = Color(0xFFE8F0FE)

    val Ink = Color(0xFF202124)
    val Ink2 = Color(0xFF5F6368)
    val Ink3 = Color(0xFF80868B)

    val Line = Color(0xFFDADCE0)
    val Surface = Color(0xFFFFFFFF)
    val Surface2 = Color(0xFFF8F9FA)
    val Surface3 = Color(0xFFF1F3F4)

    val Green = Color(0xFF188038)
    val Amber = Color(0xFFF9AB00)
    val Red = Color(0xFFD93025)

    /** Warning block: amber text on its own tinted ground (Material's "warning container"). */
    val WarnBg = Color(0xFFFEF7E0)
    val WarnInk = Color(0xFF8A6116)

    /** Scrim behind a full-screen sheet. */
    val Scrim = Color(0x52000000)
}

/**
 * Roboto throughout — the face Android and Google Maps already render in, so the app
 * inherits the platform's text metrics instead of fighting them. [Mono] is reserved for
 * machine values in the diagnostics list (IP, MAC, firmware, probe replies), where digits
 * and hex need to line up.
 *
 * Same Downloadable Fonts provider as [KoveFonts]; if Play Services can't serve them,
 * Compose falls back to the system face, which for Roboto is the system face anyway.
 */
object AppFonts {

    private val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

    val Sans = FontFamily(
        Font(googleFont = GoogleFont("Roboto"), fontProvider = provider, weight = FontWeight.Normal),
        Font(googleFont = GoogleFont("Roboto"), fontProvider = provider, weight = FontWeight.Medium),
        Font(googleFont = GoogleFont("Roboto"), fontProvider = provider, weight = FontWeight.Bold),
    )

    val Mono = FontFamily(
        Font(googleFont = GoogleFont("Roboto Mono"), fontProvider = provider, weight = FontWeight.Normal),
        Font(googleFont = GoogleFont("Roboto Mono"), fontProvider = provider, weight = FontWeight.Medium),
    )
}

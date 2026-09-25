package com.kovedash.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kovedash.app.ui.theme.AppColors
import com.kovedash.app.ui.theme.AppFonts

/**
 * The Material/Maps control set for the phone UI: buttons, the floating search bar, map
 * FABs, the bottom sheet and list rows. Small and hand-rolled rather than Material3's
 * components so the app keeps one visual definition in one file, the way the retro set it
 * replaces (BeveledButton, PositionPill, …) did.
 *
 * Elevation follows Material's two useful steps — a resting shadow for things sitting on
 * the map, a heavier one for the sheet that covers it.
 */

private val E1 = 2.dp
private val E2 = 6.dp

// ---------------------------------------------------------------- buttons

enum class ButtonTone {
    /** The one action the screen is for. Filled blue. */
    Filled,
    /** A strong action that isn't the only one. Blue on a blue tint. */
    Tonal,
    /** A secondary action sitting next to a stronger one. */
    Outline,
    /** Low-emphasis, usually a dismissal. */
    Text,
    /** Destructive or stop-this-now. */
    Danger,
}

@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Filled,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(20.dp)
    val bg = when {
        !enabled -> AppColors.Surface3
        tone == ButtonTone.Filled -> AppColors.Blue
        tone == ButtonTone.Tonal -> AppColors.BlueTint
        tone == ButtonTone.Danger -> AppColors.Red
        tone == ButtonTone.Outline -> AppColors.Surface
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> AppColors.Ink3
        tone == ButtonTone.Filled || tone == ButtonTone.Danger -> Color.White
        else -> AppColors.BluePressed
    }
    // Only the raised tones cast a shadow; a text button that floats looks like a bug.
    val elevation = if (enabled && (tone == ButtonTone.Filled || tone == ButtonTone.Danger)) E1 else 0.dp

    Box(
        modifier = modifier
            .height(40.dp)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(bg)
            .then(
                if (tone == ButtonTone.Outline) Modifier.border(1.dp, AppColors.Line, shape)
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (tone == ButtonTone.Text) 12.dp else 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontFamily = AppFonts.Sans,
            fontWeight = FontWeight_Medium,
            fontSize = 14.sp,
        )
    }
}

// Compose's FontWeight.Medium, aliased so the call sites above stay short.
private val FontWeight_Medium = androidx.compose.ui.text.font.FontWeight.Medium

// ---------------------------------------------------------------- map furniture

/**
 * The pill floating over the map. Doubles as the destination field: [placeholder] is what
 * shows when nothing is set, [value] the chosen destination. [trailing] carries either the
 * settings cog (idle) or a clear button (destination set).
 */
@Composable
fun FloatingSearchBar(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    leading: @Composable () -> Unit = { SearchGlyph() },
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .shadow(E2, shape, clip = false)
            .clip(shape)
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 6.dp),
    ) {
        leading()
        Box(Modifier.width(10.dp))
        Text(
            text = value ?: placeholder,
            color = if (value != null) AppColors.Ink else AppColors.Ink3,
            fontFamily = AppFonts.Sans,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Box(Modifier.width(6.dp))
            trailing()
        }
    }
}

/** Round control resting on the map — style switcher, GPX, recenter. */
@Composable
fun MapFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = AppColors.Surface,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(E1, CircleShape, clip = false)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Small labelled capsule on the map — the loaded GPX course, a map-style name. */
@Composable
fun MapPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.Ink2,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(32.dp)
            .shadow(E1, shape, clip = false)
            .clip(shape)
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
    ) {
        Text(
            text = label,
            color = accent,
            fontFamily = AppFonts.Sans,
            fontWeight = FontWeight_Medium,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- sheet

/**
 * The bottom sheet the phone UI's controls live in. Rounded top, heavy shadow, grabber.
 *
 * With [expanded] set, the grabber is live: tap toggles, a downward drag collapses and an
 * upward drag expands, and the caller renders the compact form when [expanded] is false. With
 * it null (the default) the grabber is decorative and the content alone sizes the sheet.
 * [shape] lets a caller float the sheet as a card (landscape side panel) instead of docking it.
 */
@Composable
fun BottomSheet(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    expanded: Boolean? = null,
    onExpandedChange: (Boolean) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .background(AppColors.Surface)
            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
    ) {
        // The touch target is the full-width strip around the pill, not the 32×4dp pill itself.
        val handleArea = if (expanded == null) {
            Modifier
        } else {
            val latest by rememberUpdatedState(expanded)
            Modifier
                .clickable(onClickLabel = if (expanded) "たたむ" else "ひろげる") { onExpandedChange(!latest) }
                .pointerInput(Unit) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onVerticalDrag = { _, dy -> dragged += dy },
                        onDragEnd = {
                            if (dragged > HANDLE_DRAG_PX) onExpandedChange(false)
                            else if (dragged < -HANDLE_DRAG_PX) onExpandedChange(true)
                        },
                    )
                }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(24.dp).then(handleArea),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppColors.Line),
            )
        }
        Box(Modifier.height(4.dp))
        content()
    }
}

private const val HANDLE_DRAG_PX = 24f

/** Connection-state dot. Color is the state; there is no other signal to read. */
@Composable
fun StateDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** Determinate progress for the connect sequence. */
@Composable
fun LinearProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AppColors.Surface3),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppColors.Blue),
        )
    }
}

// ---------------------------------------------------------------- lists

/** Section label in a settings/diagnostics list. Blue, small, not a numbered header. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = AppColors.Blue,
        fontFamily = AppFonts.Sans,
        fontWeight = FontWeight_Medium,
        fontSize = 12.sp,
        letterSpacing = 0.04.sp,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
    )
}

/**
 * One row of a settings list: title over an optional subtitle, with an optional right-hand
 * value. [mono] sets that value in Roboto Mono for machine readouts.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = AppColors.Ink2,
    mono: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                color = AppColors.Ink,
                fontFamily = AppFonts.Sans,
                fontSize = 15.sp,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = AppColors.Ink2,
                    fontFamily = AppFonts.Sans,
                    fontSize = 13.sp,
                )
            }
        }
        if (value != null) {
            Box(Modifier.width(12.dp))
            Text(
                text = value,
                color = valueColor,
                fontFamily = if (mono) AppFonts.Mono else AppFonts.Sans,
                fontSize = 13.sp,
            )
        }
        trailing?.invoke(this)
    }
}

/** Hairline between list rows. Inset so it reads as grouping, not a table. */
@Composable
fun RowDivider(modifier: Modifier = Modifier, inset: Dp = 16.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp)
            .background(AppColors.Surface3),
    )
}

/** Amber block for a caution the rider must read before acting. */
@Composable
fun WarningBox(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.WarnBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = AppColors.WarnInk,
            fontFamily = AppFonts.Sans,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

// ---------------------------------------------------------------- glyphs

/**
 * Icons are drawn as text glyphs rather than vector assets — the same call the retro set
 * made, and it keeps this file self-contained. They're set in Roboto, which carries all
 * of them.
 */
@Composable
fun SearchGlyph(color: Color = AppColors.Ink2) =
    Glyph("🔍", color, 15.sp)

@Composable
fun BackGlyph(color: Color = AppColors.Ink2) = Glyph("←", color, 20.sp)

@Composable
fun CloseGlyph(color: Color = AppColors.Ink2) = Glyph("✕", color, 15.sp)

@Composable
fun Glyph(text: String, color: Color, size: androidx.compose.ui.unit.TextUnit) {
    Text(text = text, color = color, fontFamily = AppFonts.Sans, fontSize = size)
}

/** Round icon chip leading a search result row. */
@Composable
fun ResultIcon(glyph: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(AppColors.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, color = AppColors.Ink2, fontFamily = AppFonts.Sans, fontSize = 15.sp)
    }
}

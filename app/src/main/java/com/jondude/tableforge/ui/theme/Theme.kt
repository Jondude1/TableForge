@file:OptIn(ExperimentalTextApi::class)

package com.jondude.tableforge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jondude.tableforge.R
import com.jondude.tableforge.data.IdeaType

/*
 * Visual language (see CREDITS.md):
 *  - "Ancient Leather" dark browns with muted gold, after Jack Lockwood's Quest Journal theme.
 *  - Near-black brown canvas (#17110C), gold accents and a crimson accent for things in play,
 *    after the Rathgar Gold TTRPG theme.
 *  - Cinzel display type and Crimson Text body type (both SIL OFL 1.1).
 */
val Ink = Color(0xFF17110C)
val Hearth = Color(0xFF1E1711)
val Leather = Color(0xFF281F17)
val LeatherHigh = Color(0xFF33281E)
val Seam = Color(0xFF4D3F31)
val SeamSoft = Color(0xFF34291F)
val Parchment = Color(0xFFEFE4CF)
val Faded = Color(0xFFB9AB92)
val Gold = Color(0xFFD4A84B)
val GoldBright = Color(0xFFF0C766)
val GoldDim = Color(0xFF5C4620)
val Crimson = Color(0xFFC0523F)
val Moss = Color(0xFF8DAE74)

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = GoldDim,
    onPrimaryContainer = Color(0xFFFFE3A6),
    secondary = Crimson,
    onSecondary = Parchment,
    secondaryContainer = Color(0xFF4B231C),
    onSecondaryContainer = Color(0xFFFFD9D1),
    tertiary = Moss,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF2F3E25),
    onTertiaryContainer = Color(0xFFDCEFCB),
    background = Ink,
    onBackground = Parchment,
    surface = Ink,
    onSurface = Parchment,
    surfaceVariant = Leather,
    onSurfaceVariant = Faded,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Hearth,
    surfaceContainer = Hearth,
    surfaceContainerHigh = Leather,
    surfaceContainerHighest = LeatherHigh,
    inverseSurface = Parchment,
    inverseOnSurface = Ink,
    outline = Seam,
    outlineVariant = SeamSoft,
    error = Color(0xFFE8776B),
    onError = Ink,
)

private fun cinzel(weight: Int) = Font(
    R.font.cinzel,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val CinzelFamily = FontFamily(cinzel(400), cinzel(600), cinzel(700))

val CrimsonFamily = FontFamily(
    Font(R.font.crimson_regular, FontWeight.Normal),
    Font(R.font.crimson_semibold, FontWeight.SemiBold),
    Font(R.font.crimson_italic, FontWeight.Normal, FontStyle.Italic),
)

private val typography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = CinzelFamily, fontWeight = FontWeight.SemiBold),
        headlineLarge = t.headlineLarge.copy(fontFamily = CinzelFamily, fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = CinzelFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = CinzelFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleLarge = t.titleLarge.copy(fontFamily = CrimsonFamily, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 28.sp),
        titleMedium = t.titleMedium.copy(fontFamily = CrimsonFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
        bodyLarge = t.bodyLarge.copy(fontFamily = CrimsonFamily, fontSize = 18.sp, lineHeight = 25.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = CrimsonFamily, fontSize = 16.5.sp, lineHeight = 22.sp),
        bodySmall = t.bodySmall.copy(fontFamily = CrimsonFamily, fontSize = 14.5.sp, lineHeight = 19.sp),
        labelSmall = TextStyle(fontFamily = CinzelFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
    )
}

@Composable
fun TableForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

/** Each idea type gets its own hue so it's recognizable on cards, chips and the map. */
fun IdeaType.color(): Color = when (this) {
    IdeaType.NPC -> Color(0xFFD4A84B)
    IdeaType.EVENT -> Color(0xFFE0805F)
    IdeaType.LOCATION -> Color(0xFF8DAE74)
    IdeaType.DUNGEON_ROOM -> Color(0xFFA493D1)
    IdeaType.MAGIC_ITEM -> Color(0xFF63B7CC)
    IdeaType.STORY_BEAT -> Color(0xFFD884AA)
    IdeaType.NOTE -> Color(0xFFB9AB92)
}

/** Standard 5e rarity colors, tuned for a dark background. */
fun rarityColor(r: String): Color = when (r.lowercase()) {
    "common" -> Color(0xFFC9BEA8)
    "uncommon" -> Color(0xFF7FC27A)
    "rare" -> Color(0xFF6AA8E6)
    "very rare" -> Color(0xFFB487E6)
    "legendary" -> Color(0xFFE8A64A)
    "artifact" -> Color(0xFFE86E5E)
    else -> Color(0xFFC9BEA8)
}

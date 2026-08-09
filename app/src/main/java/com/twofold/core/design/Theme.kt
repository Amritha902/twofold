package com.twofold.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.sp
import com.twofold.R

/**
 * Twofold's palette. Paper and ink, one accent, no gradients — see docs/DESIGN.md.
 *
 * The far half of this screen is, for ten minutes, the agent's face. It should read as a well-set
 * page, not as an app.
 */
data class TwofoldColors(
    val paper: Color,
    val paperRaised: Color,
    val ink: Color,
    val inkMuted: Color,
    val rule: Color,
    val seal: Color,
    val marker: Color,
    val note: Color,
)

private val LightColors = TwofoldColors(
    paper = Color(0xFFF6F3EE),
    paperRaised = Color(0xFFFFFDF9),
    ink = Color(0xFF1B1917),
    inkMuted = Color(0xFF6E675C),
    rule = Color(0xFFE0D9CE),
    seal = Color(0xFF7A3428),
    marker = Color(0x47E8B84B),
    note = Color(0xFF2F4B3F),
)

/** A warm dark, not a black-and-purple inversion. Agents work in living rooms in the evening. */
private val DarkColors = TwofoldColors(
    paper = Color(0xFF17150F),
    paperRaised = Color(0xFF201D16),
    ink = Color(0xFFEDE7DC),
    inkMuted = Color(0xFF9C9384),
    rule = Color(0xFF35301F),
    seal = Color(0xFFC26A54),
    marker = Color(0x47E8B84B),
    note = Color(0xFF8FBCA5),
)

val LocalTwofoldColors = staticCompositionLocalOf { LightColors }

/**
 * Both faces are embedded in the APK, not fetched from a font provider.
 *
 * Downloadable fonts would mean a document opening with fallback type in a client's living room
 * with no signal — which is precisely the situation this app is for. Both are SIL OFL; the licences
 * ship in assets/licenses.
 *
 * These are variable fonts, so weights come from variation settings on the single file rather than
 * from separate static cuts.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Editorial, credible, reads as printed matter. Used for the document and for headings. */
private val SourceSerif = FontFamily(
    variable(R.font.source_serif_4, FontWeight.Normal),
    variable(R.font.source_serif_4, FontWeight.Medium),
    variable(R.font.source_serif_4, FontWeight.SemiBold),
)

/**
 * The Devanagari serif, for Hindi.
 *
 * Source Serif 4 contains no Devanagari at all, so Hindi headings fell back to the system sans and
 * quietly lost the serif/sans distinction the whole design rests on — the split that makes the
 * client's half read as a document and the agent's controls read as machinery.
 *
 * Compose picks a family member by weight and style, not by which script it covers, so this can't
 * simply be appended to [SourceSerif]. It is selected by locale in [TwofoldTheme] instead.
 */
private val NotoSerifDevanagari = FontFamily(
    variable(R.font.noto_serif_devanagari, FontWeight.Normal),
    variable(R.font.noto_serif_devanagari, FontWeight.Medium),
    variable(R.font.noto_serif_devanagari, FontWeight.SemiBold),
)

/** Neutral, gets out of the way. Used for controls and metadata. */
private val Inter = FontFamily(
    variable(R.font.inter, FontWeight.Normal),
    variable(R.font.inter, FontWeight.Medium),
)

/**
 * Client-side base size is deliberately larger than a phone UI would normally use. Many clients are
 * over fifty and reading a policy across a table without their glasses.
 *
 * Serif for anything that is the document; sans for anything that operates it. The split is the
 * point — it makes the agent's controls read as machinery and the client's half read as paper.
 */
private fun twofoldTypography(serif: FontFamily) = Typography(
    displaySmall = TextStyle(
        fontFamily = serif,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineMedium = TextStyle(
        fontFamily = serif,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyLarge = TextStyle(
        fontFamily = serif,
        fontSize = 19.sp,
        lineHeight = 29.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun TwofoldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    // Latin and Devanagari need different serifs; neither font covers the other's script. Sans
    // stays Inter either way, because Android's own fallback resolves Devanagari to a neutral sans
    // that sits beside Inter without a visible seam.
    val serif = when (Locale.current.language) {
        "hi" -> NotoSerifDevanagari
        else -> SourceSerif
    }

    CompositionLocalProvider(LocalTwofoldColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) {
                darkColorScheme(
                    background = colors.paper,
                    surface = colors.paperRaised,
                    onBackground = colors.ink,
                    onSurface = colors.ink,
                    primary = colors.seal,
                )
            } else {
                lightColorScheme(
                    background = colors.paper,
                    surface = colors.paperRaised,
                    onBackground = colors.ink,
                    onSurface = colors.ink,
                    primary = colors.seal,
                )
            },
            typography = twofoldTypography(serif),
            content = content,
        )
    }
}

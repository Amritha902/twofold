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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
 * Client-side base size is deliberately larger than a phone UI would normally use. Many clients are
 * over fifty and reading a policy across a table without their glasses.
 */
private val TwofoldTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Normal),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 29.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun TwofoldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

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
            typography = TwofoldTypography,
            content = content,
        )
    }
}

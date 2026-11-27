package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Aniyomi+ accent variants share one neutral canvas and differ only in accent roles.
 */
internal class AniyomiAccentColorScheme(
    private val accent: Color,
) : BaseColorScheme() {

    private val onAccent = Color(0xFF17131F)
    private val accentContainer = accent.copy(alpha = 0.22f)

    override val darkScheme: ColorScheme = TachiyomiColorScheme.darkScheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = accent,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentContainer,
        onSecondaryContainer = accent,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentContainer,
        onTertiaryContainer = accent,
    )

    override val lightScheme: ColorScheme = TachiyomiColorScheme.lightScheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = accent,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentContainer,
        onSecondaryContainer = onAccent,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentContainer,
        onTertiaryContainer = accent,
    )
}

package eu.kanade.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.colorscheme.AniyomiAccentColorScheme
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme
import eu.kanade.presentation.theme.colorscheme.CloudflareColorScheme
import eu.kanade.presentation.theme.colorscheme.CottoncandyColorScheme
import eu.kanade.presentation.theme.colorscheme.DoomColorScheme
import eu.kanade.presentation.theme.colorscheme.MatrixColorScheme
import eu.kanade.presentation.theme.colorscheme.MochaColorScheme
import eu.kanade.presentation.theme.colorscheme.MonetColorScheme
import eu.kanade.presentation.theme.colorscheme.MonochromeColorScheme
import eu.kanade.presentation.theme.colorscheme.SapphireColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import eu.kanade.presentation.theme.colorscheme.TakoColorScheme
import eu.kanade.presentation.theme.colorscheme.YinYangColorScheme
import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TachiyomiTheme(
    appTheme: AppTheme? = null,
    amoled: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    BaseTachiyomiTheme(
        appTheme = appTheme ?: uiPreferences.appTheme().get(),
        isAmoled = amoled ?: uiPreferences.themeDarkAmoled().get(),
        content = content,
    )
}

@Composable
fun TachiyomiPreviewTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) = BaseTachiyomiTheme(appTheme, isAmoled, content)

@Composable
private fun BaseTachiyomiTheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = getThemeColorScheme(appTheme, isAmoled),
        content = content,
    )
}

@Composable
@ReadOnlyComposable
private fun getThemeColorScheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
): ColorScheme {
    val uiPreferences = Injekt.get<UiPreferences>()
    val colorScheme = if (appTheme == AppTheme.MONET) {
        MonetColorScheme(LocalContext.current)
    } else {
        colorSchemes.getOrDefault(appTheme, TachiyomiColorScheme)
    }
    return colorScheme.getColorScheme(
        isSystemInDarkTheme(),
        isAmoled,
    )
}

private const val RIPPLE_DRAGGED_ALPHA = .1f
private const val RIPPLE_FOCUSED_ALPHA = .1f
private const val RIPPLE_HOVERED_ALPHA = .1f
private const val RIPPLE_PRESSED_ALPHA = .1f

val playerRippleConfiguration
    @Composable get() = RippleConfiguration(
        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
        rippleAlpha = RippleAlpha(
            draggedAlpha = RIPPLE_DRAGGED_ALPHA,
            focusedAlpha = RIPPLE_FOCUSED_ALPHA,
            hoveredAlpha = RIPPLE_HOVERED_ALPHA,
            pressedAlpha = RIPPLE_PRESSED_ALPHA,
        ),
    )

private val colorSchemes: Map<AppTheme, BaseColorScheme> = mapOf(
    AppTheme.DEFAULT to TachiyomiColorScheme,
    AppTheme.CLOUDFLARE to CloudflareColorScheme,
    AppTheme.COTTONCANDY to CottoncandyColorScheme,
    AppTheme.DOOM to DoomColorScheme,
    AppTheme.GREEN_APPLE to AniyomiAccentColorScheme(Color(0xFFB9D77D)),
    AppTheme.LAVENDER to AniyomiAccentColorScheme(Color(0xFFC4B5FD)),
    AppTheme.ARCTIC_BLUE to AniyomiAccentColorScheme(Color(0xFF8BB8FF)),
    AppTheme.SAGE_GREEN to AniyomiAccentColorScheme(Color(0xFF91C9A8)),
    AppTheme.ROSE_BERRY to AniyomiAccentColorScheme(Color(0xFFF0A6C2)),
    AppTheme.AMBER_GOLD to AniyomiAccentColorScheme(Color(0xFFE8BD78)),
    AppTheme.MATRIX to MatrixColorScheme,
    AppTheme.MIDNIGHT_DUSK to AniyomiAccentColorScheme(Color(0xFFD69BEA)),
    AppTheme.MONOCHROME to MonochromeColorScheme,
    AppTheme.MOCHA to MochaColorScheme,
    AppTheme.SAPPHIRE to SapphireColorScheme,
    AppTheme.NORD to AniyomiAccentColorScheme(Color(0xFFA5B9E8)),
    AppTheme.STRAWBERRY_DAIQUIRI to AniyomiAccentColorScheme(Color(0xFFFF9B9B)),
    AppTheme.TAKO to TakoColorScheme,
    AppTheme.TEALTURQUOISE to AniyomiAccentColorScheme(Color(0xFF65D6CF)),
    AppTheme.TIDAL_WAVE to AniyomiAccentColorScheme(Color(0xFF55C8E8)),
    AppTheme.YINYANG to YinYangColorScheme,
    AppTheme.YOTSUBA to YotsubaColorScheme,
)

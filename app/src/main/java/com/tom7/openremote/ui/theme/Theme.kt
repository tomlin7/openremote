package com.tom7.openremote.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tom7.openremote.data.ThemeMode

private val Color0 = Color(0xFF151000)

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color0,
    primaryContainer = AmberContainerDark,
    onPrimaryContainer = AmberContainerLight,
    secondary = CodeBlue,
    onSecondary = Color0,
    secondaryContainer = Color(0xFF17243A),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = CodeGreen,
    onTertiary = Color0,
    background = Ink,
    onBackground = Color(0xFFECEAE6),
    surface = InkSurface,
    onSurface = Color(0xFFECEAE6),
    surfaceVariant = InkElevated,
    onSurfaceVariant = Color(0xFFA9A6A0),
    surfaceContainer = InkElevated,
    surfaceContainerHigh = Color(0xFF1D1D21),
    surfaceContainerHighest = Color(0xFF232327),
    outline = InkBorder,
    outlineVariant = Color(0xFF232326),
    error = CodeRed,
    onError = Color0,
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFECEAE6),
    inverseOnSurface = Color(0xFF1B1B1D),
    inversePrimary = AmberDark,
)

private val LightColorScheme = lightColorScheme(
    primary = AmberDark,
    onPrimary = PaperSurface,
    primaryContainer = AmberContainerLight,
    onPrimaryContainer = Color(0xFF3A1D00),
    secondary = Color(0xFF32618F),
    onSecondary = PaperSurface,
    secondaryContainer = Color(0xFFD6E6F8),
    onSecondaryContainer = Color(0xFF0A2844),
    tertiary = Color(0xFF2E6B43),
    onTertiary = PaperSurface,
    background = Paper,
    onBackground = Color(0xFF1C1B18),
    surface = PaperSurface,
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = PaperElevated,
    onSurfaceVariant = Color(0xFF5F5B54),
    surfaceContainer = PaperElevated,
    surfaceContainerHigh = Color(0xFFEDEAE2),
    surfaceContainerHighest = Color(0xFFE5E1D8),
    outline = PaperBorder,
    outlineVariant = Color(0xFFEBE7DE),
    error = Color(0xFFB3261E),
    onError = PaperSurface,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF31302C),
    inverseOnSurface = Color(0xFFF4F1EB),
    inversePrimary = Amber,
)

@Composable
@Suppress("DEPRECATION")
fun OpenRemoteTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Keep the three-button navigation bar dark instead of letting the system paint a light scrim.
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

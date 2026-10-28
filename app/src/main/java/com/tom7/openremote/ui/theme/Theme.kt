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

private val DarkColorScheme = darkColorScheme(
    primary = Blue600,
    onPrimary = Grey50,
    primaryContainer = Blue1200,
    onPrimaryContainer = Blue300,
    secondary = Blue400,
    onSecondary = Grey1200,
    secondaryContainer = Grey1000,
    onSecondaryContainer = Grey100,
    tertiary = Color(0xFF6BD586),
    onTertiary = Grey1200,
    background = Grey1100,
    onBackground = Grey100,
    surface = Grey1100,
    onSurface = Grey100,
    surfaceVariant = Grey900,
    onSurfaceVariant = Grey500,
    surfaceContainerLowest = Grey1200,
    surfaceContainerLow = Grey1100,
    surfaceContainer = Grey1000,
    surfaceContainerHigh = Grey900,
    surfaceContainerHighest = Grey800,
    outline = BorderDarkStrong,
    outlineVariant = BorderDarkBase,
    error = Red500,
    onError = Grey1200,
    errorContainer = Red1200,
    onErrorContainer = Red300,
    inverseSurface = Grey100,
    inverseOnSurface = Grey1100,
    inversePrimary = Blue600,
    scrim = Color(0xFF000000),
)

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Grey50,
    primaryContainer = Blue200,
    onPrimaryContainer = Blue1000,
    secondary = Blue800,
    onSecondary = Grey50,
    secondaryContainer = Grey100,
    onSecondaryContainer = Grey1100,
    tertiary = Green800,
    onTertiary = Grey50,
    background = Grey50,
    onBackground = Grey1100,
    surface = Grey50,
    onSurface = Grey1100,
    surfaceVariant = Grey200,
    onSurfaceVariant = Grey700,
    surfaceContainerLowest = Grey50,
    surfaceContainerLow = Grey100,
    surfaceContainer = Grey200,
    surfaceContainerHigh = Grey300,
    surfaceContainerHighest = Grey400,
    outline = BorderLightStrong,
    outlineVariant = BorderLightBase,
    error = Red800,
    onError = Grey50,
    errorContainer = Red100,
    onErrorContainer = Red1000,
    inverseSurface = Grey1100,
    inverseOnSurface = Grey100,
    inversePrimary = Blue400,
    scrim = Color(0xFF000000),
)

@Composable
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

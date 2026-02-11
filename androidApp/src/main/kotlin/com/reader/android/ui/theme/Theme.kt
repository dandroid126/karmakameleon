package com.reader.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFFF4500), // Reddit orange
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3A0A00),
    secondary = Color(0xFF0079D3), // Reddit blue
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E3FF),
    onSecondaryContainer = Color(0xFF001C3A),
    tertiary = Color(0xFF46A508),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6F68D),
    onTertiaryContainer = Color(0xFF0F2000),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF4DED4),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD7C2B9),
    inverseSurface = Color(0xFF352F2B),
    inverseOnSurface = Color(0xFFFAEFE7),
    inversePrimary = Color(0xFFFFB59E),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB59E),
    onPrimary = Color(0xFF5F1500),
    primaryContainer = Color(0xFF862200),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFA4C9FF),
    onSecondary = Color(0xFF00315E),
    secondaryContainer = Color(0xFF004884),
    onSecondaryContainer = Color(0xFFD4E3FF),
    tertiary = Color(0xFFAAD974),
    onTertiary = Color(0xFF1D3700),
    tertiaryContainer = Color(0xFF2D5000),
    onTertiaryContainer = Color(0xFFC6F68D),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1F1B16),
    onBackground = Color(0xFFEAE1D9),
    surface = Color(0xFF1F1B16),
    onSurface = Color(0xFFEAE1D9),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B9),
    outline = Color(0xFF9F8D84),
    outlineVariant = Color(0xFF52443C),
    inverseSurface = Color(0xFFEAE1D9),
    inverseOnSurface = Color(0xFF352F2B),
    inversePrimary = Color(0xFFAD2E00),
)

@Composable
fun ReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

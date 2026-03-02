package com.karmakameleon.android.ui.theme

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
    onError = Color.White,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),

    background = Color.White,
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFAFAFA), // Action bar, reply bar
    onSurface = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFFF5F5F5), // nav bar
    surfaceContainerHighest = Color(0xFFEFEFEF), // post card background
    surfaceVariant = Color(0xFFE8E8E8), // vote chip background
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Color(0xFF808080),
    outlineVariant = Color(0xFFD0D0D0),
    inverseSurface = Color(0xFF2A2A2A),
    inverseOnSurface = Color(0xFFF0F0F0),
    inversePrimary = Color(0xFFFFB59E),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF662E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3A0A00),
    secondary = Color(0xFFD4E3FF),
    onSecondary = Color(0xFF001C3A),
    secondaryContainer = Color(0xFF0079D3),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF46A508),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6F68D),
    onTertiaryContainer = Color(0xFF0F2000),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),

    background = Color(0xFF1A1A1A),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF2A2A2A), // Action bar, reply bar
    onSurface = Color(0xFFE0E0E0),
    surfaceContainer = Color(0xFF191919), // nav bar
    surfaceContainerHighest = Color(0xFF262626), // post card background
    surfaceVariant = Color(0xFF3A3A3A), // vote chip background
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF808080),
    outlineVariant = Color(0xFF4A4A4A),
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF2A2A2A),
    inversePrimary = Color(0xFFFFB59E),
)

data class VoteColors(
    val upvoteColor: Color,
    val downvoteColor: Color
)

@Composable
fun voteColors(darkTheme: Boolean = isSystemInDarkTheme()): VoteColors {
    return VoteColors(
        upvoteColor = if(darkTheme) Color(0xFFFF662E) else Color(0xFFFF4500),
        downvoteColor = Color(0xFF7193FF)
    )
}

data class PostFlairColors(
    val nsfwColor: Color,
    val spoilerColor: Color,
    val pinnedColor: Color,
)

@Composable
fun postFlairColors(darkTheme: Boolean = isSystemInDarkTheme()): PostFlairColors {
    return PostFlairColors(
        nsfwColor = Color(0xFFFF4444),
        spoilerColor = Color(0xFFFFD700),
        pinnedColor = Color(0xFF00AA00),
    )
}

data class CommentColors(
    val depthColors: List<Color>,
    val selectedBackground: Color,
    val submitterColor: Color,
    val moderatorColor: Color,
    val adminColor: Color,
    val ownCommentColor: Color,
)

@Composable
fun commentColors(darkTheme: Boolean = isSystemInDarkTheme()): CommentColors {
    return CommentColors(
        depthColors = listOf(
            Color(0xFFFF4500),
            Color(0xFF0079D3),
            Color(0xFF46A508),
            Color(0xFFFFD635),
            Color(0xFF7193FF),
            Color(0xFFFF66AC),
        ),
        selectedBackground = Color(0xFF0079D3).copy(alpha = 0.08f),
        submitterColor = Color(0xFF0079D3),
        moderatorColor = Color(0xFF46A508),
        adminColor = Color(0xFFFF4500),
        ownCommentColor = Color(0xFFFF0000),
    )
}

data class MessageColors(
    val selectedBackground: Color,
    val newMessageColor: Color,
)

@Composable
fun messageColors(darkTheme: Boolean = isSystemInDarkTheme()): MessageColors {
    return MessageColors(
        selectedBackground = Color(0xFF0079D3).copy(alpha = 0.08f),
        newMessageColor = Color(0xFFFF0000),
    )
}

data class MediaColors(
    val loopActiveColor: Color,
)

@Composable
fun mediaColors(darkTheme: Boolean = isSystemInDarkTheme()): MediaColors {
    return MediaColors(
        loopActiveColor = Color(0xFF4FC3F7),
    )
}

data class ReadStateColors(
    val readTitleColor: Color,
)

@Composable
fun readStateColors(darkTheme: Boolean = isSystemInDarkTheme()): ReadStateColors {
    return ReadStateColors(
        readTitleColor = if(darkTheme) Color(0xFF8090B0) else Color(0xFF516181),
    )
}

@Composable
fun KarmaKameleonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.copy(
                // Override dynamic colors with our own
                background = if(darkTheme) DarkColorScheme.background else LightColorScheme.background,
                onBackground = if(darkTheme) DarkColorScheme.onBackground else LightColorScheme.onBackground,
                surface = if(darkTheme) DarkColorScheme.surface else LightColorScheme.surface,
                onSurface = if(darkTheme) DarkColorScheme.onSurface else LightColorScheme.onSurface,
                surfaceContainer = if(darkTheme) DarkColorScheme.surfaceContainer else LightColorScheme.surfaceContainer,
                surfaceContainerHighest = if(darkTheme) DarkColorScheme.surfaceContainerHighest else LightColorScheme.surfaceContainerHighest,
                surfaceVariant = if(darkTheme) DarkColorScheme.surfaceVariant else LightColorScheme.surfaceVariant,
                onSurfaceVariant = if(darkTheme) DarkColorScheme.onSurfaceVariant else LightColorScheme.onSurfaceVariant,
            )
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()
            }
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

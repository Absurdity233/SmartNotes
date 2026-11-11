package com.nexo.smartnotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = VioletDark,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F5EE),
    onSecondaryContainer = Color(0xFF00503F),
    tertiary = Coral,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFEDE9F5),
    error = Color(0xFFDC2626),
    surfaceTint = Color.Transparent,
)

private val DarkColors = darkColorScheme(
    primary = VioletLight,
    onPrimary = Color(0xFF221B4D),
    primaryContainer = Color(0xFF3A2F7A),
    onPrimaryContainer = Color(0xFFE6E0FF),
    secondary = Color(0xFF6FE3C4),
    onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF0F4D3F),
    onSecondaryContainer = Color(0xFFB7F5E3),
    tertiary = Color(0xFFFF9F8A),
    onTertiary = Color(0xFF3A0F05),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF2C2836),
    error = Color(0xFFFF8A80),
    surfaceTint = Color.Transparent,
)

/** 笔记卡片配色（随主题切换）。 */
val LocalNoteColors = staticCompositionLocalOf { NoteColorsLight }

@Composable
fun SmartNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalNoteColors provides if (darkTheme) NoteColorsDark else NoteColorsLight) {
        MaterialTheme(
            colorScheme = colors,
            typography = SmartTypography,
            content = content,
        )
    }
}

/** 按索引取笔记底色（超出范围时取模）。 */
@Composable
fun noteColorAt(index: Int): Color {
    val palette = LocalNoteColors.current
    return palette[((index % palette.size) + palette.size) % palette.size]
}

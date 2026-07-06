package com.duynd.uthsynctask.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val UthLightColorScheme = lightColorScheme(
    primary = UthBluePrimary,
    onPrimary = UthSurfaceLight,
    primaryContainer = UthBlueContainer,
    onPrimaryContainer = UthBluePrimaryDark,
    secondary = UthCyanAccent,
    onSecondary = UthSurfaceLight,
    secondaryContainer = UthBlueContainer,
    onSecondaryContainer = UthBluePrimaryDark,
    background = UthBackgroundLight,
    onBackground = UthOnBackgroundLight,
    surface = UthSurfaceLight,
    onSurface = UthOnSurfaceLight,
    surfaceVariant = UthSurfaceVariantLight,
    onSurfaceVariant = UthOnBackgroundLight,
    outline = UthOutlineLight,
    error = UthError,
    errorContainer = UthErrorContainer,
    onError = UthSurfaceLight,
    onErrorContainer = UthError
)

private val UthDarkColorScheme = darkColorScheme(
    primary = UthBluePrimaryLight,
    onPrimary = UthBlueDeepest,
    primaryContainer = UthBlueContainerDark,
    onPrimaryContainer = UthBlueContainer,
    secondary = UthCyanAccentDark,
    onSecondary = UthBlueDeepest,
    secondaryContainer = UthBlueContainerDark,
    onSecondaryContainer = UthBlueContainer,
    background = UthBackgroundDark,
    onBackground = UthOnBackgroundDark,
    surface = UthSurfaceDark,
    onSurface = UthOnSurfaceDark,
    surfaceVariant = UthSurfaceVariantDark,
    onSurfaceVariant = UthOnBackgroundDark,
    outline = UthOutlineDark,
    error = UthError,
    errorContainer = UthErrorContainer,
    onError = UthBlueDeepest,
    onErrorContainer = UthErrorContainer
)

/**
 * Theme thương hiệu UTH.
 *
 * Cố tình KHÔNG dùng Material You dynamic color (Android 12+) để giữ nhận diện
 * thương hiệu xanh dương UTH nhất quán trên mọi máy, thay vì đổi theo hình nền máy người dùng.
 */
@Composable
fun UTHSyncTaskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) UthDarkColorScheme else UthLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = UthTypography,
        content = content
    )
}

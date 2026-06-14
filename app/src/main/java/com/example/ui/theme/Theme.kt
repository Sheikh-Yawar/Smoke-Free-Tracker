package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary           = VioletPrimary,
    onPrimary         = TextOnAccent,
    primaryContainer  = VioletMuted,
    onPrimaryContainer = VioletDeep,
    secondary         = MintAccent,
    tertiary          = AmberAccent,
    background        = BackgroundBase,
    surface           = SurfaceCard,
    onBackground      = TextPrimaryDark,
    onSurface         = TextPrimaryDark,
    surfaceVariant    = SurfaceElevated,
    error             = CoralAccent
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Unused since light-only now
    dynamicColor: Boolean = false, // Strictly false
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BackgroundBase.toArgb()
            window.navigationBarColor = BackgroundBase.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}

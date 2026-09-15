package com.example.nyxa_interview.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = RedlineRed80,
    secondary = RedlineGrey80,
    tertiary = RedlineGold80
)

private val LightColorScheme = lightColorScheme(
    primary = RedlineRed40,
    secondary = RedlineGrey40,
    tertiary = RedlineGold40
)

@Composable
fun Nyxa_interviewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color intentionally disabled so the Redline brand red is consistent per section 4.6.
    dynamicColor: Boolean = false,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
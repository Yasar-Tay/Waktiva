package com.ybugmobile.waktiva.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import java.time.LocalTime
import java.util.Locale

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun WaktivaTheme(
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

    val context = LocalContext.current
    val typography = remember(context) {
        val locale = Locale.getDefault()
        val language = locale.language
        when (language) {
            "ar", "fa", "ur" -> ArabicComposedTypography
            "bn" -> BengaliComposedTypography
            else -> DefaultComposedTypography
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

@Composable
fun WaktivaBackgroundWrapper(
    currentTime: LocalTime,
    prayerDay: PrayerDay?,
    weatherCondition: WeatherCondition = WeatherCondition.CLEAR,
    content: @Composable () -> Unit
) {
    val glassTheme = getGlassTheme(currentTime, prayerDay, weatherCondition)
    val backgroundGradient = getGradientForTime(currentTime, prayerDay, weatherCondition)

    CompositionLocalProvider(
        LocalGlassTheme provides glassTheme,
        LocalBackgroundGradient provides backgroundGradient
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = backgroundGradient)
        ) {
            content()
        }
    }
}

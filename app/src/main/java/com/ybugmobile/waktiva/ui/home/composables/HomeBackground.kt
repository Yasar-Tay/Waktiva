package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.ui.theme.AtmosphericBackgroundLayer
import com.ybugmobile.waktiva.ui.theme.StarryBackgroundLayer
import com.ybugmobile.waktiva.ui.theme.WeatherBackgroundLayer
import java.time.LocalTime

@Composable
fun HomeBackground(
    backgroundGradient: Brush,
    currentTime: LocalTime,
    currentPrayerDay: PrayerDay?,
    sunAzimuth: () -> Float,
    sunAltitude: () -> Float,
    compassAzimuth: () -> Float,
    showWeatherEffects: Boolean,
    weatherCondition: WeatherCondition
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundGradient)
    ) {
        // Environmental Background
        Box(Modifier.fillMaxSize()) {
            StarryBackgroundLayer(
                currentTime = currentTime,
                day = currentPrayerDay
            )
            
            // Optimization: Defer sensor reading to avoid recomposing HomeBackground
            AtmosphericBackgroundLayer(
                currentTime = currentTime,
                day = currentPrayerDay,
                weatherCondition = weatherCondition,
                sunAzimuth = sunAzimuth(),
                sunAltitude = sunAltitude(),
                compassAzimuth = compassAzimuth()
            )

            if (showWeatherEffects) {
                WeatherBackgroundLayer(
                    condition = weatherCondition,
                    isDay = true
                )
            }
        }
    }
}

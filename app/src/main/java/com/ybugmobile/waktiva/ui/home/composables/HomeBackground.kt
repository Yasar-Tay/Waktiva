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

/**
 * Composite background component that renders multiple environmental layers.
 * Combines gradients, astronomical bodies (stars, sun), and atmospheric effects (weather).
 *
 * @param backgroundGradient The base brush representing the sky's primary color/gradient.
 * @param currentTime The system time used to synchronize celestial movements.
 * @param currentPrayerDay Timing data used to calculate sun/star positions relative to the horizon.
 * @param sunAzimuth Lambda providing the current horizontal coordinate of the sun.
 * @param sunAltitude Lambda providing the current vertical coordinate of the sun.
 * @param compassAzimuth Lambda providing the device's current heading.
 * @param showWeatherEffects Global toggle for particle-based weather animations (rain, snow).
 * @param weatherCondition The specific weather state to render (e.g., Cloudy, Clear).
 */
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
        // Environmental Layers
        Box(Modifier.fillMaxSize()) {
            // Renders stars and constellations during nighttime
            StarryBackgroundLayer(
                currentTime = currentTime,
                day = currentPrayerDay
            )
            
            // Renders astronomical bodies and atmospheric light scattering (lens flares, horizon glow)
            // Note: Sensor readings are passed as lambdas to minimize unnecessary recompositions of this root box.
            AtmosphericBackgroundLayer(
                currentTime = currentTime,
                day = currentPrayerDay,
                weatherCondition = weatherCondition,
                sunAzimuth = sunAzimuth(),
                sunAltitude = sunAltitude(),
                compassAzimuth = compassAzimuth()
            )

            // Renders foreground weather particles (rain, snow, fog)
            if (showWeatherEffects) {
                WeatherBackgroundLayer(
                    condition = weatherCondition,
                    isDay = true
                )
            }
        }
    }
}

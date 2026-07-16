package com.ybugmobile.waktiva.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherConditionTest {

    @Test
    fun traceRainStartsRainEffectAtFiveHundredthsOfAMillimeter() {
        val effect = WeatherCondition.forVisualEffects(
            reportedCondition = WeatherCondition.RAIN_SHOWERS,
            precipitationMillimeters = 0.06,
            cloudCoverPercent = 0
        )

        assertEquals(WeatherCondition.RAIN_SHOWERS, effect)
    }

    @Test
    fun drizzleStartsAtAnyPositiveMeasuredPrecipitation() {
        val effect = WeatherCondition.forVisualEffects(
            reportedCondition = WeatherCondition.DRIZZLE,
            precipitationMillimeters = 0.01,
            cloudCoverPercent = 100
        )

        assertEquals(WeatherCondition.DRIZZLE, effect)
    }

    @Test
    fun rainBelowFiveHundredthsFallsBackToCloudCover() {
        val effect = WeatherCondition.forVisualEffects(
            reportedCondition = WeatherCondition.RAINY,
            precipitationMillimeters = 0.04,
            cloudCoverPercent = 45
        )

        assertEquals(WeatherCondition.PARTLY_CLOUDY, effect)
    }

    @Test
    fun dryRainReportFallsBackToMeasuredCloudCover() {
        val effect = WeatherCondition.forVisualEffects(
            reportedCondition = WeatherCondition.RAINY,
            precipitationMillimeters = 0.0,
            cloudCoverPercent = 90
        )

        assertEquals(WeatherCondition.OVERCAST, effect)
    }

    @Test
    fun measurableRainKeepsRainEffect() {
        val effect = WeatherCondition.forVisualEffects(
            reportedCondition = WeatherCondition.RAINY,
            precipitationMillimeters = 0.05,
            cloudCoverPercent = 100
        )

        assertEquals(WeatherCondition.RAINY, effect)
    }

    @Test
    fun thunderstormCodeKeepsEffectWhenPrecipitationIsRoundedToZero() {
        val effect = WeatherCondition.forVisualEffects(
            reportedCondition = WeatherCondition.THUNDERSTORM,
            precipitationMillimeters = 0.0,
            cloudCoverPercent = 100
        )

        assertEquals(WeatherCondition.THUNDERSTORM, effect)
    }

    @Test
    fun nonPrecipitationConditionIsUnchanged() {
        val effect = WeatherCondition.forVisualEffects(
            reportedCondition = WeatherCondition.PARTLY_CLOUDY,
            precipitationMillimeters = 0.0,
            cloudCoverPercent = 75
        )

        assertEquals(WeatherCondition.PARTLY_CLOUDY, effect)
    }
}

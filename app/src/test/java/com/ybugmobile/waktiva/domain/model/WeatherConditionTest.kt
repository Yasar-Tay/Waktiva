package com.ybugmobile.waktiva.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherConditionTest {

    @Test
    fun tracePrecipitationDoesNotStartRainEffect() {
        val effect = WeatherCondition.forVisualEffects(
            reportedCondition = WeatherCondition.RAIN_SHOWERS,
            precipitationMillimeters = 0.06,
            cloudCoverPercent = 0
        )

        assertEquals(WeatherCondition.CLEAR, effect)
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
            precipitationMillimeters = 0.2,
            cloudCoverPercent = 100
        )

        assertEquals(WeatherCondition.RAINY, effect)
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

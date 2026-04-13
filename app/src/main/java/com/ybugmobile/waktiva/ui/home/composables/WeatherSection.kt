package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import java.time.LocalTime
import java.util.Locale

/**
 * Component for displaying current weather information including temperature and condition icons.
 * Automatically adjusts icons based on day/night cycles derived from prayer times.
 *
 * @param temperature The current temperature value in Celsius/Fahrenheit.
 * @param condition The [WeatherCondition] enum representing current weather state.
 * @param contentColor The base color for text and icon tinting.
 * @param modifier Root layout modifier.
 * @param currentTime Current system time used to determine day/night status.
 * @param currentPrayerDay Current day's prayer timings to extract sunrise/sunset for icon selection.
 */
@Composable
fun WeatherSection(
    temperature: Double?,
    condition: WeatherCondition,
    contentColor: Color,
    modifier: Modifier = Modifier,
    currentTime: LocalTime = LocalTime.now(),
    currentPrayerDay: PrayerDay? = null
) {
    // Determine if it's currently day or night to choose appropriate weather icons
    val isDay = remember(currentTime, currentPrayerDay) {
        val sunrise = currentPrayerDay?.timings?.get(PrayerType.SUNRISE) ?: LocalTime.of(6, 0)
        val sunset = currentPrayerDay?.timings?.get(PrayerType.MAGHRIB) ?: LocalTime.of(18, 0)
        currentTime.isAfter(sunrise) && currentTime.isBefore(sunset)
    }

    // Icon resource selection logic
    val weatherIconRes = when (condition) {
        WeatherCondition.CLEAR -> if (isDay) R.drawable.clear_day else R.drawable.clear_night
        WeatherCondition.MAINLY_CLEAR -> if (isDay) R.drawable.partly_cloudy_day else R.drawable.partly_cloudy_night
        WeatherCondition.PARTLY_CLOUDY -> if (isDay) R.drawable.partly_cloudy_day else R.drawable.partly_cloudy_night
        WeatherCondition.OVERCAST -> R.drawable.cloudy_day_night
        WeatherCondition.FOGGY -> R.drawable.haze_day_rotated
        WeatherCondition.DRIZZLE -> R.drawable.drizzle_day_night
        WeatherCondition.FREEZING_DRIZZLE -> R.drawable.sleet_day_night
        WeatherCondition.RAINY -> R.drawable.rain_day_night
        WeatherCondition.HEAVY_RAIN -> R.drawable.rain_day_night
        WeatherCondition.FREEZING_RAIN -> R.drawable.sleet_day_night
        WeatherCondition.SNOWY -> R.drawable.snow_day_night
        WeatherCondition.HEAVY_SNOW -> R.drawable.snow_day_night
        WeatherCondition.SNOW_GRAINS -> R.drawable.ic_snowflake
        WeatherCondition.RAIN_SHOWERS -> R.drawable.rain_day_night
        WeatherCondition.SNOW_SHOWERS -> R.drawable.snow_day_night
        WeatherCondition.THUNDERSTORM -> R.drawable.thunderstorm_day_night
        WeatherCondition.THUNDERSTORM_HAIL -> R.drawable.hail_day_night
        WeatherCondition.UNKNOWN -> R.drawable.cloudy_day_night
    }

    val weatherLabel = condition.displayName

    Row(
        modifier = modifier.height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (temperature != null) {
            Row(verticalAlignment = Alignment.Top) {
                // Temperature numeric display
                Text(
                    text = String.format(Locale.US, "%.0f", temperature),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 60.sp,
                        fontWeight = FontWeight.Light
                    ),
                    color = contentColor
                )
                
                // Degree symbol (styled differently for better visual balance)
                Text(
                    text = "°",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light
                    ),
                    color = contentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                // Weather icon container
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxHeight().padding(top = 10.dp, bottom = 6.dp, start = 4.dp)
                ) {
                    Image(
                        painter = painterResource(id = weatherIconRes),
                        contentDescription = weatherLabel,
                        modifier = Modifier.size(30.dp),
                        alpha = 0.9f
                    )
                }
            }
        } else {
            // Placeholder/Loading state when temperature data is pending
            Box(Modifier.width(60.dp).fillMaxHeight())
            
            /*Image(
                painter = painterResource(id = weatherIconRes),
                contentDescription = condition.name,
                modifier = Modifier.size(42.dp),
                alpha = 0.9f
            )*/
        }
    }
}

package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.model.WeatherCondition
import java.time.LocalTime
import java.util.Locale

@Composable
fun WeatherSection(
    temperature: Double?,
    condition: WeatherCondition,
    contentColor: Color,
    modifier: Modifier = Modifier,
    currentTime: LocalTime = LocalTime.now(),
    currentPrayerDay: PrayerDay? = null
) {
    val isDay = remember(currentTime, currentPrayerDay) {
        val sunrise = currentPrayerDay?.timings?.get(PrayerType.SUNRISE) ?: LocalTime.of(6, 0)
        val sunset = currentPrayerDay?.timings?.get(PrayerType.MAGHRIB) ?: LocalTime.of(18, 0)
        currentTime.isAfter(sunrise) && currentTime.isBefore(sunset)
    }

    val weatherIconRes = when (condition) {
        WeatherCondition.CLEAR -> if (isDay) R.drawable.clear_day else R.drawable.clear_night
        WeatherCondition.PARTLY_CLOUDY -> if (isDay) R.drawable.partly_cloudy_day else R.drawable.partly_cloudy_night
        WeatherCondition.CLOUDY -> R.drawable.cloudy_day_night
        WeatherCondition.FOGGY -> R.drawable.fog_day_night
        WeatherCondition.RAINY -> R.drawable.rain_day_night
        WeatherCondition.SNOWY -> R.drawable.snow_day_night
        WeatherCondition.THUNDERSTORM -> R.drawable.thunderstorm_day_night
        WeatherCondition.UNKNOWN -> null
    }

    Row(
        modifier = modifier.height(72.dp), // Stable height to prevent layout shifts
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (temperature != null) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = String.format(Locale.US, "%.0f", temperature),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 60.sp,
                        fontWeight = FontWeight.Light
                    ),
                    color = contentColor
                )
                
                if (weatherIconRes != null) {
                    Image(
                        painter = painterResource(id = weatherIconRes),
                        contentDescription = condition.name,
                        modifier = Modifier
                            .padding(top = 10.dp, start = 4.dp)
                            .size(32.dp),
                        alpha = 0.8f // Subtle glassmorphic look for the icon itself
                    )
                }
            }
        } else {
            // Placeholder to maintain width/height while loading
            Box(Modifier.width(60.dp).fillMaxHeight())
            
            if (weatherIconRes != null) {
                Image(
                    painter = painterResource(id = weatherIconRes),
                    contentDescription = condition.name,
                    modifier = Modifier.size(42.dp),
                    alpha = 0.9f
                )
            }
        }
    }
}

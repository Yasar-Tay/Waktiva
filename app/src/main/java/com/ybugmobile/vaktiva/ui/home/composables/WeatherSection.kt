package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.domain.model.WeatherCondition
import java.util.Locale

@Composable
fun WeatherSection(
    temperature: Double?,
    condition: WeatherCondition,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (temperature != null) {
            Text(
                text = String.format(Locale.US, "%.0f°", temperature),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Light
                ),
                color = contentColor
            )
        }
        
        val weatherIcon = when (condition) {
            WeatherCondition.CLEAR -> Icons.Rounded.WbSunny
            WeatherCondition.PARTLY_CLOUDY -> Icons.Rounded.WbCloudy
            WeatherCondition.CLOUDY -> Icons.Rounded.Cloud
            WeatherCondition.FOGGY -> Icons.Rounded.Grain
            WeatherCondition.RAINY -> Icons.Rounded.Umbrella
            WeatherCondition.SNOWY -> Icons.Rounded.AcUnit
            WeatherCondition.THUNDERSTORM -> Icons.Rounded.Thunderstorm
            WeatherCondition.UNKNOWN -> Icons.AutoMirrored.Rounded.HelpOutline
        }

        Icon(
            imageVector = weatherIcon,
            contentDescription = condition.name,
            tint = contentColor.copy(alpha = 0.8f),
            modifier = Modifier
                .size(42.dp)
                .padding(top = 4.dp) // Slight adjustment to balance with large temperature text
        )
    }
}

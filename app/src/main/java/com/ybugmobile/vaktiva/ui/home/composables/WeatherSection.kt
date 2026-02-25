package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
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
        
        Text(
            text = condition.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            color = contentColor.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
    }
}

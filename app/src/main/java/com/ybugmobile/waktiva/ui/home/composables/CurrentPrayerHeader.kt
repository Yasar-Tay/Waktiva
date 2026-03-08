package com.ybugmobile.waktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.domain.model.CurrentPrayer

/**
 * A floating header component that identifies the currently active prayer period.
 * Positioned relative to the central visualization.
 *
 * @param currentPrayer Information about the active prayer period.
 * @param contentColor Base color for the text.
 * @param iconColor Secondary color for decorative elements (defaults to contentColor).
 * @param modifier Root layout modifier.
 */
@Composable
fun CurrentPrayerHeader(
    currentPrayer: CurrentPrayer?,
    contentColor: Color,
    iconColor: Color = contentColor,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // Adaptive sizing based on device orientation
    val fontSize = if (isLandscape) 11.sp else 14.sp
    val offsetY = if (isLandscape) (-60.dp) else (-74.dp)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (currentPrayer != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = offsetY)
            ) {
                Text(
                    text = currentPrayer.type.getDisplayName(context).uppercase(locale),
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor.copy(alpha = 0.6f),
                    letterSpacing = if (isLandscape) 1.5.sp else 2.sp
                )
            }
        }
    }
}

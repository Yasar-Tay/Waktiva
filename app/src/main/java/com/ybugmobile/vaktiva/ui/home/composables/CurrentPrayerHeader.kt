package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.CurrentPrayer
import com.ybugmobile.vaktiva.domain.model.PrayerType

@Composable
fun CurrentPrayerHeader(
    currentPrayer: CurrentPrayer?,
    contentColor: Color,
    iconColor: Color = contentColor,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]

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
                    .offset(y = (-74.dp)) // Positioned above the FlippableCalendarCard
            ) {
                Icon(
                    imageVector = when(currentPrayer.type) {
                        PrayerType.FAJR -> ImageVector.vectorResource(R.drawable.water_lux_rotated)
                        PrayerType.SUNRISE -> Icons.Rounded.WbTwilight
                        PrayerType.DHUHR -> Icons.Rounded.WbSunny
                        PrayerType.ASR -> Icons.Rounded.WbSunny
                        PrayerType.MAGHRIB -> Icons.Rounded.WbTwilight
                        PrayerType.ISHA -> Icons.Rounded.NightsStay
                        else -> Icons.Rounded.WbSunny
                    },
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = currentPrayer.type.getDisplayName(context).uppercase(locale),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor.copy(alpha = 0.6f),
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

package com.ybugmobile.vaktiva.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import java.time.format.DateTimeFormatter
import kotlin.math.floor

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val currentDay by viewModel.currentPrayerDay.collectAsState(initial = null)
    val nextPrayer by viewModel.nextPrayerInfo.collectAsState(initial = null)
    val currentTime by viewModel.currentTime.collectAsState()
    val context = LocalContext.current

    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    val backgroundGradient = getGradientForPrayer(nextPrayer?.name)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!permissionState.allPermissionsGranted) {
                PermissionRequestCard(permissionState)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(48.dp))
            
            // City & Date
            Text(
                text = "My Location", // Placeholder until location naming is implemented
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (currentDay != null) {
                Text(
                    text = currentDay!!.hijriDate,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Countdown to Next Prayer
            if (nextPrayer != null) {
                Text(
                    text = "Next: ${nextPrayer!!.name}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = formatRemainingTime(nextPrayer!!.remainingMillis),
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Thin
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Prayer Times List (Glassmorphism effect)
            currentDay?.let { day ->
                PrayerTimeList(day, nextPrayer?.name)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestCard(permissionState: MultiplePermissionsState) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Location and Notification permissions are needed for the app to function correctly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                onClick = {
                    if (permissionState.shouldShowRationale) {
                        permissionState.launchMultiplePermissionRequest()
                    } else {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun PrayerTimeList(day: PrayerDayEntity, nextPrayerName: String?) {
    val prayers = listOf(
        "Fajr" to day.fajr,
        "Sunrise" to day.sunrise,
        "Dhuhr" to day.dhuhr,
        "Asr" to day.asr,
        "Maghrib" to day.maghrib,
        "Isha" to day.isha
    )

    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            prayers.forEach { (name, time) ->
                val isNext = name == nextPrayerName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        color = if (isNext) Color.Yellow else Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = time,
                        color = if (isNext) Color.Yellow else Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun getGradientForPrayer(prayerName: String?): Brush {
    return when (prayerName) {
        "Fajr" -> Brush.verticalGradient(listOf(Color(0xFF2C3E50), Color(0xFFFD746C))) // Pre-dawn
        "Sunrise" -> Brush.verticalGradient(listOf(Color(0xFFF3904F), Color(0xFF3B4371))) // Morning
        "Dhuhr" -> Brush.verticalGradient(listOf(Color(0xFF4CA1AF), Color(0xFFC4E0E5))) // Midday
        "Asr" -> Brush.verticalGradient(listOf(Color(0xFFF16529), Color(0xFFE44D26))) // Afternoon
        "Maghrib" -> Brush.verticalGradient(listOf(Color(0xFFE94057), Color(0xFFF27121))) // Sunset
        "Isha" -> Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF2C5364))) // Night
        else -> Brush.verticalGradient(listOf(Color(0xFF1e3c72), Color(0xFF2a5298)))
    }
}

fun formatRemainingTime(millis: Long): String {
    val seconds = floor(millis / 1000.0).toLong()
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

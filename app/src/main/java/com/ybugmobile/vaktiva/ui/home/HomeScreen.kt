package com.ybugmobile.vaktiva.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.ui.home.composables.ModernCalendarStrip
import com.ybugmobile.vaktiva.ui.home.composables.NextPrayerCountdown
import com.ybugmobile.vaktiva.ui.home.composables.PrayerCircleVisualization
import com.ybugmobile.vaktiva.ui.home.composables.PermissionRequestCard
import com.ybugmobile.vaktiva.ui.home.composables.PrayerTimeList
import com.ybugmobile.vaktiva.ui.theme.getGradientForTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val currentDay by viewModel.currentPrayerDay.collectAsState(initial = null)
    val nextPrayer by viewModel.nextPrayerInfo.collectAsState(initial = null)
    val currentTime by viewModel.currentTime.collectAsState()
    val settings by viewModel.settings.collectAsState(initial = null)
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val allDays by viewModel.allPrayerDays.collectAsState()

    HomeScreenContent(
        currentDay = currentDay,
        nextPrayer = nextPrayer,
        currentTime = currentTime,
        settings = settings,
        isRefreshing = isRefreshing,
        selectedDate = selectedDate,
        allDays = allDays,
        calculationMethods = viewModel.calculationMethods,
        onRefresh = { viewModel.refresh() },
        onMethodSelected = { viewModel.updateCalculationMethod(it) },
        onDateSelected = { viewModel.selectDate(it) }
    )
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    currentDay: PrayerDay?,
    nextPrayer: NextPrayer?,
    currentTime: LocalDateTime,
    settings: UserSettings?,
    isRefreshing: Boolean,
    selectedDate: LocalDate,
    allDays: List<PrayerDay>,
    calculationMethods: List<Pair<String, Int>>,
    onRefresh: () -> Unit,
    onMethodSelected: (Int) -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    val backgroundGradient = getGradientForTime(currentTime.toLocalTime(), currentDay)
    val scrollState = rememberScrollState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }

    // Dynamic Color Logic
    val sunrise = currentDay?.timings?.get(PrayerType.SUNRISE)
    val sunset = currentDay?.timings?.get(PrayerType.MAGHRIB)
    val isLightBackground = if (sunrise != null && sunset != null) {
        val t = currentTime.toLocalTime()
        t.isAfter(sunrise) && t.isBefore(sunset)
    } else false

    val contentColor = if (isLightBackground) Color.Black else Color.White
    val containerColor = if (isLightBackground) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.2f)
    val textShadow = if (!isLightBackground) Shadow(
        color = Color.Black.copy(alpha = 0.5f),
        offset = Offset(0f, 2f),
        blurRadius = 4f
    ) else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundGradient)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.Start
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                if (!permissionState.allPermissionsGranted) {
                    Spacer(modifier = Modifier.height(24.dp))
                    PermissionRequestCard(permissionState)
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Location & Date
                val locationText =
                    settings?.locationName ?: stringResource(R.string.home_unknown_location)
                val coordinatesText = if (settings != null) {
                    String.format(Locale.US, "%.4f, %.4f", settings.latitude, settings.longitude)
                } else ""

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = locationText,
                        color = contentColor,
                        style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start
                    )
                }

                // Display selected date's info
                val displayDate = if (selectedDate == LocalDate.now()) {
                    currentTime.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
                } else {
                    selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
                }

                Text(
                    text = displayDate,
                    color = contentColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        shadow = textShadow
                    ),
                    modifier = Modifier.padding(start = 32.dp)
                )

                if (currentDay != null) {
                    Text(
                        text = formatHijriDate(currentDay.hijriDate),
                        color = contentColor.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            shadow = textShadow
                        ),
                        modifier = Modifier.padding(start = 32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Circular Prayer Visualization
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (currentDay != null) {
                        PrayerCircleVisualization(
                            day = currentDay,
                            currentTime = if (selectedDate == LocalDate.now()) currentTime.toLocalTime() else LocalTime.MIDNIGHT,
                            nextPrayer = if (selectedDate == LocalDate.now()) nextPrayer else null,
                            isSelectedDayToday = selectedDate == LocalDate.now(),
                            contentColor = contentColor,
                            centerContent = {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        color = contentColor,
                                        modifier = Modifier.size(48.dp)
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.clickable { showDatePicker = true }
                                    ) {
                                        Text(
                                            text = selectedDate.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                shadow = textShadow
                                            ),
                                            color = contentColor.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${selectedDate.dayOfMonth}",
                                            style = MaterialTheme.typography.displayMedium.copy(
                                                shadow = textShadow
                                            ),
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor,
                                            fontSize = 64.sp
                                        )
                                        Text(
                                            text = selectedDate.year.toString(),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                shadow = textShadow
                                            ),
                                            color = contentColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        if (isRefreshing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = contentColor,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Data unavailable",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { onDateSelected(selectedDate) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = containerColor
                                    )
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
                }

                NextPrayerCountdown(
                    nextPrayer = nextPrayer,
                    selectedDate = selectedDate
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                // Calendar Strip
                ModernCalendarStrip(
                    selectedDate = selectedDate,
                    availableDates = allDays.map { it.date }.filter { !it.isBefore(LocalDate.now()) },
                    onDateSelected = onDateSelected
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                if (currentDay != null) {
                    PrayerTimeList(
                        day = currentDay,
                        nextPrayerType = if (selectedDate == LocalDate.now()) nextPrayer?.type else null,
                        contentColor = contentColor,
                        highlightColor = containerColor
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Footer with calculation method info
                settings?.let {
                    val methodName = calculationMethods.find { m -> m.second == it.calculationMethod }?.first
                        ?: "Unknown Method"

                    Card(
                        onClick = { showMethodDialog = true },
                        colors = CardDefaults.cardColors(
                            containerColor = containerColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Calculation Method",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = contentColor.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = methodName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = contentColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            onDateSelected(date)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showMethodDialog) {
        AlertDialog(
            onDismissRequest = { showMethodDialog = false },
            title = { Text(text = stringResource(R.string.settings_method)) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(calculationMethods) { (name, id) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMethodSelected(id)
                                    showMethodDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (id == settings?.calculationMethod),
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMethodDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// Utility to format Hijri date
private fun formatHijriDate(hijri: String): String {
    // Basic formatting if the string is like "1445-09-10"
    return try {
        val parts = hijri.split("-")
        if (parts.size == 3) {
            "${parts[2]} ${getMonthName(parts[1].toInt())} ${parts[0]} AH"
        } else hijri
    } catch (e: Exception) {
        hijri
    }
}

private fun getMonthName(month: Int): String {
    val months = listOf(
        "Muharram", "Safar", "Rabi' al-awwal", "Rabi' al-thani",
        "Jumada al-ula", "Jumada al-akhira", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    )
    return if (month in 1..12) months[month - 1] else ""
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    val dummyDay = PrayerDay(
        date = LocalDate.now(),
        hijriDate = "1445-09-10",
        timings = mapOf(
            PrayerType.FAJR to LocalTime.of(5, 30),
            PrayerType.SUNRISE to LocalTime.of(6, 45),
            PrayerType.DHUHR to LocalTime.of(12, 30),
            PrayerType.ASR to LocalTime.of(15, 45),
            PrayerType.MAGHRIB to LocalTime.of(18, 15),
            PrayerType.ISHA to LocalTime.of(19, 45)
        )
    )

    val dummyNextPrayer = NextPrayer(
        type = PrayerType.DHUHR,
        time = LocalTime.of(12, 30),
        remainingDuration = Duration.ofHours(1).plusMinutes(30)
    )

    val dummySettings = UserSettings(
        locationName = "Istanbul, Turkey",
        latitude = 41.0082,
        longitude = 28.9784,
        calculationMethod = 13,
        madhab = 1,
        language = "en",
        selectedAdhanPath = null,
        prayerSpecificAdhanPaths = emptyMap(),
        useSpecificAdhanForEachPrayer = false,
        playAdhanAudio = true,
        isSetupComplete = true,
        enablePreAdhanWarning = true,
        preAdhanWarningMinutes = 5
    )

    MaterialTheme {
        HomeScreenContent(
            currentDay = dummyDay,
            nextPrayer = dummyNextPrayer,
            currentTime = LocalDateTime.now(),
            settings = dummySettings,
            isRefreshing = false,
            selectedDate = LocalDate.now(),
            allDays = listOf(dummyDay, dummyDay.copy(date = LocalDate.now().plusDays(1))),
            calculationMethods = listOf("Turkey" to 13),
            onRefresh = {},
            onMethodSelected = {},
            onDateSelected = {}
        )
    }
}

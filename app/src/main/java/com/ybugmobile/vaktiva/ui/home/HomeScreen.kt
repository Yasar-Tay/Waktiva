package com.ybugmobile.vaktiva.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.ui.home.composables.ModernCalendarStrip
import com.ybugmobile.vaktiva.ui.home.composables.NextPrayerCountdown
import com.ybugmobile.vaktiva.ui.home.composables.PrayerCircleVisualization
import com.ybugmobile.vaktiva.ui.home.composables.PermissionRequestCard
import com.ybugmobile.vaktiva.ui.home.composables.PrayerTimeList
import com.ybugmobile.vaktiva.ui.theme.getGradientForTime
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
    val state by viewModel.state.collectAsState()

    HomeScreenContent(
        state = state,
        onRefresh = { viewModel.refresh() },
        onDateSelected = { viewModel.selectDate(it) },
        onMethodSelected = { /* handled in settings or direct update logic */ }
    )
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    state: HomeViewState,
    onRefresh: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onMethodSelected: (Int) -> Unit
) {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)
    val scrollState = rememberScrollState()
    var showDatePicker by remember { mutableStateOf(false) }

    val backgroundGradient = getGradientForTime(state.currentTime.toLocalTime(), state.currentPrayerDay)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundGradient)
    ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (!permissionState.allPermissionsGranted) {
                    Spacer(modifier = Modifier.height(24.dp))
                    PermissionRequestCard(permissionState)
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Location & Date
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.locationName.ifEmpty { stringResource(R.string.home_unknown_location) },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = 24.sp
                    )
                }

                val displayDate = if (state.selectedDate == LocalDate.now()) {
                    state.currentTime.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
                } else {
                    state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
                }

                Text(
                    text = displayDate,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 32.dp)
                )

                state.currentPrayerDay?.let {
                    Text(
                        text = formatHijriDate(it.hijriDate),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Circular Prayer Visualization
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    state.currentPrayerDay?.let { day ->
                        PrayerCircleVisualization(
                            day = day,
                            currentTime = if (state.selectedDate == LocalDate.now()) state.currentTime.toLocalTime() else LocalTime.MIDNIGHT,
                            nextPrayer = if (state.selectedDate == LocalDate.now()) state.nextPrayer else null,
                            isSelectedDayToday = state.selectedDate == LocalDate.now(),
                            centerContent = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.clickable { showDatePicker = true }
                                ) {
                                    Text(
                                        text = state.selectedDate.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                    Text(
                                        text = "${state.selectedDate.dayOfMonth}",
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 64.sp
                                    )
                                }
                            }
                        )
                    }
                }

                NextPrayerCountdown(
                    nextPrayer = state.nextPrayer,
                    selectedDate = state.selectedDate
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Modern Calendar Strip - Mock dates for now
                ModernCalendarStrip(
                    selectedDate = state.selectedDate,
                    availableDates = listOf(state.selectedDate), // Should be populated from state
                    onDateSelected = onDateSelected
                )

                Spacer(modifier = Modifier.height(24.dp))

                state.currentPrayerDay?.let { day ->
                    PrayerTimeList(
                        day = day,
                        nextPrayerType = if (state.selectedDate == LocalDate.now()) state.nextPrayer?.type else null,
                        currentMethodId = 3, // Mocked
                        onMethodSelected = onMethodSelected
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = state.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            onDateSelected(date)
                        }
                        showDatePicker = false
                    }) {
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
    }
}

@Composable
fun formatHijriDate(date: String): String {
    // Logic kept as is for brevity
    return date
}

package com.ybugmobile.vaktiva.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.ui.home.composables.*
import com.ybugmobile.vaktiva.ui.theme.getGradientForTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(viewModel)
        onDispose { lifecycleOwner.lifecycle.removeObserver(viewModel) }
    }

    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState(initial = null)
    val allDays by viewModel.allPrayerDays.collectAsState()

    HomeScreenContent(
        state = state,
        settings = settings,
        allDays = allDays,
        calculationMethods = viewModel.calculationMethods,
        onRefresh = { viewModel.refresh() },
        onMethodSelected = { viewModel.updateCalculationMethod(it) },
        onDateSelected = { viewModel.selectDate(it) },
        onSkipNextAudio = { name, date -> viewModel.toggleSkipNextPrayerAudio(name, date) },
        onStopAdhan = { viewModel.stopAdhan() },
        onStopTest = { viewModel.stopTestAlarm() }
    )
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    state: HomeViewState,
    settings: UserSettings?,
    allDays: List<PrayerDay>,
    calculationMethods: List<Pair<String, Int>>,
    onRefresh: () -> Unit,
    onMethodSelected: (Int) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onSkipNextAudio: (String, LocalDate) -> Unit,
    onStopAdhan: () -> Unit,
    onStopTest: () -> Unit
) {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    val backgroundGradient =
        getGradientForTime(state.currentTime.toLocalTime(), state.currentPrayerDay)
    val scrollState = rememberScrollState()
    var isFlipped by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }

    val isLight = state.currentPrayerDay?.let { day ->
        val sunrise = day.timings[PrayerType.SUNRISE]
        val sunset = day.timings[PrayerType.MAGHRIB]
        if (sunrise != null && sunset != null) {
            val now = state.currentTime.toLocalTime()
            now.isAfter(sunrise) && now.isBefore(sunset)
        } else false
    } ?: false

    val contentColor = if (isLight) Color.Black else Color.White
    val textShadow = if (!isLight) Shadow(
        Color.Black.copy(alpha = 0.5f),
        offset = Offset(0f, 2f),
        blurRadius = 4f
    ) else null

    Box(modifier = Modifier
        .fillMaxSize()
        .background(brush = backgroundGradient)) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = contentColor
            )
        } else {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        if (!permissionState.allPermissionsGranted) {
                            Spacer(modifier = Modifier.height(24.dp))
                            PermissionRequestCard(permissionState)
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        // Header with Location and Date Card on top right
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(end = 100.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            null,
                                            tint = contentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = state.locationName.substringBefore(",").ifEmpty { stringResource(R.string.home_unknown_location) },
                                            color = contentColor,
                                            style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = state.locationName.substringAfter(", ").ifEmpty { "" },
                                        color = contentColor.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                                        modifier = Modifier.padding(start = 26.dp)
                                    )
                                }
                            }

                            // Flippable Date Card (Top Right)
                            if (state.currentPrayerDay != null) {
                                val rotation by animateFloatAsState(
                                    if (isFlipped) 180f else 0f,
                                    tween(500, easing = FastOutSlowInEasing),
                                    label = "flip"
                                )
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .width(84.dp)
                                        .height(112.dp)
                                        .graphicsLayer {
                                            rotationY = rotation; cameraDistance = 12f * density
                                        }
                                        .clickable { isFlipped = !isFlipped },
                                    shadowElevation = 8.dp
                                ) {
                                    if (rotation <= 90f) {
                                        // FRONT: Gregorian
                                        Column(
                                            Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .weight(0.35f)
                                                    .background(
                                                        MaterialTheme.colorScheme.error.copy(0.3f)
                                                    ), Alignment.Center
                                            ) {
                                                Text(
                                                    state.selectedDate.format(
                                                        DateTimeFormatter.ofPattern(
                                                            "MMM"
                                                        )
                                                    ).uppercase(),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            HorizontalDivider(
                                                thickness = 1.dp,
                                                color = Color.Black.copy(0.05f)
                                            )
                                            Column(
                                                Modifier.weight(0.65f),
                                                Arrangement.Center,
                                                Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    state.selectedDate.dayOfMonth.toString(),
                                                    fontSize = 32.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.Black
                                                )
                                                Text(
                                                    state.selectedDate.format(
                                                        DateTimeFormatter.ofPattern(
                                                            "EEEE"
                                                        )
                                                    ).uppercase().take(3),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    } else {
                                        // BACK: Hijri
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .graphicsLayer { rotationY = 180f }) {
                                            state.currentPrayerDay.hijriDate?.let { hijri ->
                                                Column(
                                                    Modifier.fillMaxSize(),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Box(
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .weight(0.35f)
                                                            .background(Color(0xFF4CAF50).copy(0.2f)),
                                                        Alignment.Center
                                                    ) {
                                                        Text(
                                                            hijri.monthEn.uppercase().take(3),
                                                            color = Color(0xFF2E7D32),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    HorizontalDivider(
                                                        thickness = 1.dp,
                                                        color = Color.Black.copy(0.05f)
                                                    )
                                                    Column(
                                                        Modifier.weight(0.65f),
                                                        Arrangement.Center,
                                                        Alignment.CenterHorizontally
                                                    ) {
                                                        Text(
                                                            hijri.day.toString(),
                                                            fontSize = 32.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Color.Black
                                                        )
                                                        Text(
                                                            "${hijri.year} AH",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Circular Visualization
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.currentPrayerDay != null) {
                                PrayerCircleVisualization(
                                    day = state.currentPrayerDay,
                                    currentTime = if (state.selectedDate == LocalDate.now()) state.currentTime.toLocalTime() else LocalTime.MIDNIGHT,
                                    nextPrayer = if (state.selectedDate == LocalDate.now()) state.nextPrayer else null,
                                    isSelectedDayToday = state.selectedDate == LocalDate.now(),
                                    contentColor = contentColor,
                                    centerContent = {
                                        NextPrayerCountdown(
                                            nextPrayer = state.nextPrayer,
                                            selectedDate = state.selectedDate,
                                            onSkipAudio = { state.nextPrayer?.let { onSkipNextAudio(it.type.name, it.date) } },
                                            isMuted = state.isMuted,
                                            contentColor = contentColor,
                                            playAdhanAudio = settings?.playAdhanAudio ?: false
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Modern Adhan Controls
                    val showAdhanControls = state.isAdhanPlaying

                    AnimatedVisibility(
                        visible = showAdhanControls,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            tonalElevation = 8.dp,
                            shadowElevation = 12.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (state.isAdhanPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (state.isAdhanPlaying) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = if (state.isAdhanPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (state.isAdhanPlaying) (state.playingPrayerName ?: stringResource(R.string.adhan_playing)) 
                                               else if (state.nextPrayer?.isTest == true) stringResource(R.string.adhan_test_alarm)
                                               else (state.nextPrayer?.type?.displayName ?: ""),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (state.isAdhanPlaying) stringResource(R.string.adhan_sounding) 
                                               else if (state.nextPrayer?.isTest == true) stringResource(R.string.adhan_test_desc)
                                               else stringResource(R.string.adhan_upcoming),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }

                                // STOP BUTTON
                                Surface(
                                    onClick = if (state.isAdhanPlaying) onStopAdhan else onStopTest,
                                    color = MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "STOP", 
                                            color = Color.White, 
                                            style = MaterialTheme.typography.labelLarge, 
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(24.dp)) {
                        ModernCalendarStrip(
                            state.selectedDate,
                            allDays.map { it.date }.filter { !it.isBefore(LocalDate.now()) },
                            onDateSelected
                        )
                        Spacer(Modifier.height(24.dp))
                        if (state.currentPrayerDay != null) {
                            PrayerTimeList(
                                state.currentPrayerDay,
                                if (state.selectedDate == LocalDate.now()) state.nextPrayer?.type else null,
                                contentColor,
                                contentColor.copy(0.1f)
                            )
                        }
                        Spacer(Modifier.height(32.dp))
                        settings?.let { s ->
                            Card(
                                onClick = { showMethodDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            "Calculation Method",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            calculationMethods.find { it.second == s.calculationMethod }?.first
                                                ?: "Default",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Icon(Icons.Default.Settings, null)
                                }
                            }
                        }
                        Spacer(Modifier.height(100.dp))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            state.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        onDateSelected(
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }; showDatePicker = false
                }) { Text("OK") }
            }) {
            DatePicker(dateState)
        }
    }

    if (showMethodDialog) {
        AlertDialog(
            onDismissRequest = { showMethodDialog = false },
            title = { Text("Method") },
            text = {
                LazyColumn {
                    items(calculationMethods) { (name, id) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onMethodSelected(id); showMethodDialog = false }
                                .padding(12.dp), verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(id == settings?.calculationMethod, null)
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showMethodDialog = false
                }) { Text("Close") }
            })
    }
}

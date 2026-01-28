package com.ybugmobile.vaktiva.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

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
    var showMethodDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isLight = state.currentPrayerDay?.let { day ->
        val sunrise = day.timings[PrayerType.SUNRISE]
        val sunset = day.timings[PrayerType.MAGHRIB]
        if (sunrise != null && sunset != null) {
            val now = state.currentTime.toLocalTime()
            now.isAfter(sunrise) && now.isBefore(sunset)
        } else false
    } ?: false

    val contentColor = /*if (isLight) Color.Black else*/ Color.White

    val showAdhanControls = state.isAdhanPlaying || state.nextPrayer?.isTest == true

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = backgroundGradient)
                // .padding(padding)
                .padding(top = padding.calculateTopPadding())
        ) {

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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // Header Section
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.locationName.substringBefore(",")
                                    .ifEmpty { stringResource(R.string.home_unknown_location) },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Light,
                                color = contentColor,
                                //modifier = Modifier.graphicsLayer { shadowElevation = 2f }
                            )
                            Text(
                                text = state.locationName.substringAfter(", ").ifEmpty { "" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            // Circular Visualization - The "Weather Circle"
                            if (state.currentPrayerDay != null) {
                                PrayerCircleVisualization(
                                    day = state.currentPrayerDay,
                                    currentTime = if (state.selectedDate == LocalDate.now()) state.currentTime.toLocalTime() else LocalTime.MIDNIGHT,
                                    nextPrayer = if (state.selectedDate == LocalDate.now()) state.nextPrayer else null,
                                    isSelectedDayToday = state.selectedDate == LocalDate.now(),
                                    contentColor = contentColor,
                                    isMuted = state.isMuted,
                                    playAdhanAudio = settings?.playAdhanAudio ?: false,
                                    onSkipAudio = { prayerName ->
                                        state.nextPrayer?.let { next ->
                                            onSkipNextAudio(prayerName, next.date)
                                            scope.launch {
                                                val message = if (!state.isMuted)
                                                    "Adhan skipped for ${
                                                        prayerName.lowercase()
                                                            .replaceFirstChar { it.uppercase() }
                                                    }"
                                                else
                                                    "Adhan unmuted for ${
                                                        prayerName.lowercase()
                                                            .replaceFirstChar { it.uppercase() }
                                                    }"
                                                snackbarHostState.showSnackbar(message)
                                            }
                                        }
                                    },
                                    centerContent = {
                                        NextPrayerCountdown(
                                            nextPrayer = state.nextPrayer,
                                            selectedDate = state.selectedDate,
                                            contentColor = contentColor,
                                            currentPrayer = state.currentPrayer
                                        )
                                    }
                                )
                            }

                            // ADHAN CONTROLS
                            AnimatedVisibility(
                                visible = showAdhanControls,
                                enter = slideInVertically { it } + fadeIn(),
                                exit = slideOutVertically { it } + fadeOut()
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .padding(top = 24.dp)
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
                        }

                        // Bottom Content (Glassmorphic Container)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(/*top = */10.dp),
                            color = (if (isLight) Color.White else Color.Black).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                contentColor.copy(alpha = 0.1f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                            ) {
                                // Date Selection Strip
                                ModernCalendarStrip(
                                    state.selectedDate,
                                    allDays.map { it.date }
                                        .filter { !it.isBefore(LocalDate.now()) },
                                    onDateSelected
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                // Prayer Times List
                                if (state.currentPrayerDay != null) {
                                    PrayerTimeList(
                                        day = state.currentPrayerDay,
                                        nextPrayerType = if (state.selectedDate == LocalDate.now()) state.nextPrayer?.type else null,
                                        contentColor = contentColor,
                                        highlightColor = contentColor.copy(alpha = 0.15f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Settings / Calculation Method Card
                                settings?.let { s ->
                                    Card(
                                        onClick = { showMethodDialog = true },
                                        colors = CardDefaults.cardColors(
                                            containerColor = contentColor.copy(alpha = 0.05f)
                                        ),
                                        shape = RoundedCornerShape(16.dp),
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
                                                    stringResource(R.string.settings_method).uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = contentColor.copy(alpha = 0.5f),
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp
                                                )
                                                Text(
                                                    calculationMethods.find { it.second == s.calculationMethod }?.first
                                                        ?: "Default",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = contentColor
                                                )
                                            }
                                            Icon(
                                                Icons.Default.Settings,
                                                null,
                                                tint = contentColor.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Calculation Method Dialog
            if (showMethodDialog) {
                AlertDialog(
                    onDismissRequest = { showMethodDialog = false },
                    title = { Text(stringResource(R.string.settings_method)) },
                    text = {
                        LazyColumn {
                            items(calculationMethods) { method ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onMethodSelected(method.second)
                                            showMethodDialog = false
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = settings?.calculationMethod == method.second,
                                        onClick = null
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(method.first)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showMethodDialog = false }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                )
            }
        }
    }
}

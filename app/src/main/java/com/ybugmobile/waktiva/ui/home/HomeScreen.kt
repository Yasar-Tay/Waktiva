package com.ybugmobile.waktiva.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.local.preferences.UserSettings
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.ui.home.composables.*
import com.ybugmobile.waktiva.ui.settings.composables.SystemHealthEmptyState
import com.ybugmobile.waktiva.ui.settings.composables.SystemHealthOverlay
import com.ybugmobile.waktiva.ui.theme.LocalBackgroundGradient
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.waktiva.ui.theme.getGlassTheme
import com.ybugmobile.waktiva.ui.theme.getGradientForTime
import java.time.LocalDate

/**
 * Entry point for the Home screen.
 * Handles ViewModel initialization, lifecycle observations, and state collection.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                viewModel.onResume(lifecycleOwner)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState(initial = null)
    val allDays by viewModel.allPrayerDays.collectAsState()

    LaunchedEffect(state.isNetworkAvailable, state.hasSystemIssues, state.currentPrayerDay) {
        if (state.currentPrayerDay == null && state.isNetworkAvailable && !state.hasSystemIssues) {
            viewModel.refresh()
        }
    }

    val onRefresh = remember(viewModel) { { viewModel.refresh(); Unit } }
    val onMethodSelected = remember(viewModel) { { it: Int -> viewModel.updateCalculationMethod(it); Unit } }
    val onDateSelected = remember(viewModel) { { it: LocalDate -> viewModel.selectDate(it); Unit } }
    val onToggleCalendarType = remember(viewModel) { { it: Boolean -> viewModel.toggleCalendarType(it); Unit } }
    val onSkipNextAudio = remember(viewModel) { { name: String, date: LocalDate -> viewModel.toggleSkipNextPrayerAudio(name, date); Unit } }
    val onStopAdhan = remember(viewModel) { { viewModel.stopAdhan(); Unit } }
    val onStopTest = remember(viewModel) { { viewModel.stopTestAlarm(); Unit } }
    val onResetDate = remember(viewModel) { { viewModel.selectDate(LocalDate.now()); Unit } }
    val onDebugWeather = remember(viewModel) { { it: WeatherCondition -> viewModel.debugSetWeather(it); Unit } }

    HomeScreenContent(
        state = state,
        settings = settings,
        allDays = allDays,
        calculationMethods = viewModel.calculationMethods,
        onRefresh = onRefresh,
        onMethodSelected = onMethodSelected,
        onDateSelected = onDateSelected,
        onToggleCalendarType = onToggleCalendarType,
        onSkipNextAudio = onSkipNextAudio,
        onStopAdhan = onStopAdhan,
        onStopTest = onStopTest,
        onResetDate = onResetDate,
        onDebugWeather = onDebugWeather
    )
}

/**
 * Main UI content for the Home screen.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    state: HomeViewState,
    settings: UserSettings?,
    allDays: List<PrayerDay>,
    calculationMethods: List<Pair<Int, Int>>,
    onRefresh: () -> Unit,
    onMethodSelected: (Int) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onToggleCalendarType: (Boolean) -> Unit,
    onSkipNextAudio: (String, LocalDate) -> Unit,
    onStopAdhan: () -> Unit,
    onStopTest: () -> Unit,
    onResetDate: () -> Unit,
    onDebugWeather: (WeatherCondition) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val permissions = remember {
        val list = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        list
    }
    
    val permissionState = rememberMultiplePermissionsState(permissions) { permissionsResult ->
        if (permissionsResult.values.all { it }) {
            onRefresh()
        }
    }

    val effectiveWeather = remember(settings?.showWeatherEffects, state.weatherCondition) {
        if (settings?.showWeatherEffects == true) {
            state.weatherCondition
        } else {
            WeatherCondition.CLEAR
        }
    }

    val minuteTime = remember(state.currentTime.minute, state.currentTime.hour, state.currentTime.dayOfYear) {
        state.currentTime.toLocalTime().withSecond(0).withNano(0)
    }

    val backgroundGradient = remember(minuteTime, state.currentPrayerDay, effectiveWeather) {
        getGradientForTime(
            currentTime = minuteTime, 
            day = state.currentPrayerDay,
            weatherCondition = effectiveWeather
        )
    }
    
    val glassTheme = remember(minuteTime, state.currentPrayerDay, effectiveWeather) {
        getGlassTheme(minuteTime, state.currentPrayerDay, effectiveWeather)
    }
    
    var showMethodDialog by remember { mutableStateOf(false) }
    var showHealthOverlay by remember { mutableStateOf(false) }
    var showDebugWeather by remember { mutableStateOf(false) }

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showToast by remember { mutableStateOf(false) }
    val contentColor = glassTheme.contentColor

    val handleShowToast = remember(context, state.isMuted) {
        { prayerName: String ->
            val localizedPrayerName = PrayerType.fromString(prayerName)?.getDisplayName(context)
                ?: prayerName.lowercase().replaceFirstChar { it.uppercase() }

            toastMessage = if (!state.isMuted)
                context.getString(R.string.home_adhan_muted, localizedPrayerName)
            else
                context.getString(R.string.home_adhan_unmuted, localizedPrayerName)
            showToast = true
            Unit
        }
    }

    CompositionLocalProvider(
        LocalGlassTheme provides glassTheme,
        LocalBackgroundGradient provides backgroundGradient
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                Box(Modifier.fillMaxSize()) {
                    LargeFloatingActionButton(
                        onClick = { showDebugWeather = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 80.dp)
                            .size(48.dp)
                    ) {
                        Icon(Icons.Rounded.CloudQueue, "Debug Weather")
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                HomeBackground(
                    backgroundGradient = backgroundGradient,
                    currentTime = state.currentTime.toLocalTime(),
                    currentPrayerDay = state.currentPrayerDay,
                    sunAzimuth = { state.sunAzimuth },
                    sunAltitude = { state.sunAltitude },
                    compassAzimuth = { state.compassAzimuth },
                    showWeatherEffects = settings?.showWeatherEffects == true,
                    weatherCondition = effectiveWeather
                )

                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = contentColor
                    )
                } else if (state.currentPrayerDay == null && (!permissionState.allPermissionsGranted || !state.isNetworkAvailable || state.hasSystemIssues)) {
                    SystemHealthEmptyState(
                        isRefreshing = state.isRefreshing,
                        hasPrayerData = false,
                        contentColor = contentColor,
                        glassTheme = glassTheme,
                        onStatusClick = { showHealthOverlay = true }
                    )
                } else {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val scrollState = rememberScrollState()
                        val localTime = state.currentTime.toLocalTime()

                        if (isLandscape) {
                            HomeLandscapeContent(
                                state = state,
                                settings = settings,
                                allDays = allDays,
                                calculationMethods = calculationMethods,
                                glassTheme = glassTheme,
                                scrollState = scrollState,
                                localTime = localTime,
                                contentColor = contentColor,
                                onStatusClick = { showHealthOverlay = true },
                                onToggleCalendarType = onToggleCalendarType,
                                onDateSelected = onDateSelected,
                                onSkipNextAudio = onSkipNextAudio,
                                onStopAdhan = onStopAdhan,
                                onStopTest = onStopTest,
                                onResetDate = onResetDate,
                                onMethodClick = { showMethodDialog = true },
                                onShowToast = handleShowToast
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .navigationBarsPadding()
                            ) {
                                HomePortraitContent(
                                    state = state,
                                    settings = settings,
                                    allDays = allDays,
                                    calculationMethods = calculationMethods,
                                    glassTheme = glassTheme,
                                    scrollState = scrollState,
                                    localTime = localTime,
                                    contentColor = contentColor,
                                    onStatusClick = { showHealthOverlay = true },
                                    onToggleCalendarType = onToggleCalendarType,
                                    onDateSelected = onDateSelected,
                                    onSkipNextAudio = onSkipNextAudio,
                                    onStopAdhan = onStopAdhan,
                                    onStopTest = onStopTest,
                                    onResetDate = onResetDate,
                                    onMethodClick = { showMethodDialog = true },
                                    onShowToast = handleShowToast
                                )
                            }
                        }
                    }
                }

                if (showMethodDialog) {
                    CalculationMethodDialog(
                        showDialog = showMethodDialog,
                        onDismiss = { showMethodDialog = false },
                        calculationMethods = calculationMethods,
                        selectedMethod = settings?.calculationMethod ?: 3,
                        onMethodSelected = {
                            onMethodSelected(it)
                            showMethodDialog = false
                        }
                    )
                }

                if (showHealthOverlay) {
                    SystemHealthOverlay(
                        hasPrayerData = state.currentPrayerDay != null,
                        onDismiss = { showHealthOverlay = false }
                    )
                }
                
                if (showDebugWeather) {
                    AlertDialog(
                        onDismissRequest = { showDebugWeather = false },
                        title = { Text("Debug Weather") },
                        text = {
                            Column {
                                WeatherCondition.entries.forEach { condition ->
                                    TextButton(onClick = { 
                                        onDebugWeather(condition)
                                        showDebugWeather = false
                                    }) {
                                        Text(condition.name)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showDebugWeather = false }) {
                                Text("Close")
                            }
                        }
                    )
                }

                GlassToast(
                    message = toastMessage ?: "",
                    isVisible = showToast,
                    onDismiss = { showToast = false }
                )
            }
        }
    }
}

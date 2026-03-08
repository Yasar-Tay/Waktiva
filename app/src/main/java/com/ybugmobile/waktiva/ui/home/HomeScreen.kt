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
import com.ybugmobile.waktiva.ui.theme.getGlassTheme
import com.ybugmobile.waktiva.ui.theme.getGradientForTime
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    HomeScreenContent(
        state = state,
        settings = settings,
        allDays = allDays,
        calculationMethods = viewModel.calculationMethods,
        onRefresh = { viewModel.refresh() },
        onMethodSelected = { viewModel.updateCalculationMethod(it) },
        onDateSelected = { viewModel.selectDate(it) },
        onToggleCalendarType = { viewModel.toggleCalendarType(it) },
        onSkipNextAudio = { name, date -> viewModel.toggleSkipNextPrayerAudio(name, date) },
        onStopAdhan = { viewModel.stopAdhan() },
        onStopTest = { viewModel.stopTestAlarm() },
        onResetDate = { viewModel.selectDate(LocalDate.now()) },
        onDebugWeather = { viewModel.debugSetWeather(it) }
    )
}

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

    val localTime = remember(state.currentTime) { state.currentTime.toLocalTime() }

    val backgroundGradient = remember(localTime, state.currentPrayerDay, effectiveWeather) {
        getGradientForTime(
            currentTime = localTime, 
            day = state.currentPrayerDay,
            weatherCondition = effectiveWeather
        )
    }
    
    val glassTheme = remember(localTime, state.currentPrayerDay) {
        getGlassTheme(localTime, state.currentPrayerDay)
    }
    
    var showMethodDialog by remember { mutableStateOf(false) }
    var showHealthOverlay by remember { mutableStateOf(false) }
    var showDebugWeather by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val contentColor = glassTheme.contentColor

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.systemBars,
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
                currentTime = localTime,
                currentPrayerDay = state.currentPrayerDay,
                sunAzimuth = state.sunAzimuth,
                sunAltitude = state.sunAltitude,
                compassAzimuth = state.compassAzimuth,
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
                    
                    val handleShowSnackbar: (String) -> Unit = { prayerName ->
                        scope.launch {
                            val localizedPrayerName = PrayerType.fromString(prayerName)?.getDisplayName(context)
                                ?: prayerName.lowercase().replaceFirstChar { it.uppercase() }

                            val message = if (!state.isMuted)
                                "MUTED:" + context.getString(R.string.home_adhan_muted, localizedPrayerName)
                            else
                                "UNMUTED:" + context.getString(R.string.home_adhan_unmuted, localizedPrayerName)
                            snackbarHostState.showSnackbar(message)
                        }
                    }

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
                            onShowSnackbar = handleShowSnackbar
                        )
                    } else {
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
                            onShowSnackbar = handleShowSnackbar
                        )
                    }
                }
            }

            if (showHealthOverlay) {
                SystemHealthOverlay(
                    onDismiss = { 
                        showHealthOverlay = false 
                        onRefresh()
                    }
                )
            }

            if (showDebugWeather) {
                DebugWeatherDialog(
                    onDismiss = { showDebugWeather = false },
                    onConditionSelected = {
                        onDebugWeather(it)
                        showDebugWeather = false
                    }
                )
            }

            HomeSnackbarHost(
                hostState = snackbarHostState,
                glassTheme = glassTheme,
                modifier = Modifier.align(Alignment.Center)
            )

            CalculationMethodDialog(
                showDialog = showMethodDialog,
                onDismiss = { showMethodDialog = false },
                calculationMethods = calculationMethods,
                selectedMethod = settings?.calculationMethod ?: -1,
                onMethodSelected = {
                    onMethodSelected(it)
                    showMethodDialog = false
                }
            )
        }
    }
}

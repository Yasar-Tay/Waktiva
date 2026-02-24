package com.ybugmobile.vaktiva.ui.home

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.model.WeatherCondition
import com.ybugmobile.vaktiva.ui.home.composables.*
import com.ybugmobile.vaktiva.ui.settings.composables.SystemHealthCard
import com.ybugmobile.vaktiva.ui.settings.composables.SystemHealthEmptyState
import com.ybugmobile.vaktiva.ui.settings.composables.SystemHealthOverlay
import com.ybugmobile.vaktiva.ui.settings.composables.SystemHealthIndicator
import com.ybugmobile.vaktiva.ui.theme.getGlassTheme
import com.ybugmobile.vaktiva.ui.theme.getGradientForTime
import com.ybugmobile.vaktiva.ui.theme.StarryBackgroundLayer
import com.ybugmobile.vaktiva.ui.theme.AtmosphericBackgroundLayer
import com.ybugmobile.vaktiva.ui.theme.WeatherBackgroundLayer
import com.ybugmobile.vaktiva.utils.PermissionUtils
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

    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permissionState = rememberMultiplePermissionsState(permissions) { permissionsResult ->
        if (permissionsResult.values.all { it }) {
            onRefresh()
        }
    }

    // Determine the effective weather condition based on user setting
    val effectiveWeather = if (settings?.showWeatherEffects == true) {
        state.weatherCondition
    } else {
        WeatherCondition.CLEAR
    }

    // Pass effective weatherCondition to dynamic gradient calculation
    val backgroundGradient = getGradientForTime(
        currentTime = state.currentTime.toLocalTime(), 
        day = state.currentPrayerDay,
        weatherCondition = effectiveWeather
    )
    val glassTheme = getGlassTheme(state.currentTime.toLocalTime(), state.currentPrayerDay)
    var showMethodDialog by remember { mutableStateOf(false) }
    var showHealthOverlay by remember { mutableStateOf(false) }
    var showDebugWeather by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val contentColor = glassTheme.contentColor

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { },
        contentWindowInsets = WindowInsets.systemBars,
        floatingActionButton = {
            Box(Modifier.fillMaxSize()) {
                // Debug Weather FAB - Positioned on the Bottom Left
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
                .background(brush = backgroundGradient)
        ) {
            // Dynamic Environmental Layers
            StarryBackgroundLayer(
                currentTime = state.currentTime.toLocalTime(),
                day = state.currentPrayerDay
            )
            
            AtmosphericBackgroundLayer(
                currentTime = state.currentTime.toLocalTime(),
                day = state.currentPrayerDay,
                sunAzimuth = state.sunAzimuth,
                sunAltitude = state.sunAltitude,
                compassAzimuth = state.compassAzimuth
            )

            // Only show weather layer if effects are enabled in settings
            if (settings?.showWeatherEffects == true) {
                WeatherBackgroundLayer(
                    condition = effectiveWeather,
                    isDay = true
                )
            }

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
                    if (isLandscape) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                                    .displayCutoutPadding()
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                                    .padding(start = 72.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                            ) {
                                HomeHeader(
                                    locationName = state.locationName,
                                    date = state.selectedDate,
                                    hijriDate = state.effectiveHijriDate,
                                    contentColor = contentColor,
                                    onStatusClick = { showHealthOverlay = true },
                                    isNetworkAvailable = state.isNetworkAvailable,
                                    isLocationEnabled = state.isLocationEnabled,
                                    isLocationPermissionGranted = state.isLocationPermissionGranted
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier.size(280.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        state.currentPrayerDay?.let { prayerDay ->
                                            PrayerCircleVisualization(
                                                day = prayerDay,
                                                currentTime = if (state.selectedDate == LocalDate.now()) state.currentTime.toLocalTime() else LocalTime.MIDNIGHT,
                                                nextPrayer = if (state.selectedDate == LocalDate.now()) state.nextPrayer else null,
                                                currentPrayer = if (state.selectedDate == LocalDate.now()) state.currentPrayer else null,
                                                isSelectedDayToday = state.selectedDate == LocalDate.now(),
                                                contentColor = contentColor,
                                                isMuted = state.isMuted,
                                                playAdhanAudio = settings?.playAdhanAudio ?: false,
                                                onSkipAudio = { prayerName ->
                                                    state.nextPrayer?.let { next ->
                                                        onSkipNextAudio(prayerName, next.date)
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(32.dp))

                                    Box(modifier = Modifier.weight(1f)) {
                                        AnimatedContent(
                                            targetState = state.isAdhanPlaying,
                                            transitionSpec = {
                                                fadeIn() togetherWith fadeOut()
                                            },
                                            label = "NextPrayerOrAdhan"
                                        ) { playing ->
                                            if (playing) {
                                                AdhanControls(
                                                    isAdhanPlaying = true,
                                                    playingPrayerName = state.playingPrayerName,
                                                    isTest = state.nextPrayer?.isTest == true,
                                                    onStopAdhan = onStopAdhan,
                                                    onStopTest = onStopTest,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else {
                                                NextPrayerCountdown(
                                                    nextPrayer = state.nextPrayer,
                                                    selectedDate = state.selectedDate,
                                                    contentColor = contentColor,
                                                    currentPrayer = state.currentPrayer,
                                                    playAdhanAudio = settings?.playAdhanAudio ?: false,
                                                    isMuted = state.isMuted,
                                                    onSkipAudio = { prayerName ->
                                                        state.nextPrayer?.let { next ->
                                                            onSkipNextAudio(prayerName, next.date)
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
                                                    },
                                                    onResetDate = onResetDate,
                                                    accentColor = Color.White,
                                                    showIdleState = false
                                                )
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = glassTheme.containerColor,
                                    shape = RoundedCornerShape(32.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        glassTheme.borderColor
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        ModernCalendarStrip(
                                            selectedDate = state.selectedDate,
                                            availableDays = allDays.filter { !it.date.isBefore(LocalDate.now()) },
                                            isHijriSelected = state.isHijriSelected,
                                            onToggleCalendarType = onToggleCalendarType,
                                            onDateSelected = onDateSelected,
                                            contentColor = contentColor
                                        )

                                        Spacer(modifier = Modifier.height(32.dp))

                                        state.currentPrayerDay?.let { prayerDay ->
                                            PrayerTimeList(
                                                day = prayerDay,
                                                currentPrayerType = if (state.selectedDate == LocalDate.now()) state.currentPrayer?.type else null,
                                                contentColor = contentColor,
                                                highlightColor = contentColor.copy(alpha = 0.15f)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))

                                        CalculationMethodCard(
                                            settings = settings,
                                            calculationMethods = calculationMethods,
                                            onClick = { showMethodDialog = true },
                                            contentColor = contentColor,
                                            glassTheme = glassTheme
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(80.dp))
                            }

                            // Moon Phase in Top Middle for Landscape
                            // Now moves with scroll by applying negative scroll offset
                            MoonPhaseView(
                                moonPhase = state.moonPhase,
                                contentColor = contentColor,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp)
                                    .graphicsLayer {
                                        translationY = -scrollState.value.toFloat()
                                    }
                            )
                        }
                    } else {
                        // Portrait Layout
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 16.dp)
                                    .systemBarsPadding()
                                    .verticalScroll(scrollState)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    HomeHeader(
                                        locationName = state.locationName,
                                        date = state.selectedDate,
                                        hijriDate = state.effectiveHijriDate,
                                        contentColor = contentColor,
                                        onStatusClick = { showHealthOverlay = true },
                                        isNetworkAvailable = state.isNetworkAvailable,
                                        isLocationEnabled = state.isLocationEnabled,
                                        isLocationPermissionGranted = state.isLocationPermissionGranted
                                    )
                                }

                                Column(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Weather Summary Row (Temporary for testing)
                                    if (state.temperature != null) {
                                        Text(
                                            text = "${state.temperature}°C • ${state.weatherCondition.name}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = contentColor.copy(alpha = 0.6f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(32.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(300.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        state.currentPrayerDay?.let { prayerDay ->
                                            PrayerCircleVisualization(
                                                day = prayerDay,
                                                currentTime = if (state.selectedDate == LocalDate.now()) state.currentTime.toLocalTime() else LocalTime.MIDNIGHT,
                                                nextPrayer = if (state.selectedDate == LocalDate.now()) state.nextPrayer else null,
                                                currentPrayer = if (state.selectedDate == LocalDate.now()) state.currentPrayer else null,
                                                isSelectedDayToday = state.selectedDate == LocalDate.now(),
                                                contentColor = contentColor,
                                                isMuted = state.isMuted,
                                                playAdhanAudio = settings?.playAdhanAudio ?: false,
                                                onSkipAudio = { prayerName ->
                                                    state.nextPrayer?.let { next ->
                                                        onSkipNextAudio(prayerName, next.date)
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    AnimatedContent(
                                        targetState = state.isAdhanPlaying,
                                        transitionSpec = {
                                            fadeIn() togetherWith fadeOut()
                                        },
                                        label = "NextPrayerOrAdhan"
                                    ) { playing ->
                                        if (playing) {
                                            AdhanControls(
                                                isAdhanPlaying = true,
                                                playingPrayerName = state.playingPrayerName,
                                                isTest = state.nextPrayer?.isTest == true,
                                                onStopAdhan = onStopAdhan,
                                                onStopTest = onStopTest,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            NextPrayerCountdown(
                                                nextPrayer = state.nextPrayer,
                                                selectedDate = state.selectedDate,
                                                contentColor = contentColor,
                                                currentPrayer = state.currentPrayer,
                                                playAdhanAudio = settings?.playAdhanAudio ?: false,
                                                isMuted = state.isMuted,
                                                onSkipAudio = { prayerName ->
                                                    state.nextPrayer?.let { next ->
                                                        onSkipNextAudio(prayerName, next.date)
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
                                                },
                                                onResetDate = onResetDate,
                                                accentColor = Color.White,
                                                showIdleState = false
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    color = glassTheme.containerColor,
                                    shape = RoundedCornerShape(32.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        glassTheme.borderColor
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(24.dp)
                                    ) {
                                        ModernCalendarStrip(
                                            selectedDate = state.selectedDate,
                                            availableDays = allDays.filter { !it.date.isBefore(LocalDate.now()) },
                                            isHijriSelected = state.isHijriSelected,
                                            onToggleCalendarType = onToggleCalendarType,
                                            onDateSelected = onDateSelected,
                                            contentColor = contentColor
                                        )

                                        Spacer(modifier = Modifier.height(32.dp))

                                        state.currentPrayerDay?.let { prayerDay ->
                                            PrayerTimeList(
                                                day = prayerDay,
                                                currentPrayerType = if (state.selectedDate == LocalDate.now()) state.currentPrayer?.type else null,
                                                contentColor = contentColor,
                                                highlightColor = contentColor.copy(alpha = 0.15f)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))

                                        CalculationMethodCard(
                                            settings = settings,
                                            calculationMethods = calculationMethods,
                                            onClick = { showMethodDialog = true },
                                            contentColor = contentColor,
                                            glassTheme = glassTheme
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(80.dp))
                            }

                            // Moon Phase in Top Right for Portrait
                            // Now moves with scroll by applying negative scroll offset
                            MoonPhaseView(
                                moonPhase = state.moonPhase,
                                contentColor = contentColor,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 24.dp, end = 24.dp)
                                    .graphicsLayer {
                                        translationY = -scrollState.value.toFloat()
                                        scaleX = 0.85f
                                        scaleY = 0.85f
                                    }
                            )
                        }
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
                AlertDialog(
                    onDismissRequest = { showDebugWeather = false },
                    title = { Text("Test Weather Conditions") },
                    text = {
                        LazyColumn {
                            items(WeatherCondition.entries.filter { it != WeatherCondition.UNKNOWN }) { condition ->
                                TextButton(
                                    onClick = { 
                                        onDebugWeather(condition)
                                        showDebugWeather = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(condition.name)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDebugWeather = false }) { Text("Close") }
                    }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.Center),
                snackbar = { data ->
                    val parts = data.visuals.message.split(":", limit = 2)
                    val type = parts.getOrNull(0)
                    val message = parts.getOrNull(1) ?: data.visuals.message

                    val icon = when (type) {
                        "MUTED" -> Icons.AutoMirrored.Filled.VolumeOff
                        "UNMUTED" -> Icons.AutoMirrored.Filled.VolumeUp
                        else -> null
                    }

                    val snackbarBackgroundColor = if (glassTheme.isLightMode) {
                        Color.White.copy(alpha = 0.9f)
                    } else {
                        Color(0xFF1C1B1F).copy(alpha = 0.8f)
                    }

                    val snackbarContentColor = if (glassTheme.isLightMode) {
                        Color(0xFF1C1B1F)
                    } else {
                        Color.White
                    }

                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .wrapContentWidth(),
                        shape = RoundedCornerShape(percent = 50),
                        color = snackbarBackgroundColor,
                        contentColor = snackbarContentColor,
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            snackbarContentColor.copy(alpha = 0.12f)
                        ),
                        shadowElevation = 12.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = snackbarContentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.3.sp
                                ),
                                color = snackbarContentColor
                            )
                        }
                    }
                }
            )

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
                                    Text(stringResource(method.first))
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionRequiredFallback(permissionState: MultiplePermissionsState) {
    val context = LocalContext.current
    var denialCount by rememberSaveable { mutableIntStateOf(0) }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.home_permissions_required),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.home_permissions_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (denialCount >= 2 || permissionState.shouldShowRationale) {
                        openAppSettings()
                    } else {
                        permissionState.launchMultiplePermissionRequest()
                        denialCount++
                    }
                },
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.home_grant_permissions))
            }
        }
    }
}

@Composable
private fun CalculationMethodCard(
    settings: UserSettings?,
    calculationMethods: List<Pair<Int, Int>>,
    onClick: () -> Unit,
    contentColor: Color,
    glassTheme: com.ybugmobile.vaktiva.ui.theme.GlassTheme
) {
    settings?.let { s ->
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = glassTheme.secondaryContentColor.copy(alpha = 0.1f)
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
                        color = glassTheme.secondaryContentColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        calculationMethods.find { it.second == s.calculationMethod }?.first?.let { stringResource(it) }
                            ?: "Default",
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                }
                Icon(
                    Icons.Default.Settings,
                    null,
                    tint = glassTheme.secondaryContentColor
                )
            }
        }
    }
}

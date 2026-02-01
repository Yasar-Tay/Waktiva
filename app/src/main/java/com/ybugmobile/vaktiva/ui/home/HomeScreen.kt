package com.ybugmobile.vaktiva.ui.home

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.ybugmobile.vaktiva.ui.theme.getGlassTheme
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
        onToggleCalendarType = { viewModel.toggleCalendarType(it) },
        onSkipNextAudio = { name, date -> viewModel.toggleSkipNextPrayerAudio(name, date) },
        onStopAdhan = { viewModel.stopAdhan() },
        onStopTest = { viewModel.stopTestAlarm() },
        onResetDate = { viewModel.selectDate(LocalDate.now()) }
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
    onResetDate: () -> Unit
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
    val permissionState = rememberMultiplePermissionsState(permissions)

    val backgroundGradient =
        getGradientForTime(state.currentTime.toLocalTime(), state.currentPrayerDay)
    val glassTheme = getGlassTheme(state.currentTime.toLocalTime(), state.currentPrayerDay)
    var showMethodDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val contentColor = glassTheme.contentColor

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { },
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = backgroundGradient)
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
                    if (isLandscape) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .systemBarsPadding()
                                .displayCutoutPadding()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .padding(start = 72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HomeHeader(
                                locationName = state.locationName,
                                date = state.selectedDate,
                                hijriDate = state.currentPrayerDay?.hijriDate,
                                contentColor = contentColor
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            if (state.currentPrayerDay != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier.size(280.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
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
                                                }
                                            },
                                            centerContent = { accentColor ->
                                                CurrentPrayerHeader(
                                                    currentPrayer = state.currentPrayer,
                                                    contentColor = contentColor
                                                )
                                            }
                                        )
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

                                    if (state.currentPrayerDay != null) {
                                        PrayerTimeList(
                                            day = state.currentPrayerDay,
                                            nextPrayerType = if (state.selectedDate == LocalDate.now()) state.nextPrayer?.type else null,
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
                    } else {
                        // Portrait Layout
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .systemBarsPadding()
                                .verticalScroll(rememberScrollState())
                        ) {
                            HomeHeader(
                                locationName = state.locationName,
                                date = state.selectedDate,
                                hijriDate = state.currentPrayerDay?.hijriDate,
                                contentColor = contentColor
                            )

                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(32.dp))

                                if (state.currentPrayerDay != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(300.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
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
                                                }
                                            },
                                            centerContent = { accentColor ->
                                                CurrentPrayerHeader(
                                                    currentPrayer = state.currentPrayer,
                                                    contentColor = contentColor
                                                )
                                            }
                                        )
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

                                    if (state.currentPrayerDay != null) {
                                        PrayerTimeList(
                                            day = state.currentPrayerDay,
                                            nextPrayerType = if (state.selectedDate == LocalDate.now()) state.nextPrayer?.type else null,
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
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.Center),
                snackbar = { data ->
                    val parts = data.visuals.message.split(":", limit = 2)
                    val type = parts.getOrNull(0)
                    val message = parts.getOrNull(1) ?: data.visuals.message

                    val icon = when (type) {
                        "MUTED" -> Icons.Filled.VolumeOff
                        "UNMUTED" -> Icons.Filled.VolumeUp
                        else -> null
                    }

                    Surface(
                        modifier = Modifier.padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (icon != null) {
                                Icon(icon, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(message, style = MaterialTheme.typography.bodyMedium)
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
